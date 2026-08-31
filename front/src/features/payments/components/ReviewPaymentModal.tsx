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
import type { InstallmentDetail } from '../../loans/model/loan.types';
import { formatPaymentMoney, isPositiveMoney, isUuid, moneyToScaledInteger, paymentErrorMessage } from '../model/paymentPresentation';
import type { PaymentAllocationType, PaymentResult, PendingPayment } from '../model/payment.types';
import { FinancialConfirmation } from './FinancialConfirmation';

type AllocationDraft = { installmentId: string; type: PaymentAllocationType; amount: string };
type ReviewAction = 'approve' | 'reject';

type ReviewPaymentModalProps = {
  payment: PendingPayment | null;
  installments: InstallmentDetail[];
  onDismiss: () => void;
  onProcessed: (result: PaymentResult) => void;
};

function outstandingForType(installment: InstallmentDetail, type: PaymentAllocationType) {
  if (type === 'INSTALLMENT_PRINCIPAL') return installment.outstandingPrincipal;
  if (type === 'INTEREST') return installment.outstandingInterest;
  return installment.outstandingFee;
}

function firstInstallmentId(installments: InstallmentDetail[], type: PaymentAllocationType): string {
  if (type === 'DIRECT_PRINCIPAL') return '';
  return installments.find((item) => Number(outstandingForType(item, type).amount) > 0)?.installmentId ?? '';
}

function initialAllocation(payment: PendingPayment, installments: InstallmentDetail[], amount = payment.reportedAmount.amount): AllocationDraft {
  const type: PaymentAllocationType = installments.some((item) => Number(item.outstandingPrincipal.amount) > 0)
    ? 'INSTALLMENT_PRINCIPAL' : installments.some((item) => Number(item.outstandingInterest.amount) > 0)
      ? 'INTEREST' : installments.some((item) => Number(item.outstandingFee.amount) > 0) ? 'FEE' : 'DIRECT_PRINCIPAL';
  return { installmentId: firstInstallmentId(installments, type), type, amount };
}

export function ReviewPaymentModal({ installments, payment, onDismiss, onProcessed }: ReviewPaymentModalProps) {
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
    setAllocations([initialAllocation(payment, installments)]);
    setReason('');
    setFormError(null);
    setRequestError(null);
    setConfirmation(false);
  }, [installments, payment]);

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
      setFormError('Las asignaciones deben ser positivas, usar destinos únicos y sumar exactamente el monto validado. Selecciona una cuota para cada componente contractual.');
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
            {installments.length === 0 && <div className="payment-limitation-note">Este préstamo no tiene cuotas pendientes en su plan vigente. Sólo puedes registrar un abono directo a capital.</div>}
            <IonInput fill="outline" inputmode="decimal" label="Monto validado" labelPlacement="stacked" value={validatedAmount} onIonInput={(event) => { setValidatedAmount(event.detail.value ?? ''); setFormError(null); }} />
            <div className="payment-section-title"><div><h2>Asignaciones</h2><p>Deben sumar exactamente el monto validado.</p></div><IonButton fill="clear" type="button" onClick={() => setAllocations((current) => [...current, initialAllocation(payment, installments, '')])}><IonIcon icon={addOutline} slot="start" />Agregar</IonButton></div>
            <div className="allocation-list">{allocations.map((allocation, index) => <div className="allocation-row" key={index}>
              <IonSelect fill="outline" label="Componente" labelPlacement="stacked" value={allocation.type} onIonChange={(event) => { const type = event.detail.value as PaymentAllocationType; updateAllocation(index, { type, installmentId: firstInstallmentId(installments, type) }); }}><IonSelectOption value="INSTALLMENT_PRINCIPAL">Capital de cuota</IonSelectOption><IonSelectOption value="INTEREST">Interés</IonSelectOption><IonSelectOption value="FEE">Cargo</IonSelectOption><IonSelectOption value="DIRECT_PRINCIPAL">Abono directo a capital</IonSelectOption></IonSelect>
              {allocation.type !== 'DIRECT_PRINCIPAL' && <IonSelect fill="outline" interface="popover" label="Cuota" labelPlacement="stacked" placeholder="Selecciona una cuota" value={allocation.installmentId} onIonChange={(event) => updateAllocation(index, { installmentId: event.detail.value })}>{installments.filter((installment) => Number(outstandingForType(installment, allocation.type).amount) > 0).map((installment) => <IonSelectOption key={installment.installmentId} value={installment.installmentId}>Cuota #{installment.number} · {installment.dueDate} · {formatPaymentMoney(outstandingForType(installment, allocation.type))}</IonSelectOption>)}</IonSelect>}
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
