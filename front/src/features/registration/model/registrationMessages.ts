import { ApiError } from '../../../core/http/apiClient';

/**
 * Turns a failed registration call into something a person can act on.
 *
 * The backend's own wording is English and phrased for an API client, so the
 * cases worth distinguishing are mapped here by their stable `code` rather than
 * by passing the server text through.
 */
export function registrationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError) || error.status === 0 || error.code === 'network_error') {
    return 'No fue posible conectar con el servidor. Verifica tu conexión e inténtalo nuevamente.';
  }
  if (error.status === 429 || error.code === 'rate_limit_exceeded') {
    return 'Se realizaron demasiados intentos con este correo. Espera un momento antes de continuar.';
  }
  if (error.code === 'business_rule_violation') {
    return 'Esa contraseña no cumple la política de seguridad. Elige una más larga y menos común.';
  }
  if (error.status >= 500) {
    return 'El servicio no está disponible en este momento. Inténtalo nuevamente.';
  }
  return 'No fue posible completar el registro. Revisa los datos e inténtalo nuevamente.';
}
