import { afterEach, describe, expect, test, vi } from 'vitest';

import { apiClient, ApiError } from './apiClient';
import { registerAuthenticatedSessionController } from './authenticatedSessionController';
import { clearHttpError, getHttpFeedbackSnapshot } from './httpFeedback';

let unregisterController: (() => void) | undefined;

function response(status: number, payload: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 401 ? 'Unauthorized' : 'OK',
    headers: new Headers({ 'content-type': 'application/problem+json' }),
    json: vi.fn().mockResolvedValue(payload),
  } as unknown as Response;
}

function registerController(
  controller: Parameters<typeof registerAuthenticatedSessionController>[0],
) {
  unregisterController = registerAuthenticatedSessionController(controller);
}

afterEach(() => {
  clearHttpError();
  unregisterController?.();
  unregisterController = undefined;
  vi.unstubAllGlobals();
});

describe('apiClient authentication', () => {
  test('adds the current bearer token automatically', async () => {
    registerController({
      getAccessToken: () => 'current-token',
      refreshAccessToken: vi.fn(),
      invalidateSession: vi.fn(),
    });
    const fetchMock = vi.fn().mockResolvedValue(response(200, { value: 'ok' }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiClient<{ value: string }>('/api/v1/private')).resolves.toEqual({ value: 'ok' });

    const request = fetchMock.mock.calls[0][1] as RequestInit;
    expect(new Headers(request.headers).get('authorization')).toBe('Bearer current-token');
  });

  test('refreshes and retries exactly once after a 401', async () => {
    let accessToken = 'expired-token';
    const refreshAccessToken = vi.fn().mockImplementation(async () => {
      accessToken = 'renewed-token';
      return accessToken;
    });
    registerController({
      getAccessToken: () => accessToken,
      refreshAccessToken,
      invalidateSession: vi.fn(),
    });
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(401, { code: 'authentication_required' }))
      .mockResolvedValueOnce(response(200, { value: 'ok' }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiClient<{ value: string }>('/api/v1/private')).resolves.toEqual({ value: 'ok' });

    expect(refreshAccessToken).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const retry = fetchMock.mock.calls[1][1] as RequestInit;
    expect(new Headers(retry.headers).get('authorization')).toBe('Bearer renewed-token');
  });

  test('shares one refresh between concurrent unauthorized requests', async () => {
    let accessToken = 'expired-token';
    let resolveRefresh!: (token: string) => void;
    const refreshAccessToken = vi.fn().mockImplementation(
      () =>
        new Promise<string>((resolve) => {
          resolveRefresh = resolve;
        }),
    );
    registerController({
      getAccessToken: () => accessToken,
      refreshAccessToken,
      invalidateSession: vi.fn(),
    });
    const fetchMock = vi.fn().mockImplementation((_url: string, request: RequestInit) => {
      const authorization = new Headers(request.headers).get('authorization');
      return Promise.resolve(
        authorization === 'Bearer renewed-token'
          ? response(200, { value: 'ok' })
          : response(401, { code: 'authentication_required' }),
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    const firstRequest = apiClient<{ value: string }>('/api/v1/first');
    const secondRequest = apiClient<{ value: string }>('/api/v1/second');
    await vi.waitFor(() => expect(refreshAccessToken).toHaveBeenCalledOnce());

    accessToken = 'renewed-token';
    resolveRefresh(accessToken);

    await expect(Promise.all([firstRequest, secondRequest])).resolves.toEqual([
      { value: 'ok' },
      { value: 'ok' },
    ]);
    expect(refreshAccessToken).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  test('invalidates the local session when the refresh token is rejected', async () => {
    const invalidateSession = vi.fn();
    registerController({
      getAccessToken: () => 'expired-token',
      refreshAccessToken: vi
        .fn()
        .mockRejectedValue(new ApiError(401, 'authentication_failed', 'Invalid refresh token')),
      invalidateSession,
    });
    const fetchMock = vi
      .fn()
      .mockResolvedValue(response(401, { code: 'authentication_required' }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiClient('/api/v1/private')).rejects.toMatchObject({
      status: 401,
      code: 'authentication_failed',
    });
    expect(invalidateSession).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  test('does not enter an infinite retry when the renewed token is rejected', async () => {
    let accessToken = 'expired-token';
    const invalidateSession = vi.fn();
    registerController({
      getAccessToken: () => accessToken,
      refreshAccessToken: vi.fn().mockImplementation(async () => {
        accessToken = 'renewed-token';
        return accessToken;
      }),
      invalidateSession,
    });
    const fetchMock = vi
      .fn()
      .mockResolvedValue(response(401, { code: 'authentication_required' }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiClient('/api/v1/private')).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(invalidateSession).toHaveBeenCalledOnce();
  });

  test('keeps explicitly public requests free of authorization headers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response(200, { value: 'public' }));
    vi.stubGlobal('fetch', fetchMock);

    await apiClient('/api/v1/public', { authentication: 'none' });

    const request = fetchMock.mock.calls[0][1] as RequestInit;
    expect(new Headers(request.headers).has('authorization')).toBe(false);
  });

  test('normalizes connection failures as API errors', async () => {
    registerController({
      getAccessToken: () => 'current-token',
      refreshAccessToken: vi.fn(),
      invalidateSession: vi.fn(),
    });
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(apiClient('/api/v1/private')).rejects.toMatchObject({
      status: 0,
      code: 'network_error',
    });
  });

  test('preserves the centralized problem code, detail and field violations', async () => {
    registerController({
      getAccessToken: () => 'current-token',
      refreshAccessToken: vi.fn(),
      invalidateSession: vi.fn(),
    });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        response(400, {
          code: 'validation_failed',
          detail: 'One or more request fields are invalid',
          violations: { amount: 'must be greater than zero' },
        }),
      ),
    );

    await expect(apiClient('/api/v1/private')).rejects.toMatchObject({
      status: 400,
      code: 'validation_failed',
      message: 'One or more request fields are invalid',
      violations: { amount: 'must be greater than zero' },
    });
  });

  test('reports global loading for one logical request, including its retry', async () => {
    let accessToken = 'expired-token';
    let resolveRetry!: (value: Response) => void;
    registerController({
      getAccessToken: () => accessToken,
      refreshAccessToken: vi.fn().mockImplementation(async () => {
        accessToken = 'renewed-token';
        return accessToken;
      }),
      invalidateSession: vi.fn(),
    });
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(response(401, { code: 'authentication_required' }))
        .mockImplementationOnce(
          () => new Promise<Response>((resolve) => { resolveRetry = resolve; }),
        ),
    );

    const request = apiClient<{ value: string }>('/api/v1/private');
    expect(getHttpFeedbackSnapshot().pendingRequests).toBe(1);
    await vi.waitFor(() => expect(accessToken).toBe('renewed-token'));
    expect(getHttpFeedbackSnapshot().pendingRequests).toBe(1);

    resolveRetry(response(200, { value: 'ok' }));
    await expect(request).resolves.toEqual({ value: 'ok' });
    expect(getHttpFeedbackSnapshot().pendingRequests).toBe(0);
  });

  test('publishes final backend errors to the global feedback channel', async () => {
    registerController({
      getAccessToken: () => 'current-token',
      refreshAccessToken: vi.fn(),
      invalidateSession: vi.fn(),
    });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(response(403, { code: 'access_denied' })),
    );

    await expect(apiClient('/api/v1/private')).rejects.toMatchObject({ status: 403 });
    expect(getHttpFeedbackSnapshot()).toMatchObject({
      pendingRequests: 0,
      error: { status: 403, code: 'access_denied' },
    });
  });
});
