import { IonButton, IonIcon } from '@ionic/react';
import { calendarClearOutline, peopleOutline, walletOutline } from 'ionicons/icons';

import type { LoanSummary } from '../model/loan.types';
import {
  formatLoanDate,
  formatMoney,
  loanStatusClass,
  loanStatusLabel,
} from '../model/loanPresentation';

type LoanCardProps = {
  action?: { label: string; path: string };
  loan: LoanSummary;
};

export function LoanCard({ action, loan }: LoanCardProps) {
  return (
    <article className="loan-card">
      <div className="loan-card-topline">
        <span className={`loan-status ${loanStatusClass(loan.status)}`}>
          {loanStatusLabel(loan.status)}
        </span>
        <span className="loan-id" title={loan.loanId}>#{loan.loanId.slice(0, 8)}</span>
      </div>

      <div className="loan-card-balance">
        <span>Saldo pendiente</span>
        <strong>{formatMoney(loan.outstandingBalance)}</strong>
      </div>

      <dl className="loan-card-details">
        <div>
          <dt><IonIcon aria-hidden="true" icon={walletOutline} /> Monto original</dt>
          <dd>{formatMoney(loan.originalPrincipal)}</dd>
        </div>
        <div>
          <dt><IonIcon aria-hidden="true" icon={calendarClearOutline} /> Creado</dt>
          <dd>{formatLoanDate(loan.createdAt)}</dd>
        </div>
        <div>
          <dt><IonIcon aria-hidden="true" icon={peopleOutline} /> Contraparte</dt>
          <dd title={loan.counterpartyPersonId}>{loan.counterpartyPersonId.slice(0, 8)}</dd>
        </div>
      </dl>
      {action && <IonButton expand="block" fill="outline" routerLink={action.path}>{action.label}</IonButton>}
    </article>
  );
}
