import { afterEach, expect, test, vi } from 'vitest';

import {
  registerLender,
  resendAccountVerification,
  verifyAccountEmail,
} from './registrationApi';

const authenticatedSession = {
  userAccountId: 'd98d8980-5be6-4439-ab0d-a12da2a9da5b',
  personId: '07046205-65a7-442c-99bd-9a4265b50344',
  roles: ['LENDER'],
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  accessTokenExpiresAt: '2030-01-01T00:00:00Z',
};

function stubFetch(response: Partial<Response> & { json?: unknown }) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    headers: new Headers({ 'content-type': 'application/json' }),
    ...response,
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

test('registers a lender using the backend contract', async () => {
  const fetchMock = stubFetch({ status: 202, headers: new Headers() });

  await registerLender({
    email: 'carolina@example.com',
    firstName: 'Carolina',
    lastName: 'Restrepo',
    password: 'mesa verde caliente',
  });

  const [url, request] = fetchMock.mock.calls[0] as [string, RequestInit];
  expect(url).toBe('/api/v1/registration/lender');
  expect(request.method).toBe('POST');
  expect(request.body).toBe(
    JSON.stringify({
      email: 'carolina@example.com',
      firstName: 'Carolina',
      lastName: 'Restrepo',
      password: 'mesa verde caliente',
    }),
  );
});

/** The accepted response carries no body; the client must not choke on that. */
test('accepts an empty 202 response without a parse error', async () => {
  stubFetch({ status: 202, headers: new Headers() });

  await expect(resendAccountVerification('carolina@example.com')).resolves.toBeUndefined();
});

test('redeems the verification token and returns the session', async () => {
  const fetchMock = stubFetch({ json: vi.fn().mockResolvedValue(authenticatedSession) });

  await expect(verifyAccountEmail('single-use-token')).resolves.toEqual(authenticatedSession);

  const [url, request] = fetchMock.mock.calls[0] as [string, RequestInit];
  expect(url).toBe('/api/v1/registration/verification');
  expect(request.body).toBe(JSON.stringify({ verificationToken: 'single-use-token' }));
});

/** The token belongs in the body only: a query string would reach server logs. */
test('never puts the verification token in the URL', async () => {
  const fetchMock = stubFetch({ json: vi.fn().mockResolvedValue(authenticatedSession) });

  await verifyAccountEmail('single-use-token');

  const [url] = fetchMock.mock.calls[0] as [string];
  expect(url).not.toContain('single-use-token');
});
