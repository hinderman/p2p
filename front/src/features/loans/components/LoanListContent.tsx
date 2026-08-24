import { IonButton, IonIcon, IonSkeletonText } from '@ionic/react';
import { alertCircleOutline, refreshOutline, walletOutline } from 'ionicons/icons';

import type { LoanSummary } from '../model/loan.types';
import { loanRequestErrorMessage } from '../model/loanPresentation';
import { LoanCard } from './LoanCard';

type LoanListContentProps = {
  actionForLoan?: (loan: LoanSummary) => { label: string; path: string } | undefined;
  error: unknown;
  isLoading: boolean;
  loans: LoanSummary[];
  onCreate?: () => void;
  onRetry: () => void;
};

function LoanListSkeleton() {
  return (
    <div aria-label="Cargando préstamos" className="loan-grid" role="status">
      {[1, 2, 3].map((item) => (
        <div className="loan-card loan-card-skeleton" key={item}>
          <IonSkeletonText animated style={{ width: '32%' }} />
          <IonSkeletonText animated style={{ height: '2rem', width: '62%' }} />
          <IonSkeletonText animated style={{ width: '88%' }} />
          <IonSkeletonText animated style={{ width: '74%' }} />
        </div>
      ))}
    </div>
  );
}

export function LoanListContent({ actionForLoan, error, isLoading, loans, onCreate, onRetry }: LoanListContentProps) {
  if (isLoading && loans.length === 0) return <LoanListSkeleton />;

  if (error && loans.length === 0) {
    return (
      <section className="loan-state" role="alert">
        <IonIcon aria-hidden="true" icon={alertCircleOutline} />
        <h2>No pudimos cargar tus préstamos</h2>
        <p>{loanRequestErrorMessage(error)}</p>
        <IonButton fill="outline" onClick={onRetry}>
          <IonIcon aria-hidden="true" icon={refreshOutline} slot="start" />
          Reintentar
        </IonButton>
      </section>
    );
  }

  if (loans.length === 0) {
    return (
      <section className="loan-state">
        <IonIcon aria-hidden="true" icon={walletOutline} />
        <h2>Aún no hay préstamos</h2>
        <p>{onCreate ? 'Crea tu primera propuesta para comenzar.' : 'Los préstamos que aceptes aparecerán aquí.'}</p>
        {onCreate && <IonButton onClick={onCreate}>Crear préstamo</IonButton>}
      </section>
    );
  }

  return (
    <>
      {error && (
        <div className="loan-inline-error" role="alert">
          <span>No pudimos actualizar la información.</span>
          <button type="button" onClick={onRetry}>Reintentar</button>
        </div>
      )}
      <div className={isLoading ? 'loan-grid refreshing' : 'loan-grid'}>
        {loans.map((loan) => <LoanCard action={actionForLoan?.(loan)} key={loan.loanId} loan={loan} />)}
      </div>
    </>
  );
}
