import { IonButton, IonIcon, IonToast } from '@ionic/react';
import { addOutline, refreshOutline } from 'ionicons/icons';
import { useState } from 'react';

import { AppPage } from '../../../shared/ui/AppPage';
import { CreateLoanModal } from '../components/CreateLoanModal';
import { LoanListContent } from '../components/LoanListContent';
import type { CreatedLoan, LoanScope } from '../model/loan.types';
import { useLoans } from '../model/useLoans';
import './LoansPage.css';

type LoansPageProps = {
  scope: LoanScope;
};

export function LoansPage({ scope }: LoansPageProps) {
  const isLender = scope === 'lender';
  const { error, loans, reload, status } = useLoans(scope);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createdLoan, setCreatedLoan] = useState<CreatedLoan | null>(null);

  function handleCreated(loan: CreatedLoan) {
    setCreatedLoan(loan);
    setIsCreateOpen(false);
    reload();
  }

  return (
    <AppPage title={isLender ? 'Préstamos otorgados' : 'Mis préstamos'}>
      <div className="loans-page">
        <header className="loans-heading">
          <div>
            <p>{isLender ? 'Portafolio como prestamista' : 'Compromisos como pagador'}</p>
            <h1>{isLender ? 'Préstamos otorgados' : 'Mis préstamos'}</h1>
            <span>
              {isLender
                ? 'Consulta tus propuestas y préstamos activos.'
                : 'Consulta los préstamos asociados a tu cuenta.'}
            </span>
          </div>
          <div className="loans-heading-actions">
            <IonButton
              aria-label="Actualizar préstamos"
              disabled={status === 'loading'}
              fill="clear"
              onClick={reload}
            >
              <IonIcon aria-hidden="true" icon={refreshOutline} slot="icon-only" />
            </IonButton>
            {isLender && (
              <IonButton onClick={() => setIsCreateOpen(true)}>
                <IonIcon aria-hidden="true" icon={addOutline} slot="start" />
                Nuevo préstamo
              </IonButton>
            )}
          </div>
        </header>

        <LoanListContent
          actionForLoan={(loan) => ({ label: 'Ver detalle', path: `/app/${scope}/loans/${loan.loanId}` })}
          error={status === 'error' ? error : null}
          isLoading={status === 'loading'}
          loans={loans}
          onCreate={isLender ? () => setIsCreateOpen(true) : undefined}
          onRetry={reload}
        />
      </div>

      {isLender && (
        <CreateLoanModal
          isOpen={isCreateOpen}
          onCreated={handleCreated}
          onDismiss={() => setIsCreateOpen(false)}
        />
      )}
      <IonToast
        color="success"
        duration={4_500}
        isOpen={createdLoan !== null}
        message="Préstamo creado. La invitación fue programada para envío."
        position="top"
        onDidDismiss={() => setCreatedLoan(null)}
      />
    </AppPage>
  );
}

export function LenderLoansPage() {
  return <LoansPage scope="lender" />;
}

export function PayerLoansPage() {
  return <LoansPage scope="payer" />;
}
