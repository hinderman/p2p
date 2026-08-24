import { afterEach, expect, test, vi } from 'vitest';

import { completePayerOnboarding } from './payerOnboardingApi';

const authenticatedSession = {
  userAccountId: 'd98d8980-5be6-4439-ab0d-a12da2a9da5b',
  personId: '07046205-65a7-442c-99bd-9a4265b50344',
  roles: ['PAYER'],
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  accessTokenExpiresAt: '2030-01-01T00:00:00Z',
};

afterEach(() => {
  vi.unstubAllGlobals();
});

test('redeems the invitation using the backend contract', async () => {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(authenticatedSession),
  });
  vi.stubGlobal('fetch', fetchMock);

  await expect(
    completePayerOnboarding({
      invitationToken: 'single-use-token',
      password: 'secure-password',
    }),
  ).resolves.toEqual(authenticatedSession);

  const [url, request] = fetchMock.mock.calls[0] as [string, RequestInit];
  expect(url).toBe('/api/v1/onboarding/payer');
  expect(request.method).toBe('POST');
  expect(request.body).toBe(
    JSON.stringify({
      invitationToken: 'single-use-token',
      password: 'secure-password',
    }),
  );
});
