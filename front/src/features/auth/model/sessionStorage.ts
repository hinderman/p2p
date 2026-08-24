import { isAuthenticatedSession, type AuthenticatedSession } from './auth.types';

const SESSION_KEY = 'hybrid-client.auth.session.v1';

export function loadStoredSession(): AuthenticatedSession | null {
  try {
    const serializedSession = window.sessionStorage.getItem(SESSION_KEY);
    if (!serializedSession) return null;

    const session: unknown = JSON.parse(serializedSession);
    if (!isAuthenticatedSession(session)) {
      clearStoredSession();
      return null;
    }

    return session;
  } catch {
    clearStoredSession();
    return null;
  }
}

export function storeSession(session: AuthenticatedSession): void {
  try {
    window.sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
  } catch {
    // La sesión continúa en memoria cuando el navegador bloquea el almacenamiento.
  }
}

export function clearStoredSession(): void {
  try {
    window.sessionStorage.removeItem(SESSION_KEY);
  } catch {
    // No hay nada adicional que limpiar si el almacenamiento no está disponible.
  }
}
