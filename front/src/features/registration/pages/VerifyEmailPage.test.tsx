import { render, screen, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

import { VerifyEmailPage } from './VerifyEmailPage';

const establishSession = vi.fn();
const navigate = vi.fn();

vi.mock('../../auth', () => ({
  useAuth: () => ({ establishSession }),
}));

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => navigate,
}));

const session = {
  userAccountId: 'd98d8980-5be6-4439-ab0d-a12da2a9da5b',
  personId: '07046205-65a7-442c-99bd-9a4265b50344',
  roles: ['LENDER'],
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  accessTokenExpiresAt: '2030-01-01T00:00:00Z',
};

/**
 * StrictMode is on in this app, so the effect runs twice on mount. The token is
 * consumed from the URL by the first run and cannot be read again, which is why
 * these tests render exactly as production does.
 */
function renderPage() {
  return render(
    <StrictMode>
      <MemoryRouter>
        <VerifyEmailPage />
      </MemoryRouter>
    </StrictMode>,
  );
}

beforeEach(() => {
  establishSession.mockClear();
  navigate.mockClear();
  window.history.replaceState({}, '', '/registro/verificacion');
});

afterEach(() => {
  vi.unstubAllGlobals();
});

test('redeems the token once and signs the person in', async () => {
  window.history.replaceState({}, '', '/registro/verificacion#verificationToken=single-use-token');
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(session),
  });
  vi.stubGlobal('fetch', fetchMock);

  renderPage();

  await waitFor(() => expect(establishSession).toHaveBeenCalledWith(session));
  expect(navigate).toHaveBeenCalledWith('/', { replace: true });
  expect(fetchMock).toHaveBeenCalledOnce();
  expect(window.location.hash).toBe('');
});

test('offers a replacement link when the token was already spent', async () => {
  window.history.replaceState({}, '', '/registro/verificacion#verificationToken=spent-token');
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      headers: new Headers({ 'content-type': 'application/problem+json' }),
      json: vi.fn().mockResolvedValue({ code: 'verification_invalid' }),
    }),
  );

  renderPage();

  await waitFor(() =>
    expect(screen.getByText('No pudimos confirmar tu correo')).toBeInTheDocument(),
  );
  expect(establishSession).not.toHaveBeenCalled();
});

test('does not call the backend when the link carries no token', async () => {
  const fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);

  renderPage();

  await waitFor(() =>
    expect(screen.getByText('No pudimos confirmar tu correo')).toBeInTheDocument(),
  );
  expect(fetchMock).not.toHaveBeenCalled();
});
