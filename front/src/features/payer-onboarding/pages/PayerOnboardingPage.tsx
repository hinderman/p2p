import {
  IonButton,
  IonIcon,
  IonInput,
  IonInputPasswordToggle,
  IonSpinner,
} from '@ionic/react';
import {
  alertCircleOutline,
  informationCircleOutline,
  linkOutline,
  shieldCheckmarkOutline,
} from 'ionicons/icons';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';

import { ApiError } from '../../../core/http/apiClient';
import { AccessLayout } from '../../../shared/ui/AccessLayout';
import { useAuth } from '../../auth';
import { completePayerOnboarding } from '../api/payerOnboardingApi';
import { consumeInvitationToken } from '../model/invitationToken';
import './PayerOnboardingPage.css';

type FieldErrors = {
  password?: string;
  passwordConfirmation?: string;
};

function validatePasswords(password: string, confirmation: string): FieldErrors {
  const errors: FieldErrors = {};

  if (!password) {
    errors.password = 'Ingresa una contraseña.';
  } else if (password.length < 12 || password.length > 128) {
    errors.password = 'La contraseña debe tener entre 12 y 128 caracteres.';
  }

  if (!confirmation) {
    errors.passwordConfirmation = 'Confirma tu contraseña.';
  } else if (password !== confirmation) {
    errors.passwordConfirmation = 'Las contraseñas no coinciden.';
  }

  return errors;
}

function onboardingErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'No fue posible conectar con el servidor. Verifica tu conexión e inténtalo nuevamente.';
  }
  if (error.status === 0 || error.code === 'network_error') {
    return 'No fue posible conectar con el servidor. Verifica tu conexión e inténtalo nuevamente.';
  }
  if (error.status >= 500) {
    return 'El servicio no está disponible en este momento. Inténtalo nuevamente.';
  }
  return 'No fue posible completar la activación. Revisa los datos e inténtalo nuevamente.';
}

function InvitationUnavailable() {
  return (
    <AccessLayout
      description="El enlace puede haber vencido, haber sido utilizado o estar incompleto. Solicita una nueva invitación al prestamista."
      eyebrow="Invitación no disponible"
      icon={linkOutline}
      title="No pudimos abrir la invitación"
    >
      <IonButton expand="block" fill="outline" routerLink="/login">
        Ir al inicio de sesión
      </IonButton>
    </AccessLayout>
  );
}

export function PayerOnboardingPage() {
  const { establishSession } = useAuth();
  const navigate = useNavigate();
  const tokenCaptured = useRef(false);
  const [invitationToken, setInvitationToken] = useState<string | null>();
  const [password, setPassword] = useState('');
  const [passwordConfirmation, setPasswordConfirmation] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [requestError, setRequestError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (tokenCaptured.current) return;
    tokenCaptured.current = true;
    setInvitationToken(consumeInvitationToken());
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!invitationToken || isSubmitting) return;

    const errors = validatePasswords(password, passwordConfirmation);
    setFieldErrors(errors);
    setRequestError(null);
    if (Object.keys(errors).length > 0) return;

    setIsSubmitting(true);
    try {
      const session = await completePayerOnboarding({ invitationToken, password });
      setPassword('');
      setPasswordConfirmation('');
      establishSession(session);
      navigate('/', { replace: true });
    } catch (error) {
      if (
        error instanceof ApiError &&
        (error.code === 'invitation_invalid' || error.code === 'resource_not_found')
      ) {
        setPassword('');
        setPasswordConfirmation('');
        setInvitationToken(null);
      } else {
        setRequestError(onboardingErrorMessage(error));
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  if (invitationToken === undefined) {
    return (
      <AccessLayout
        description="Estamos preparando tu invitación de acceso."
        eyebrow="Invitación de pago"
        icon={shieldCheckmarkOutline}
        title="Un momento"
      >
        <div className="onboarding-loading" role="status" aria-label="Preparando invitación">
          <IonSpinner name="crescent" />
        </div>
      </AccessLayout>
    );
  }

  if (invitationToken === null) return <InvitationUnavailable />;

  return (
    <AccessLayout
      description="Usa tu contraseña actual si ya tienes una cuenta o crea una para tu primer acceso."
      eyebrow="Invitación de pago"
      icon={shieldCheckmarkOutline}
      title="Activa tu acceso"
    >
      <form className="onboarding-form" noValidate onSubmit={handleSubmit}>
        <IonInput
          aria-label="Contraseña"
          autocomplete="new-password"
          className={fieldErrors.password ? 'ion-invalid ion-touched' : ''}
          disabled={isSubmitting}
          errorText={fieldErrors.password}
          fill="outline"
          helperText="Entre 12 y 128 caracteres."
          label="Contraseña"
          labelPlacement="stacked"
          maxlength={128}
          name="password"
          type="password"
          value={password}
          onIonInput={(event) => {
            setPassword(event.detail.value ?? '');
            if (fieldErrors.password) {
              setFieldErrors((current) => ({ ...current, password: undefined }));
            }
          }}
        >
          <IonInputPasswordToggle slot="end" />
        </IonInput>

        <IonInput
          aria-label="Confirmar contraseña"
          autocomplete="new-password"
          className={fieldErrors.passwordConfirmation ? 'ion-invalid ion-touched' : ''}
          disabled={isSubmitting}
          errorText={fieldErrors.passwordConfirmation}
          fill="outline"
          label="Confirmar contraseña"
          labelPlacement="stacked"
          maxlength={128}
          name="passwordConfirmation"
          type="password"
          value={passwordConfirmation}
          onIonInput={(event) => {
            setPasswordConfirmation(event.detail.value ?? '');
            if (fieldErrors.passwordConfirmation) {
              setFieldErrors((current) => ({ ...current, passwordConfirmation: undefined }));
            }
          }}
        >
          <IonInputPasswordToggle slot="end" />
        </IonInput>

        <div className="onboarding-notice">
          <IonIcon aria-hidden="true" icon={informationCircleOutline} />
          <span>
            Al continuar, tu cuenta quedará activada y se aceptará el préstamo asociado a esta invitación.
          </span>
        </div>

        {requestError && (
          <div className="onboarding-error" role="alert">
            <IonIcon aria-hidden="true" icon={alertCircleOutline} />
            <span>{requestError}</span>
          </div>
        )}

        <IonButton
          className="onboarding-submit"
          disabled={isSubmitting}
          expand="block"
          type="submit"
        >
          {isSubmitting ? (
            <>
              <IonSpinner aria-hidden="true" name="crescent" />
              Activando
            </>
          ) : (
            'Activar cuenta y continuar'
          )}
        </IonButton>
      </form>
    </AccessLayout>
  );
}
