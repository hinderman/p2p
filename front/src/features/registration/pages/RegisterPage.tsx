import { IonButton, IonIcon, IonInput, IonInputPasswordToggle, IonSpinner } from '@ionic/react';
import {
  alertCircleOutline,
  informationCircleOutline,
  mailOutline,
  mailUnreadOutline,
  personAddOutline,
} from 'ionicons/icons';
import { useState, type FormEvent } from 'react';

import { AccessLayout } from '../../../shared/ui/AccessLayout';
import { registerLender } from '../api/registrationApi';
import { ResendVerificationForm } from '../components/ResendVerificationForm';
import {
  MAXIMUM_PASSWORD_LENGTH,
  MINIMUM_PASSWORD_LENGTH,
  validateRegistration,
  type RegistrationFieldErrors,
} from '../model/registrationForm';
import { registrationErrorMessage } from '../model/registrationMessages';
import './registration.css';

function MailboxHandoff({ email }: { email: string }) {
  return (
    <AccessLayout
      description="Te enviamos un enlace para confirmar tu correo. Ábrelo para activar tu cuenta."
      eyebrow="Revisa tu correo"
      icon={mailUnreadOutline}
      title="Confirma tu correo"
    >
      <p className="registration-mailbox">{email}</p>

      <div className="registration-notice">
        <IonIcon aria-hidden="true" icon={informationCircleOutline} />
        <span>
          El enlace vence en 24 horas y solo se puede usar una vez. Si no lo ves, revisa la carpeta
          de correo no deseado.
        </span>
      </div>

      <ResendVerificationForm initialEmail={email} showEmailField={false} />

      <IonButton className="registration-secondary" expand="block" fill="clear" routerLink="/login">
        Volver al inicio de sesión
      </IonButton>
    </AccessLayout>
  );
}

export function RegisterPage() {
  const [email, setEmail] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirmation, setPasswordConfirmation] = useState('');
  const [fieldErrors, setFieldErrors] = useState<RegistrationFieldErrors>({});
  const [requestError, setRequestError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);

  function clearFieldError(field: keyof RegistrationFieldErrors) {
    setFieldErrors((current) => (current[field] ? { ...current, [field]: undefined } : current));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSubmitting) return;

    const draft = { email, firstName, lastName, password, passwordConfirmation };
    const errors = validateRegistration(draft);
    setFieldErrors(errors);
    setRequestError(null);
    if (Object.keys(errors).length > 0) return;

    const normalizedEmail = email.trim();
    setIsSubmitting(true);
    try {
      await registerLender({
        email: normalizedEmail,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        password,
      });
      setPassword('');
      setPasswordConfirmation('');
      setRegisteredEmail(normalizedEmail);
    } catch (error) {
      setRequestError(registrationErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  if (registeredEmail) return <MailboxHandoff email={registeredEmail} />;

  return (
    <AccessLayout
      description="Crea tu cuenta para registrar préstamos y revisar los pagos que reportan tus deudores."
      eyebrow="Nueva cuenta"
      icon={personAddOutline}
      title="Regístrate como prestamista"
    >
      <form className="registration-form" noValidate onSubmit={handleSubmit}>
        <div className="registration-names">
          <IonInput
            aria-label="Nombre"
            autocomplete="given-name"
            className={fieldErrors.firstName ? 'ion-invalid ion-touched' : ''}
            disabled={isSubmitting}
            errorText={fieldErrors.firstName}
            fill="outline"
            label="Nombre"
            labelPlacement="stacked"
            maxlength={120}
            name="firstName"
            value={firstName}
            onIonInput={(event) => {
              setFirstName(event.detail.value ?? '');
              clearFieldError('firstName');
            }}
          />

          <IonInput
            aria-label="Apellido"
            autocomplete="family-name"
            className={fieldErrors.lastName ? 'ion-invalid ion-touched' : ''}
            disabled={isSubmitting}
            errorText={fieldErrors.lastName}
            fill="outline"
            label="Apellido"
            labelPlacement="stacked"
            maxlength={120}
            name="lastName"
            value={lastName}
            onIonInput={(event) => {
              setLastName(event.detail.value ?? '');
              clearFieldError('lastName');
            }}
          />
        </div>

        <IonInput
          aria-label="Correo electrónico"
          autocapitalize="off"
          autocomplete="email"
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
            clearFieldError('email');
          }}
        >
          <IonIcon aria-hidden="true" icon={mailOutline} slot="start" />
        </IonInput>

        <IonInput
          aria-label="Contraseña"
          autocomplete="new-password"
          className={fieldErrors.password ? 'ion-invalid ion-touched' : ''}
          disabled={isSubmitting}
          errorText={fieldErrors.password}
          fill="outline"
          helperText={`Al menos ${MINIMUM_PASSWORD_LENGTH} caracteres. Una frase larga es más segura que un símbolo.`}
          label="Contraseña"
          labelPlacement="stacked"
          maxlength={MAXIMUM_PASSWORD_LENGTH}
          name="password"
          type="password"
          value={password}
          onIonInput={(event) => {
            setPassword(event.detail.value ?? '');
            clearFieldError('password');
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
          maxlength={MAXIMUM_PASSWORD_LENGTH}
          name="passwordConfirmation"
          type="password"
          value={passwordConfirmation}
          onIonInput={(event) => {
            setPasswordConfirmation(event.detail.value ?? '');
            clearFieldError('passwordConfirmation');
          }}
        >
          <IonInputPasswordToggle slot="end" />
        </IonInput>

        {requestError && (
          <div className="registration-error" role="alert">
            <IonIcon aria-hidden="true" icon={alertCircleOutline} />
            <span>{requestError}</span>
          </div>
        )}

        <IonButton
          className="registration-submit"
          disabled={isSubmitting}
          expand="block"
          type="submit"
        >
          {isSubmitting ? (
            <>
              <IonSpinner aria-hidden="true" name="crescent" />
              Creando cuenta
            </>
          ) : (
            'Crear cuenta'
          )}
        </IonButton>

        <IonButton
          className="registration-secondary"
          disabled={isSubmitting}
          expand="block"
          fill="clear"
          routerLink="/login"
        >
          Ya tengo una cuenta
        </IonButton>
      </form>
    </AccessLayout>
  );
}
