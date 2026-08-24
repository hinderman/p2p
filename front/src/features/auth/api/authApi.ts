import { apiClient } from '../../../core/http/apiClient';
import type { AuthenticatedSession, SignInCredentials } from '../model/auth.types';

export function createSession(credentials: SignInCredentials) {
  return apiClient<AuthenticatedSession>('/api/v1/auth/sessions', {
    authentication: 'none',
    globalError: false,
    globalLoading: false,
    method: 'POST',
    json: {
      email: credentials.email.trim(),
      password: credentials.password,
    },
  });
}

export function refreshSession(refreshToken: string) {
  return apiClient<AuthenticatedSession>('/api/v1/auth/sessions/refresh', {
    authentication: 'none',
    globalError: false,
    globalLoading: false,
    method: 'POST',
    json: { refreshToken },
  });
}

export function revokeSessions() {
  return apiClient<void>('/api/v1/auth/sessions', {
    globalError: false,
    globalLoading: false,
    method: 'DELETE',
  });
}
