import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, vi } from 'vitest';
import { clearHttpError } from '../core/http/httpFeedback';
import { App } from './App';

const SESSION_KEY = 'hybrid-client.auth.session.v1';

function storeSession(roles: string[], expiresAt = new Date(Date.now() + 300_000)) {
  window.sessionStorage.setItem(
    SESSION_KEY,
    JSON.stringify({
      userAccountId: 'd98d8980-5be6-4439-ab0d-a12da2a9da5b',
      personId: '07046205-65a7-442c-99bd-9a4265b50344',
      roles,
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      accessTokenExpiresAt: expiresAt.toISOString(),
    }),
  );
}

beforeEach(() => {
  window.sessionStorage.clear();
  window.history.replaceState({}, '', '/');
});

afterEach(() => {
  clearHttpError();
  vi.unstubAllGlobals();
});

test('redirects anonymous users to the login page', async () => {
  render(<App />);

  expect(await screen.findByText('Inicia sesión')).toBeInTheDocument();
  expect(document.querySelectorAll('ion-input')).toHaveLength(2);
  expect(screen.queryByText(/seleccionar rol/i)).not.toBeInTheDocument();
});

test('allows an authenticated session through the private guard', async () => {
  storeSession(['PAYER']);

  render(<App />);

  expect(await screen.findByText('Bienvenido a tu panel')).toBeInTheDocument();
  expect(screen.getAllByText('Mis préstamos')).not.toHaveLength(0);
  expect(screen.queryByText('Préstamos otorgados')).not.toBeInTheDocument();
});

test('combines the navigation delivered for multiple backend roles', async () => {
  storeSession(['LENDER', 'PAYER']);

  render(<App />);

  expect(await screen.findByText('Bienvenido a tu panel')).toBeInTheDocument();
  expect(screen.getAllByText('Préstamos otorgados')).not.toHaveLength(0);
  expect(screen.getAllByText('Mis préstamos')).not.toHaveLength(0);
});

test('loads the authenticated lender portfolio from the backend', async () => {
  storeSession(['LENDER']);
  window.history.replaceState({}, '', '/app/lender');
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue([{
      loanId: '018f12ab-9f42-77c1-b1f5-cbb7e284b07f',
      counterpartyPersonId: '39e4dc48-23cc-4f36-b766-105c64d82c87',
      status: 'ACTIVE',
      originalPrincipal: { amount: 1_000_000, currency: 'COP' },
      outstandingBalance: { amount: 725_500.25, currency: 'COP' },
      createdAt: '2026-08-24T12:00:00Z',
    }]),
  });
  vi.stubGlobal('fetch', fetchMock);

  render(<App />);

  expect(await screen.findByText('Saldo pendiente')).toBeInTheDocument();
  expect(screen.getByText('725.500,25 COP')).toBeInTheDocument();
  expect(screen.getByText('Ver detalle')).toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledOnce();
  const [url, request] = fetchMock.mock.calls[0] as [string, RequestInit];
  expect(url).toBe('/api/v1/loans/lender');
  expect(new Headers(request.headers).get('authorization')).toBe('Bearer access-token');
});

test('revokes the backend session and returns to login when signing out', async () => {
  storeSession(['PAYER']);
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 204,
    statusText: 'No Content',
    headers: new Headers(),
  });
  vi.stubGlobal('fetch', fetchMock);

  render(<App />);
  await screen.findByText('Bienvenido a tu panel');
  fireEvent.click(screen.getByText('Cerrar sesión'));

  expect(await screen.findByText('Inicia sesión')).toBeInTheDocument();
  expect(window.sessionStorage.getItem(SESSION_KEY)).toBeNull();
  expect(fetchMock).toHaveBeenCalledOnce();
  const request = fetchMock.mock.calls[0][1] as RequestInit;
  expect(request.method).toBe('DELETE');
  expect(new Headers(request.headers).get('authorization')).toBe('Bearer access-token');
});

test('opens a payer invitation and removes its token from the URL', async () => {
  window.history.replaceState(
    {},
    '',
    '/onboarding/payer#invitationToken=single-use-token',
  );

  render(<App />);

  expect(await screen.findByText('Activa tu acceso')).toBeInTheDocument();
  expect(document.querySelectorAll('ion-input')).toHaveLength(2);
  expect(window.location.hash).toBe('');
});

test('clears an expired session when the refresh token is rejected', async () => {
  storeSession(['PAYER'], new Date(Date.now() - 60_000));
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      statusText: 'Unauthorized',
      headers: new Headers({ 'content-type': 'application/problem+json' }),
      json: vi.fn().mockResolvedValue({
        code: 'authentication_failed',
        detail: 'Authentication failed',
      }),
    }),
  );

  render(<App />);

  expect(await screen.findByText('Inicia sesión')).toBeInTheDocument();
  expect(window.sessionStorage.getItem(SESSION_KEY)).toBeNull();
});

test('preserves the local session when refresh fails because of the network', async () => {
  window.sessionStorage.setItem(
    SESSION_KEY,
    JSON.stringify({
      userAccountId: 'd98d8980-5be6-4439-ab0d-a12da2a9da5b',
      personId: '07046205-65a7-442c-99bd-9a4265b50344',
      roles: ['PAYER'],
      accessToken: 'expired-access-token',
      refreshToken: 'refresh-token',
      accessTokenExpiresAt: new Date(Date.now() - 60_000).toISOString(),
    }),
  );
  const fetchMock = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
  vi.stubGlobal('fetch', fetchMock);

  render(<App />);

  expect(await screen.findByText('Bienvenido a tu panel')).toBeInTheDocument();
  await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
  expect(window.sessionStorage.getItem(SESSION_KEY)).not.toBeNull();
});
