import {
  IonButton,
  IonIcon,
  IonInput,
  IonInputPasswordToggle,
  IonSpinner,
} from '@ionic/react';
import { alertCircleOutline, keyOutline, mailOutline } from 'ionicons/icons';
import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { ApiError } from '../../../core/http/apiClient';
import { AccessLayout } from '../../../shared/ui/AccessLayout';
import { useAuth } from '../model/authContext';
import './LoginPage.css';

type FieldErrors = {
  email?: string;
  password?: string;
};

function validateCredentials(email: string, password: string): FieldErrors {
  const errors: FieldErrors = {};
  const normalizedEmail = email.trim();

  if (!normalizedEmail) {
    errors.email = 'Ingresa tu correo electrónico.';
  } else if (normalizedEmail.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) {
    errors.email = 'Ingresa un correo electrónico válido.';
  }

  if (!password) {
    errors.password = 'Ingresa tu contraseña.';
  } else if (password.length < 8 || password.length > 256) {
    errors.password = 'La contraseña debe tener entre 8 y 256 caracteres.';
  }

  return errors;
}

function loginErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'No fue posible conectar con el servidor. Verifica tu conexión e inténtalo nuevamente.';
  }

  if (error.status === 0 || error.code === 'network_error') {
    return 'No fue posible conectar con el servidor. Verifica tu conexión e inténtalo nuevamente.';
  }

  if (error.status === 401 || error.code === 'authentication_failed') {
    return 'El correo o la contraseña no son correctos.';
  }
  if (error.status === 429 || error.code === 'rate_limit_exceeded') {
    return 'Se realizaron demasiados intentos. Espera un momento antes de continuar.';
  }
  if (error.status >= 500) {
    return 'El servicio no está disponible en este momento. Inténtalo nuevamente.';
  }

  return 'No fue posible iniciar sesión. Revisa los datos e inténtalo nuevamente.';
}

function destinationFromState(state: unknown): string {
  if (typeof state !== 'object' || state === null || !('from' in state)) return '/';
  const from = (state as { from?: unknown }).from;
  return typeof from === 'string' && from.startsWith('/') && from !== '/login' ? from : '/';
}

export function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [requestError, setRequestError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSubmitting) return;

    const errors = validateCredentials(email, password);
    setFieldErrors(errors);
    setRequestError(null);
    if (Object.keys(errors).length > 0) return;

    setIsSubmitting(true);
    try {
      await signIn({ email, password });
      navigate(destinationFromState(location.state), { replace: true });
    } catch (error) {
      setRequestError(loginErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AccessLayout
      description="Ingresa tu correo y contraseña para continuar."
      eyebrow="Acceso seguro"
      icon={keyOutline}
      title="Inicia sesión"
    >
      <form className="login-form" noValidate onSubmit={handleSubmit}>
              <IonInput
                aria-label="Correo electrónico"
                autocomplete="email"
                autocapitalize="off"
                className={fieldErrors.email ? 'ion-invalid ion-touched' : ''}
                disabled={isSubmitting}
                errorText={fieldErrors.email}
                fill="outline"
                label="Correo electrónico"
                labelPlacement="stacked"
                maxlength={254}
                name="email"
                placeholder="nombre@correo.com"
                spellcheck={false}
                type="email"
                value={email}
                onIonInput={(event) => {
                  setEmail(event.detail.value ?? '');
                  if (fieldErrors.email) setFieldErrors((current) => ({ ...current, email: undefined }));
                }}
              >
                <IonIcon aria-hidden="true" icon={mailOutline} slot="start" />
              </IonInput>

              <IonInput
                aria-label="Contraseña"
                autocomplete="current-password"
                className={fieldErrors.password ? 'ion-invalid ion-touched' : ''}
                disabled={isSubmitting}
                errorText={fieldErrors.password}
                fill="outline"
                label="Contraseña"
                labelPlacement="stacked"
                maxlength={256}
                name="password"
                type="password"
                value={password}
                onIonInput={(event) => {
                  setPassword(event.detail.value ?? '');
                  if (fieldErrors.password) setFieldErrors((current) => ({ ...current, password: undefined }));
                }}
              >
                <IonInputPasswordToggle slot="end" />
              </IonInput>

              {requestError && (
                <div className="login-error" role="alert">
                  <IonIcon aria-hidden="true" icon={alertCircleOutline} />
                  <span>{requestError}</span>
                </div>
              )}

              <IonButton className="login-submit" disabled={isSubmitting} expand="block" type="submit">
                {isSubmitting ? (
                  <>
                    <IonSpinner aria-hidden="true" name="crescent" />
                    Ingresando
                  </>
                ) : (
                  'Ingresar'
                )}
              </IonButton>

              <IonButton
                className="login-secondary"
                disabled={isSubmitting}
                expand="block"
                fill="clear"
                routerLink="/registro"
              >
                Crear una cuenta de prestamista
              </IonButton>
      </form>
    </AccessLayout>
  );
}
