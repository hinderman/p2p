import { IonContent, IonPage, IonSpinner } from '@ionic/react';
import { Navigate, useLocation } from 'react-router-dom';
import type { PropsWithChildren } from 'react';

import { useAuth } from '../model/authContext';
import './AuthGuards.css';

function SessionLoading() {
  return (
    <IonPage>
      <IonContent fullscreen>
        <div className="session-loading" role="status" aria-label="Validando sesión">
          <IonSpinner name="crescent" />
        </div>
      </IonContent>
    </IonPage>
  );
}

export function RequireAuth({ children }: PropsWithChildren) {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'checking') return <SessionLoading />;
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}

export function GuestOnly({ children }: PropsWithChildren) {
  const { status } = useAuth();

  if (status === 'checking') return <SessionLoading />;
  if (status === 'authenticated') return <Navigate to="/" replace />;

  return children;
}
