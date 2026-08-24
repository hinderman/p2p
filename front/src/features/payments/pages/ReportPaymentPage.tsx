import {
  IonButton,
  IonIcon,
  IonInput,
  IonSelect,
  IonSelectOption,
  IonSpinner,
} from '@ionic/react';
import { addOutline, alertCircleOutline, arrowBackOutline, documentAttachOutline, trashOutline } from 'ionicons/icons';
import { useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { ApiError } from '../../../core/http/apiClient';
import { AppPage } from '../../../shared/ui/AppPage';
import { useAuth } from '../../auth';
import { reportPayment } from '../api/paymentsApi';
import { FinancialConfirmation } from '../components/FinancialConfirmation';
import { completePaymentAttempt, resolvePaymentAttempt } from '../model/paymentIdempotency';
import { formatPaymentMoney, isIsoCalendarDate, isPositiveMoney, isUuid, paymentErrorMessage } from '../model/paymentPresentation';
import type { PaymentProof, PaymentResult, PaymentType, ReportPaymentInput } from '../model/payment.types';
import './PaymentsPage.css';

type ProofDraft = PaymentProof & { fileName?: string; isHashing?: boolean };
type ReportErrors = Partial<Record<'amount' | 'currency' | 'date' | 'proofs', string>>;

function today(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

function emptyProof(): ProofDraft {
  return { storedObjectId: '', sha256: '' };
}

export function ReportPaymentPage() {
  const { loanId = '' } = useParams();
  const { session } = useAuth();
  const navigate = useNavigate();
  const [paymentType, setPaymentType] = useState<PaymentType>('INSTALLMENT');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState('COP');
  const [paymentDate, setPaymentDate] = useState(today);
  const [externalReference, setExternalReference] = useState('');
  const [proofs, setProofs] = useState<ProofDraft[]>([emptyProof()]);
  const [errors, setErrors] = useState<ReportErrors>({});
  const [requestError, setRequestError] = useState<string | null>(null);
  const [confirmation, setConfirmation] = useState<ReportPaymentInput | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [result, setResult] = useState<PaymentResult | null>(null);

  function updateProof(index: number, values: Partial<ProofDraft>) {
    setProofs((current) => current.map((proof, position) => position === index ? { ...proof, ...values } : proof));
    if (errors.proofs) setErrors((current) => ({ ...current, proofs: undefined }));
  }

  async function calculateFileHash(index: number, file?: File) {
    if (!file) return;
    if (file.size > 15 * 1024 * 1024) {
      setErrors((current) => ({ ...current, proofs: 'El archivo supera 15 MB para el cálculo local.' }));
      return;
    }
    updateProof(index, { fileName: file.name, isHashing: true });
    try {
      const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
      const sha256 = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
      updateProof(index, { sha256, isHashing: false });
    } catch {
      updateProof(index, { isHashing: false });
      setErrors((current) => ({ ...current, proofs: 'No fue posible calcular el hash del archivo.' }));
    }
  }

  function prepareSubmission(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors: ReportErrors = {};
    if (!isPositiveMoney(amount)) nextErrors.amount = 'Ingresa un monto positivo con máximo 4 decimales.';
    if (!/^[A-Za-z]{3}$/.test(currency.trim())) nextErrors.currency = 'Usa un código de moneda de tres letras.';
    if (!isIsoCalendarDate(paymentDate)) nextErrors.date = 'Selecciona una fecha válida.';
    const normalizedProofs = proofs.map((proof) => ({
      storedObjectId: proof.storedObjectId.trim(),
      sha256: proof.sha256.trim().toLowerCase(),
    }));
    if (
      normalizedProofs.length === 0 ||
      normalizedProofs.some((proof) => !isUuid(proof.storedObjectId) || !/^[a-f0-9]{64}$/.test(proof.sha256)) ||
      new Set(normalizedProofs.map((proof) => proof.storedObjectId)).size !== normalizedProofs.length
    ) nextErrors.proofs = 'Cada comprobante necesita un UUID único y un SHA-256 de 64 caracteres.';
    setErrors(nextErrors);
    setRequestError(null);
    if (Object.keys(nextErrors).length > 0 || !isUuid(loanId)) return;
    setConfirmation({
      paymentType,
      reportedAmount: { amount: amount.trim(), currency: currency.trim().toUpperCase() },
      reportedPaymentDate: paymentDate,
      externalReference: externalReference.trim() || null,
      proofs: normalizedProofs,
    });
  }

  async function confirmSubmission() {
    if (!confirmation || !session || isSubmitting) return;
    setIsSubmitting(true);
    const attempt = resolvePaymentAttempt(session.userAccountId, loanId, confirmation);
    try {
      const payment = await reportPayment(loanId, confirmation, attempt.idempotencyKey);
      completePaymentAttempt(session.userAccountId, loanId, attempt.idempotencyKey);
      setResult(payment);
      setConfirmation(null);
    } catch (error) {
      if (error instanceof ApiError && error.code === 'idempotency_conflict') {
        completePaymentAttempt(session.userAccountId, loanId, attempt.idempotencyKey);
      }
      setRequestError(paymentErrorMessage(error));
      setConfirmation(null);
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!isUuid(loanId)) {
    return <AppPage title="Reportar pago"><section className="payment-state"><h1>Préstamo no válido</h1><IonButton routerLink="/app/payer">Volver</IonButton></section></AppPage>;
  }

  if (result) {
    return (
      <AppPage title="Pago reportado">
        <section className="payment-success">
          <IonIcon aria-hidden="true" icon={documentAttachOutline} />
          <p>Enviado a revisión</p><h1>Pago reportado correctamente</h1>
          <span>El prestamista debe revisar el comprobante antes de afectar el saldo.</span>
          <code>{result.reportedPaymentId}</code>
          <IonButton onClick={() => navigate('/app/payer', { replace: true })}>Volver a mis préstamos</IonButton>
        </section>
      </AppPage>
    );
  }

  return (
    <AppPage title="Reportar pago">
      <div className="payments-page narrow">
        <header className="payments-heading">
          <IonButton aria-label="Volver a mis préstamos" fill="clear" routerLink="/app/payer"><IonIcon icon={arrowBackOutline} slot="icon-only" /></IonButton>
          <div><p>Préstamo #{loanId.slice(0, 8)}</p><h1>Reportar pago</h1><span>El reporte quedará pendiente de revisión y todavía no reduce el saldo.</span></div>
        </header>
        <form className="payment-form" noValidate onSubmit={prepareSubmission}>
          <section><h2>Información del pago</h2><div className="payment-form-grid">
            <IonSelect fill="outline" label="Tipo de pago" labelPlacement="stacked" value={paymentType} onIonChange={(event) => setPaymentType(event.detail.value)}>
              <IonSelectOption value="INSTALLMENT">Pago de cuota</IonSelectOption><IonSelectOption value="CAPITAL_PREPAYMENT">Abono extraordinario a capital</IonSelectOption>
            </IonSelect>
            <div className="payment-money"><IonInput className={errors.amount ? 'ion-invalid ion-touched' : ''} errorText={errors.amount} fill="outline" inputmode="decimal" label="Monto reportado" labelPlacement="stacked" value={amount} onIonInput={(event) => { setAmount(event.detail.value ?? ''); setErrors((current) => ({ ...current, amount: undefined })); }} /><IonInput className={errors.currency ? 'ion-invalid ion-touched' : ''} errorText={errors.currency} fill="outline" label="Moneda" labelPlacement="stacked" maxlength={3} value={currency} onIonInput={(event) => setCurrency(event.detail.value ?? '')} /></div>
            <IonInput className={errors.date ? 'ion-invalid ion-touched' : ''} errorText={errors.date} fill="outline" label="Fecha del pago" labelPlacement="stacked" type="date" value={paymentDate} onIonInput={(event) => setPaymentDate(event.detail.value ?? '')} />
            <IonInput fill="outline" label="Referencia externa (opcional)" labelPlacement="stacked" maxlength={150} value={externalReference} onIonInput={(event) => setExternalReference(event.detail.value ?? '')} />
          </div></section>
          <section><div className="payment-section-title"><div><h2>Comprobantes</h2><p>Referencia de un archivo previamente almacenado y escaneado.</p></div><IonButton fill="clear" type="button" onClick={() => setProofs((current) => [...current, emptyProof()])}><IonIcon icon={addOutline} slot="start" />Agregar</IonButton></div>
            <div className="proof-list">{proofs.map((proof, index) => <div className="proof-row" key={index}>
              <IonInput fill="outline" label="ID del archivo almacenado" labelPlacement="stacked" placeholder="UUID" value={proof.storedObjectId} onIonInput={(event) => updateProof(index, { storedObjectId: event.detail.value ?? '' })} />
              <IonInput fill="outline" label="SHA-256" labelPlacement="stacked" maxlength={64} value={proof.sha256} onIonInput={(event) => updateProof(index, { sha256: event.detail.value ?? '' })} />
              <label className="proof-file"><span>{proof.isHashing ? <IonSpinner name="crescent" /> : 'Calcular hash desde archivo'}</span><input disabled={proof.isHashing} type="file" onChange={(event) => void calculateFileHash(index, event.currentTarget.files?.[0])} /></label>
              {proof.fileName && <small>{proof.fileName} · el archivo no se carga desde esta pantalla</small>}
              {proofs.length > 1 && <IonButton aria-label={`Eliminar comprobante ${index + 1}`} color="danger" fill="clear" type="button" onClick={() => setProofs((current) => current.filter((_, position) => position !== index))}><IonIcon icon={trashOutline} slot="icon-only" /></IonButton>}
            </div>)}</div>{errors.proofs && <p className="payment-field-error">{errors.proofs}</p>}
          </section>
          {requestError && <div className="payment-error" role="alert"><IonIcon icon={alertCircleOutline} /><span>{requestError}</span></div>}
          <div className="payment-form-actions"><IonButton fill="clear" routerLink="/app/payer" type="button">Cancelar</IonButton><IonButton type="submit">Revisar y reportar</IonButton></div>
        </form>
      </div>
      <FinancialConfirmation confirmLabel="Confirmar reporte" isOpen={confirmation !== null} isProcessing={isSubmitting} title="Confirma el reporte de pago" onCancel={() => !isSubmitting && setConfirmation(null)} onConfirm={() => void confirmSubmission()}>
        {confirmation && <><p>Vas a reportar <strong>{formatPaymentMoney(confirmation.reportedAmount)}</strong>.</p><dl><div><dt>Fecha</dt><dd>{confirmation.reportedPaymentDate}</dd></div><div><dt>Comprobantes</dt><dd>{confirmation.proofs.length}</dd></div></dl><p>Esta acción enviará el pago al prestamista para revisión.</p></>}
      </FinancialConfirmation>
    </AppPage>
  );
}
