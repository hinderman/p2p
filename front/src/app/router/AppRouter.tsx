import { IonRouterOutlet } from '@ionic/react';
import { IonReactRouter } from '@ionic/react-router';
import { Navigate, Route } from 'react-router-dom';

import { GuestOnly, LoginPage, RequireAuth } from '../../features/auth';
import { PayerOnboardingPage } from '../../features/payer-onboarding';
import { AuthenticatedShell } from '../shell/AuthenticatedShell';

export function AppRouter() {
  return (
    <IonReactRouter>
      <IonRouterOutlet>
        <Route
          path="/login"
          element={
            <GuestOnly>
              <LoginPage />
            </GuestOnly>
          }
        />
        <Route path="/onboarding/payer" element={<PayerOnboardingPage />} />
        <Route
          path="/app/*"
          element={
            <RequireAuth>
              <AuthenticatedShell />
            </RequireAuth>
          }
        />
        <Route path="/" element={<Navigate to="/app" replace />} />
        <Route path="*" element={<Navigate to="/app" replace />} />
      </IonRouterOutlet>
    </IonReactRouter>
  );
}
