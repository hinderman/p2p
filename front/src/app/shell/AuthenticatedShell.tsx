import { IonRouterOutlet, IonSplitPane } from '@ionic/react';
import { Navigate, Route } from 'react-router-dom';

import { HomePage } from '../../features/home';
import { LenderLoansPage, LoanDetailPage, PayerLoansPage } from '../../features/loans';
import { PendingPaymentsPage, ReportPaymentPage } from '../../features/payments';
import { AppSideMenu } from './AppSideMenu';
import { MobileNavigation } from './MobileNavigation';
import './AuthenticatedShell.css';

export function AuthenticatedShell() {
  return (
    <IonSplitPane className="authenticated-shell" contentId="authenticated-content" when="(min-width: 64rem)">
      <AppSideMenu />

      <div className="authenticated-content" id="authenticated-content">
        <IonRouterOutlet>
          <Route path="/app" element={<HomePage />} />
          <Route
            path="/app/lender/loans/:loanId"
            element={<LoanDetailPage scope="lender" />}
          />
          <Route
            path="/app/lender/:loanId/payments"
            element={<PendingPaymentsPage />}
          />
          <Route
            path="/app/lender"
            element={<LenderLoansPage />}
          />
          <Route
            path="/app/payer/loans/:loanId"
            element={<LoanDetailPage scope="payer" />}
          />
          <Route
            path="/app/payer/:loanId/payments/report"
            element={<ReportPaymentPage />}
          />
          <Route
            path="/app/payer"
            element={<PayerLoansPage />}
          />
          <Route path="*" element={<Navigate to="/app" replace />} />
        </IonRouterOutlet>
        <MobileNavigation />
      </div>
    </IonSplitPane>
  );
}
