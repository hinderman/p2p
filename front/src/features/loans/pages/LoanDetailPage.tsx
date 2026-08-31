import { IonButton, IonIcon, IonSpinner } from '@ionic/react';
import { alertCircleOutline, arrowBackOutline, refreshOutline } from 'ionicons/icons';
import { useParams } from 'react-router-dom';

import { AppPage } from '../../../shared/ui/AppPage';
import { paymentErrorMessage } from '../../payments/model/paymentPresentation';
import { formatLoanDate, formatMoney, loanStatusClass, loanStatusLabel } from '../model/loanPresentation';
import type { LoanScope } from '../model/loan.types';
import { useLoanDetail } from '../model/useLoanDetail';
import './LoanDetailPage.css';

const rateLabels: Record<string, string> = { DAILY: 'diaria', MONTHLY_NOMINAL: 'mensual nominal',
  MONTHLY_EFFECTIVE: 'mensual efectiva', ANNUAL_NOMINAL: 'anual nominal', ANNUAL_EFFECTIVE: 'anual efectiva' };

export function LoanDetailPage({ scope }: { scope: LoanScope }) {
  const { loanId = '' } = useParams();
  const { detail, error, reload, status } = useLoanDetail(loanId);
  const backPath = `/app/${scope}`;
  return <AppPage title="Detalle del préstamo"><div className="loan-detail-page">
    <header className="loan-detail-heading">
      <IonButton aria-label="Volver a préstamos" fill="clear" routerLink={backPath}><IonIcon icon={arrowBackOutline} slot="icon-only" /></IonButton>
      <div><p>Préstamo #{loanId.slice(0, 8)}</p><h1>Detalle y plan de pagos</h1><span>Condiciones contractuales y estado actualizado de cada cuota.</span></div>
      <IonButton aria-label="Actualizar detalle" disabled={status === 'loading'} fill="clear" onClick={reload}><IonIcon icon={refreshOutline} slot="icon-only" /></IonButton>
    </header>
    {status === 'loading' && !detail && <div className="loan-state" role="status"><IonSpinner name="crescent" /><h2>Cargando préstamo</h2></div>}
    {status === 'error' && !detail && <section className="loan-state" role="alert"><IonIcon icon={alertCircleOutline} /><h2>No pudimos consultar el préstamo</h2><p>{paymentErrorMessage(error)}</p><IonButton fill="outline" onClick={reload}>Reintentar</IonButton></section>}
    {detail && <>
      <section className="loan-detail-summary">
        <div><span className={`loan-status ${loanStatusClass(detail.status)}`}>{loanStatusLabel(detail.status)}</span><small>Contraparte #{detail.counterpartyPersonId.slice(0, 8)}</small></div>
        <dl><div><dt>Saldo de capital</dt><dd>{formatMoney(detail.outstandingBalance)}</dd></div><div><dt>Monto original</dt><dd>{formatMoney(detail.originalPrincipal)}</dd></div><div><dt>Creado</dt><dd>{formatLoanDate(detail.createdAt)}</dd></div></dl>
        <div className="loan-detail-actions">{scope === 'payer' && detail.status === 'ACTIVE' && <IonButton routerLink={`/app/payer/${loanId}/payments/report`}>Reportar pago</IonButton>}{scope === 'lender' && <IonButton fill="outline" routerLink={`/app/lender/${loanId}/payments`}>Revisar pagos</IonButton>}</div>
      </section>
      <section className="loan-terms-panel"><h2>Condiciones vigentes</h2><dl>
        <div><dt>Tasa</dt><dd>{detail.terms.interestRatePercentage}% {rateLabels[detail.terms.ratePeriod] ?? detail.terms.ratePeriod}</dd></div>
        <div><dt>Cuotas</dt><dd>{detail.terms.installmentCount}</dd></div><div><dt>Primera fecha</dt><dd>{detail.terms.firstDueDate}</dd></div>
        <div><dt>Amortización</dt><dd>{detail.terms.amortizationMethod.replaceAll('_', ' ')}</dd></div><div><dt>Zona horaria</dt><dd>{detail.terms.timeZone}</dd></div>
      </dl></section>
      <section className="installment-panel"><div><h2>Plan de pagos</h2><p>{detail.paymentPlan ? `Versión ${detail.paymentPlan.versionNumber} · ${detail.paymentPlan.installments.length} cuotas` : 'Se generará cuando el préstamo sea aceptado.'}</p></div>
        {detail.paymentPlan && <div className="installment-table-scroll"><table className="installment-table"><thead><tr><th>Cuota</th><th>Vencimiento</th><th className="numeric">Pactado</th><th className="numeric">Pagado</th><th className="numeric">Pendiente</th></tr></thead><tbody>{detail.paymentPlan.installments.map((installment) => <tr key={installment.installmentId}><td><strong>#{installment.number}</strong></td><td>{installment.dueDate}</td><td className="numeric">{formatMoney(installment.agreedTotal)}</td><td className="numeric">{formatMoney(installment.paidTotal)}</td><td className="numeric"><strong>{formatMoney(installment.outstandingTotal)}</strong><small>Capital {formatMoney(installment.outstandingPrincipal)} · Interés {formatMoney(installment.outstandingInterest)}{Number(installment.outstandingFee.amount) > 0 ? ` · Cargo ${formatMoney(installment.outstandingFee)}` : ''}</small></td></tr>)}</tbody></table></div>}
      </section>
    </>}
  </div></AppPage>;
}
