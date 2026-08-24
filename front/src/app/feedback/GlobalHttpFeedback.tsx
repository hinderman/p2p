import { IonProgressBar, IonToast } from '@ionic/react';
import { useSyncExternalStore } from 'react';

import {
  clearHttpError,
  getHttpFeedbackSnapshot,
  subscribeToHttpFeedback,
  type HttpFeedbackError,
} from '../../core/http/httpFeedback';
import './GlobalHttpFeedback.css';

function globalErrorMessage(error: HttpFeedbackError): string {
  if (error.status === 0 || error.code === 'network_error') {
    return 'No fue posible conectar con el servidor. Revisa tu conexión.';
  }
  if (error.status === 403 || error.code === 'access_denied') {
    return 'No tienes permisos para realizar esta acción.';
  }
  if (error.status === 429 || error.code === 'rate_limit_exceeded') {
    return 'Se realizaron demasiadas solicitudes. Inténtalo nuevamente en un momento.';
  }
  if (error.status === 400 || error.code === 'validation_failed') {
    return 'No fue posible procesar la solicitud. Revisa la información enviada.';
  }
  if (error.status >= 500) {
    return 'El servicio no está disponible en este momento. Inténtalo nuevamente.';
  }
  return 'No fue posible completar la operación.';
}

export function GlobalHttpFeedback() {
  const feedback = useSyncExternalStore(
    subscribeToHttpFeedback,
    getHttpFeedbackSnapshot,
    getHttpFeedbackSnapshot,
  );

  return (
    <>
      {feedback.pendingRequests > 0 && (
        <IonProgressBar
          aria-label="Solicitud en curso"
          className="global-http-progress"
          type="indeterminate"
        />
      )}
      <IonToast
        buttons={[{ text: 'Cerrar', role: 'cancel' }]}
        color="dark"
        duration={5_000}
        isOpen={feedback.error !== null}
        message={feedback.error ? globalErrorMessage(feedback.error) : ''}
        position="top"
        onDidDismiss={clearHttpError}
      />
    </>
  );
}
