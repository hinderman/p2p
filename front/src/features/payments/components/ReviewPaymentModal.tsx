import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonModal,
  IonSelect,
  IonSelectOption,
  IonTitle,
  IonToolbar,
} from '@ionic/react';
import { addOutline, alertCircleOutline, closeOutline, trashOutline } from 'ionicons/icons';
import { useEffect, useState, type FormEvent } from 'react';

import { approvePayment, rejectPayment } from '../api/paymentsApi';
import { formatPaymentMoney, isPositiveMoney, isUuid, moneyToScaledInteger, paymentErrorMessage } from '../model/paymentPresentation';
import type { PaymentAllocationType, PaymentResult, PendingPayment } from '../model/payment.types';
import { FinancialConfirmation } from './FinancialConfirmation';

type AllocationDraft = { installmentId: string; type: PaymentAllocationType; amount: string };
type ReviewAction = 'approve' | 'reject';

type ReviewPaymentModalProps = {
  payment: PendingPayment | null;
  onDismiss: () => void;
  onProcessed: (result: PaymentResult) => void;
};

function initialAllocation(payment: PendingPayment): AllocationDraft {
  return { installmentId: '', type: 'INSTALLMENT_PRINCIPAL', amount: payment.reportedAmount.amount };
}

export function ReviewPaymentModal({ payment, onDismiss, onProcessed }: ReviewPaymentModalProps) {
  const [action, setAction] = useState<ReviewAction>('approve');
  const [validatedAmount, setValidatedAmount] = useState('');
  const [allocations, setAllocations] = useState<AllocationDraft[]>([]);
  const [reason, setReason] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [requestError, setRequestError] = useState<string | null>(null);
  const [confirmation, setConfirmation] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);

  useEffect(() => {
    if (!payment) return;
    setAction('approve');
    setValidatedAmount(payment.reportedAmount.amount);
    setAllocations([initialAllocation(payment)]);
    setReason('');
    setFormError(null);
    setRequestError(null);
    setConfirmation(false);
  }, [payment]);

  function updateAllocation(index: number, values: Partial<AllocationDraft>) {
    setAllocations((current) => current.map((allocation, position) => position === index ? { ...allocation, ...values } : allocation));
    setFormError(null);
  }

  function prepare(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!payment) return;
    setRequestError(null);
    if (action === 'reject') {
      if (!reason.trim() || reason.trim().length > 1000) {
        setFormError('La razón es obligatoria y no puede superar 1000 caracteres.');
        return;
      }
      setFormError(null);
      setConfirmation(true);
      return;
    }
    const validatedScaled = moneyToScaledInteger(validatedAmount);
    const allocationValues = allocations.map((allocation) => moneyToScaledInteger(allocation.amount));
    const invalidTarget = allocations.some((allocation) => allocation.type !== 'DIRECT_PRINCIPAL' && !isUuid(allocation.installmentId.trim()));
    const invalidDirectTarget = allocations.some((allocation) => allocation.type === 'DIRECT_PRINCIPAL' && allocation.installmentId.trim() !== '');
    const targets = allocations.map((allocation) => `${allocation.type}:${allocation.type === 'DIRECT_PRINCIPAL' ? '' : allocation.installmentId.trim()}`);
    const total = allocationValues.reduce<bigint>((sum, value) => sum + (value ?? 0n), 0n);
    if (!isPositiveMoney(validatedAmount) || allocations.length === 0 || allocationValues.some((value) => value === null || value === 0n) || invalidTarget || invalidDirectTarget || new Set(targets).size !== targets.length || total !== validatedScaled) {
      setFormError('Las asignaciones deben ser positivas, usar destinos únicos y sumar exactamente el monto validado. Los componentes de cuota requieren su UUID.');
      return;
    }
    setFormError(null);
    setConfirmation(true);
  }

  async function process() {
    if (!payment || isProcessing) return;
    setIsProcessing(true);
    try {
      const result = action === 'reject'
        ? await rejectPayment(payment.reportedPaymentId, reason)
        : await approvePayment(payment.reportedPaymentId, {
            validatedAmount: { amount: validatedAmount.trim(), currency: payment.reportedAmount.currency },
            allocations: allocations.map((allocation) => ({
              installmentId: allocation.type === 'DIRECT_PRINCIPAL' ? null : allocation.installmentId.trim(),
              type: allocation.type,
              amount: { amount: allocation.amount.trim(), currency: payment.reportedAmount.currency },
            })),
          });
      setConfirmation(false);
      onProcessed(result);
    } catch (error) {
      setRequestError(paymentErrorMessage(error));
      setConfirmation(false);
    } finally {
      setIsProcessing(false);
    }
  }

  return (
    <>
      <IonModal className="review-payment-modal" canDismiss={!isProcessing} isOpen={payment !== null} onDidDismiss={onDismiss}>
        <IonHeader><IonToolbar><IonTitle>Revisar pago</IonTitle><IonButtons slot="end"><IonButton aria-label="Cerrar revisión" disabled={isProcessing} onClick={onDismiss}><IonIcon icon={closeOutline} slot="icon-only" /></IonButton></IonButtons></IonToolbar></IonHeader>
        <IonContent>{payment && <form className="review-payment-form" onSubmit={prepare}>
          <div className="review-payment-summary"><span>Monto reportado</span><strong>{formatPaymentMoney(payment.reportedAmount)}</strong><small>Pago #{payment.reportedPaymentId.slice(0, 8)} · {payment.reportedPaymentDate}</small></div>
          <div className="review-action-switch"><button className={action === 'approve' ? 'active' : ''} type="button" onClick={() => { setAction('approve'); setFormError(null); }}>Aprobar</button><button className={action === 'reject' ? 'active danger' : ''} type="button" onClick={() => { setAction('reject'); setFormError(null); }}>Rechazar</button></div>
          {action === 'approve' ? <>
            <div className="payment-limitation-note">El backend no expone el tipo del pago ni sus cuotas. Verifica el comprobante externamente e ingresa las asignaciones contractuales.</div>
            <IonInput fill="outline" inputmode="decimal" label="Monto validado" labelPlacement="stacked" value={validatedAmount} onIonInput={(event) => { setValidatedAmount(event.detail.value ?? ''); setFormError(null); }} />
            <div className="payment-section-title"><div><h2>Asignaciones</h2><p>Deben sumar exactamente el monto validado.</p></div><IonButton fill="clear" type="button" onClick={() => setAllocations((current) => [...current, { installmentId: '', type: 'INSTALLMENT_PRINCIPAL', amount: '' }])}><IonIcon icon={addOutline} slot="start" />Agregar</IonButton></div>
            <div className="allocation-list">{allocations.map((allocation, index) => <div className="allocation-row" key={index}>
              <IonSelect fill="outline" label="Componente" labelPlacement="stacked" value={allocation.type} onIonChange={(event) => updateAllocation(index, { type: event.detail.value, installmentId: event.detail.value === 'DIRECT_PRINCIPAL' ? '' : allocation.installmentId })}><IonSelectOption value="INSTALLMENT_PRINCIPAL">Capital de cuota</IonSelectOption><IonSelectOption value="INTEREST">Interés</IonSelectOption><IonSelectOption value="FEE">Cargo</IonSelectOption><IonSelectOption value="DIRECT_PRINCIPAL">Abono directo a capital</IonSelectOption></IonSelect>
              {allocation.type !== 'DIRECT_PRINCIPAL' && <IonInput fill="outline" label="ID de cuota" labelPlacement="stacked" placeholder="UUID" value={allocation.installmentId} onIonInput={(event) => updateAllocation(index, { installmentId: event.detail.value ?? '' })} />}
              <IonInput fill="outline" inputmode="decimal" label="Monto" labelPlacement="stacked" value={allocation.amount} onIonInput={(event) => updateAllocation(index, { amount: event.detail.value ?? '' })} />
              {allocations.length > 1 && <IonButton aria-label={`Eliminar asignación ${index + 1}`} color="danger" fill="clear" type="button" onClick={() => setAllocations((current) => current.filter((_, position) => position !== index))}><IonIcon icon={trashOutline} slot="icon-only" /></IonButton>}
            </div>)}</div>
          </> : <IonInput counter fill="outline" label="Razón del rechazo" labelPlacement="stacked" maxlength={1000} value={reason} onIonInput={(event) => { setReason(event.detail.value ?? ''); setFormError(null); }} />}
          {formError && <div className="payment-error" role="alert"><IonIcon icon={alertCircleOutline} /><span>{formError}</span></div>}
          {requestError && <div className="payment-error" role="alert"><IonIcon icon={alertCircleOutline} /><span>{requestError}</span></div>}
          <div className="payment-form-actions"><IonButton fill="clear" type="button" onClick={onDismiss}>Cancelar</IonButton><IonButton color={action === 'reject' ? 'danger' : 'primary'} type="submit">{action === 'reject' ? 'Revisar rechazo' : 'Revisar aprobación'}</IonButton></div>
        </form>}</IonContent>
      </IonModal>
      <FinancialConfirmation confirmLabel={action === 'reject' ? 'Confirmar rechazo' : 'Confirmar aprobación'} danger={action === 'reject'} isOpen={confirmation} isProcessing={isProcessing} title={action === 'reject' ? 'Confirma el rechazo' : 'Confirma la aprobación'} onCancel={() => !isProcessing && setConfirmation(false)} onConfirm={() => void process()}>
        {payment && (action === 'reject' ? <><p>Vas a rechazar definitivamente el pago <strong>#{payment.reportedPaymentId.slice(0, 8)}</strong>.</p><p>Motivo: {reason.trim()}</p></> : <><p>Vas a aprobar <strong>{formatPaymentMoney({ amount: validatedAmount, currency: payment.reportedAmount.currency })}</strong>.</p><p>Esta operación afectará el saldo y generará movimientos financieros.</p></>)}
      </FinancialConfirmation>
    </>
  );
}
