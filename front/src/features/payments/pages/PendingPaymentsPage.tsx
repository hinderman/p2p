import { IonButton, IonIcon, IonInput, IonSpinner, IonToast } from '@ionic/react';
import { alertCircleOutline, arrowBackOutline, refreshOutline, returnDownBackOutline } from 'ionicons/icons';
import { useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';

import { AppPage } from '../../../shared/ui/AppPage';
import { useLoanDetail } from '../../loans/model/useLoanDetail';
import { reversePayment } from '../api/paymentsApi';
import { FinancialConfirmation } from '../components/FinancialConfirmation';
import { PendingPaymentsTable } from '../components/PendingPaymentsTable';
import { ReviewPaymentModal } from '../components/ReviewPaymentModal';
import { isUuid, paymentErrorMessage } from '../model/paymentPresentation';
import type { PaymentResult, PendingPayment } from '../model/payment.types';
import { usePendingPayments } from '../model/usePendingPayments';
import './PaymentsPage.css';

export function PendingPaymentsPage() {
  const { loanId = '' } = useParams();
  const { changePageSize, error, goToPage, page, payments, reload, status } = usePendingPayments(loanId);
  const { detail } = useLoanDetail(loanId);
  const [selectedPayment, setSelectedPayment] = useState<PendingPayment | null>(null);
  const [processed, setProcessed] = useState<PaymentResult | null>(null);
  const [reversalPaymentId, setReversalPaymentId] = useState('');
  const [reversalReason, setReversalReason] = useState('');
  const [reversalError, setReversalError] = useState<string | null>(null);
  const [confirmReversal, setConfirmReversal] = useState(false);
  const [isReversing, setIsReversing] = useState(false);

  function handleProcessed(result: PaymentResult) {
    setSelectedPayment(null);
    setProcessed(result);
    if (result.status === 'APPROVED') setReversalPaymentId(result.reportedPaymentId);
    reload();
  }

  function prepareReversal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setReversalError(null);
    if (!isUuid(reversalPaymentId.trim()) || !reversalReason.trim() || reversalReason.trim().length > 1000) {
      setReversalError('Ingresa el UUID de un pago aprobado y una razón de máximo 1000 caracteres.');
      return;
    }
    setConfirmReversal(true);
  }

  async function confirmReverse() {
    if (isReversing) return;
    setIsReversing(true);
    try {
      const result = await reversePayment(reversalPaymentId.trim(), reversalReason);
      setProcessed(result);
      setReversalPaymentId('');
      setReversalReason('');
      setConfirmReversal(false);
    } catch (operationError) {
      setReversalError(paymentErrorMessage(operationError));
      setConfirmReversal(false);
    } finally {
      setIsReversing(false);
    }
  }

  if (!isUuid(loanId)) {
    return <AppPage title="Pagos pendientes"><section className="payment-state"><h1>Préstamo no válido</h1><IonButton routerLink="/app/lender">Volver</IonButton></section></AppPage>;
  }

  return (
    <AppPage title="Pagos pendientes">
      <div className="payments-page">
        <header className="payments-heading">
          <IonButton aria-label="Volver a préstamos otorgados" fill="clear" routerLink="/app/lender"><IonIcon icon={arrowBackOutline} slot="icon-only" /></IonButton>
          <div><p>Préstamo #{loanId.slice(0, 8)}</p><h1>Pagos pendientes</h1><span>Revisa cada reporte antes de afectar el saldo contractual.</span></div>
          <IonButton aria-label="Actualizar pagos pendientes" disabled={status === 'loading'} fill="clear" onClick={reload}><IonIcon icon={refreshOutline} slot="icon-only" /></IonButton>
        </header>

        {status === 'loading' && payments.length === 0 && <div className="payment-state" role="status"><IonSpinner name="crescent" /><h2>Cargando pagos</h2></div>}
        {status === 'error' && payments.length === 0 && <section className="payment-state" role="alert"><IonIcon icon={alertCircleOutline} /><h2>No pudimos consultar los pagos</h2><p>{paymentErrorMessage(error)}</p><IonButton fill="outline" onClick={reload}>Reintentar</IonButton></section>}
        {status === 'ready' && page.totalElements === 0 && <section className="payment-state"><h2>No hay pagos por revisar</h2><p>Los nuevos reportes del pagador aparecerán aquí.</p></section>}
        {payments.length > 0 && <PendingPaymentsTable isRefreshing={status === 'loading'} page={page} onPageChange={goToPage} onPageSizeChange={changePageSize} onPay={setSelectedPayment} />}

        <section className="reversal-panel">
          <div className="reversal-panel-heading"><IonIcon icon={returnDownBackOutline} /><div><h2>Reversar pago aprobado</h2><p>Utiliza esta operación sólo para corregir un pago previamente aprobado.</p></div></div>
          <div className="payment-limitation-note">El backend aún no ofrece historial de pagos aprobados. Por eso debes proporcionar el identificador exacto del pago.</div>
          <form className="reversal-form" onSubmit={prepareReversal}>
            <IonInput fill="outline" label="ID del pago aprobado" labelPlacement="stacked" placeholder="UUID" value={reversalPaymentId} onIonInput={(event) => { setReversalPaymentId(event.detail.value ?? ''); setReversalError(null); }} />
            <IonInput counter fill="outline" label="Razón de la reversión" labelPlacement="stacked" maxlength={1000} value={reversalReason} onIonInput={(event) => { setReversalReason(event.detail.value ?? ''); setReversalError(null); }} />
            {reversalError && <div className="payment-error" role="alert"><IonIcon icon={alertCircleOutline} /><span>{reversalError}</span></div>}
            <IonButton color="danger" fill="outline" type="submit">Revisar reversión</IonButton>
          </form>
        </section>
      </div>

      <ReviewPaymentModal installments={detail?.paymentPlan?.installments ?? []} payment={selectedPayment} onDismiss={() => setSelectedPayment(null)} onProcessed={handleProcessed} />
      <FinancialConfirmation confirmLabel="Confirmar reversión" danger isOpen={confirmReversal} isProcessing={isReversing} title="Confirma la reversión" onCancel={() => !isReversing && setConfirmReversal(false)} onConfirm={() => void confirmReverse()}>
        <p>Vas a reversar el pago <strong>#{reversalPaymentId.slice(0, 8)}</strong>.</p><p>Esta operación generará asientos compensatorios y modificará nuevamente el saldo financiero.</p><p>Motivo: {reversalReason.trim()}</p>
      </FinancialConfirmation>
      <IonToast color="success" duration={4_500} isOpen={processed !== null} message={processed?.status === 'REVERSED' ? 'Pago reversado correctamente.' : processed?.status === 'REJECTED' ? 'Pago rechazado correctamente.' : 'Pago aprobado correctamente.'} position="top" onDidDismiss={() => setProcessed(null)} />
    </AppPage>
  );
}
