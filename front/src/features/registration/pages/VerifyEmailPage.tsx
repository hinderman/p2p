import { IonButton, IonSpinner } from '@ionic/react';
import { linkOutline, shieldCheckmarkOutline } from 'ionicons/icons';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { AccessLayout } from '../../../shared/ui/AccessLayout';
import { useAuth } from '../../auth';
import { verifyAccountEmail } from '../api/registrationApi';
import { ResendVerificationForm } from '../components/ResendVerificationForm';
import { consumeVerificationToken } from '../model/verificationToken';
import './registration.css';

type VerificationState = 'checking' | 'verifying' | 'failed';

export function VerifyEmailPage() {
  const { establishSession } = useAuth();
  const navigate = useNavigate();
  const started = useRef(false);
  const [state, setState] = useState<VerificationState>('checking');

  useEffect(() => {
    // The token is single-use and is stripped from the URL as it is read, so this
    // must run exactly once. The ref guard is the whole mechanism: cancelling the
    // request on cleanup would abandon it under StrictMode's double invocation,
    // leaving the page spinning with no token left to retry.
    if (started.current) return;
    started.current = true;

    const token = consumeVerificationToken();
    if (!token) {
      setState('failed');
      return;
    }

    setState('verifying');
    verifyAccountEmail(token)
      .then((session) => {
        establishSession(session);
        navigate('/', { replace: true });
      })
      .catch(() => setState('failed'));
  }, [establishSession, navigate]);

  if (state === 'failed') {
    return (
      <AccessLayout
        description="El enlace pudo haber vencido, haberse usado ya o estar incompleto. Pide uno nuevo con el correo que registraste."
        eyebrow="Enlace no disponible"
        icon={linkOutline}
        title="No pudimos confirmar tu correo"
      >
        <ResendVerificationForm submitLabel="Enviarme un enlace nuevo" />

        <IonButton className="registration-secondary" expand="block" fill="clear" routerLink="/login">
          Volver al inicio de sesión
        </IonButton>
      </AccessLayout>
    );
  }

  return (
    <AccessLayout
      description="Estamos activando tu cuenta con el enlace que abriste."
      eyebrow="Confirmación de correo"
      icon={shieldCheckmarkOutline}
      title="Un momento"
    >
      <div className="registration-loading" role="status" aria-label="Confirmando tu correo">
        <IonSpinner name="crescent" />
      </div>
    </AccessLayout>
  );
}
