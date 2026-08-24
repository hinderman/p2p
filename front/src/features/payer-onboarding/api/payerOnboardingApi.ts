import { apiClient } from '../../../core/http/apiClient';
import type { AuthenticatedSession } from '../../auth';

type CompletePayerOnboardingInput = {
  invitationToken: string;
  password: string;
};

export function completePayerOnboarding(input: CompletePayerOnboardingInput) {
  return apiClient<AuthenticatedSession>('/api/v1/onboarding/payer', {
    authentication: 'none',
    globalError: false,
    globalLoading: false,
    method: 'POST',
    json: input,
  });
}
