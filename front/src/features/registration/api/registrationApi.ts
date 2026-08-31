import { apiClient } from '../../../core/http/apiClient';
import type { AuthenticatedSession } from '../../auth';

type RegisterLenderInput = {
  email: string;
  firstName: string;
  lastName: string;
  password: string;
};

/**
 * Resolves for any address the backend accepts, whether or not it already has an
 * account: the endpoint deliberately answers the same either way, so the UI must
 * not try to infer one case from the other.
 */
export function registerLender(input: RegisterLenderInput) {
  return apiClient<void>('/api/v1/registration/lender', {
    authentication: 'none',
    globalError: false,
    globalLoading: false,
    method: 'POST',
    json: input,
  });
}

export function verifyAccountEmail(verificationToken: string) {
  return apiClient<AuthenticatedSession>('/api/v1/registration/verification', {
    authentication: 'none',
    globalError: false,
    globalLoading: false,
    method: 'POST',
    json: { verificationToken },
  });
}

export function resendAccountVerification(email: string) {
  return apiClient<void>('/api/v1/registration/verification/resend', {
    authentication: 'none',
    globalError: false,
    globalLoading: false,
    method: 'POST',
    json: { email },
  });
}
