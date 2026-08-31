import { IonButton, IonIcon, IonInput, IonSpinner } from '@ionic/react';
import { alertCircleOutline, checkmarkCircleOutline, mailOutline } from 'ionicons/icons';
import { useState, type FormEvent } from 'react';

import { resendAccountVerification } from '../api/registrationApi';
import { registrationErrorMessage } from '../model/registrationMessages';

type ResendVerificationFormProps = {
  initialEmail?: string;
  showEmailField?: boolean;
  submitLabel?: string;
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Asks the backend for a replacement link.
 *
 * The confirmation deliberately says a link "was sent if the address is
 * awaiting confirmation": the endpoint answers the same for every address, and
 * claiming more here would leak exactly what it refuses to.
 */
export function ResendVerificationForm({
  initialEmail = '',
  showEmailField = true,
  submitLabel = 'Reenviar enlace',
}: ResendVerificationFormProps) {
  const [email, setEmail] = useState(initialEmail);
  const [emailError, setEmailError] = useState<string>();
  const [requestError, setRequestError] = useState<string | null>(null);
  const [wasSent, setWasSent] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSubmitting) return;

    const normalizedEmail = email.trim();
    if (!normalizedEmail || normalizedEmail.length > 254 || !EMAIL_PATTERN.test(normalizedEmail)) {
      setEmailError('Ingresa un correo electrónico válido.');
      return;
    }

    setEmailError(undefined);
    setRequestError(null);
    setWasSent(false);
    setIsSubmitting(true);
    try {
      await resendAccountVerification(normalizedEmail);
      setWasSent(true);
    } catch (error) {
      setRequestError(registrationErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form className="registration-form" noValidate onSubmit={handleSubmit}>
      {showEmailField && (
        <IonInput
          aria-label="Correo electrónico"
          autocapitalize="off"
          autocomplete="email"
          className={emailError ? 'ion-invalid ion-touched' : ''}
          disabled={isSubmitting}
          errorText={emailError}
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
            if (emailError) setEmailError(undefined);
          }}
        >
          <IonIcon aria-hidden="true" icon={mailOutline} slot="start" />
        </IonInput>
      )}

      {wasSent && (
        <div className="registration-success" role="status">
          <IonIcon aria-hidden="true" icon={checkmarkCircleOutline} />
          <span>
            Si ese correo tiene una cuenta pendiente de confirmar, enviamos un enlace nuevo. El
            anterior deja de servir.
          </span>
        </div>
      )}

      {requestError && (
        <div className="registration-error" role="alert">
          <IonIcon aria-hidden="true" icon={alertCircleOutline} />
          <span>{requestError}</span>
        </div>
      )}

      <IonButton
        className="registration-secondary"
        disabled={isSubmitting}
        expand="block"
        fill="outline"
        type="submit"
      >
        {isSubmitting ? (
          <>
            <IonSpinner aria-hidden="true" name="crescent" />
            Enviando
          </>
        ) : (
          submitLabel
        )}
      </IonButton>
    </form>
  );
}
