import { runtimeConfig } from '../config/runtime';
import {
  getCurrentAccessToken,
  invalidateAuthenticatedSession,
  refreshAccessTokenOnce,
} from './authenticatedSessionController';
import { beginHttpRequest, publishHttpError } from './httpFeedback';

type ProblemDetails = {
  code?: string;
  detail?: string;
  title?: string;
  violations?: Record<string, string>;
};

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly violations: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

type ApiRequestOptions = Omit<RequestInit, 'body'> & {
  authentication?: 'none' | 'required';
  body?: BodyInit | null;
  globalError?: boolean;
  globalLoading?: boolean;
  json?: unknown;
};

function resolveApiUrl(path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${runtimeConfig.apiBaseUrl}${normalizedPath}`;
}

function isProblemDetails(value: unknown): value is ProblemDetails {
  return typeof value === 'object' && value !== null;
}

async function apiErrorFromResponse(response: Response): Promise<ApiError> {
  const contentType = response.headers.get('content-type') ?? '';
  const payload = contentType.includes('json')
    ? await response.json().catch(() => undefined)
    : undefined;
  const problem = isProblemDetails(payload) ? payload : undefined;

  return new ApiError(
    response.status,
    problem?.code ?? 'unexpected_error',
    problem?.detail ?? problem?.title ?? response.statusText,
    problem?.violations,
  );
}

function normalizeTransportError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (error instanceof DOMException && error.name === 'AbortError') {
    return new ApiError(0, 'request_aborted', 'The request was cancelled');
  }
  return new ApiError(0, 'network_error', 'The server could not be reached');
}

async function executeRequest<T>(
  path: string,
  options: ApiRequestOptions,
  hasRetriedAuthentication: boolean,
): Promise<T> {
  const {
    authentication = 'required',
    json,
    headers: initialHeaders,
    ...requestOptions
  } = options;
  const headers = new Headers(initialHeaders);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');

  let requestAccessToken: string | null = null;
  if (authentication === 'required') {
    requestAccessToken = getCurrentAccessToken();
    if (!requestAccessToken) {
      throw new ApiError(401, 'authentication_required', 'An authenticated session is required');
    }
    headers.set('Authorization', `Bearer ${requestAccessToken}`);
  }

  if (json !== undefined) headers.set('Content-Type', 'application/json');

  let response: Response;
  try {
    response = await fetch(resolveApiUrl(path), {
      ...requestOptions,
      body: json === undefined ? requestOptions.body : JSON.stringify(json),
      headers,
    });
  } catch (error) {
    throw normalizeTransportError(error);
  }

  if (response.status === 401 && authentication === 'required') {
    if (hasRetriedAuthentication) {
      invalidateAuthenticatedSession();
      throw await apiErrorFromResponse(response);
    }

    const latestAccessToken = getCurrentAccessToken();
    if (latestAccessToken && latestAccessToken !== requestAccessToken) {
      return executeRequest<T>(path, options, true);
    }

    try {
      const refreshedAccessToken = await refreshAccessTokenOnce();
      if (!refreshedAccessToken) {
        invalidateAuthenticatedSession();
        throw await apiErrorFromResponse(response);
      }
    } catch (error) {
      const refreshError = normalizeTransportError(error);
      if (refreshError.status === 401) invalidateAuthenticatedSession();
      throw refreshError;
    }

    return executeRequest<T>(path, options, true);
  }

  if (!response.ok) throw await apiErrorFromResponse(response);

  // A successful response that declares no JSON body has none to parse: 204 for a
  // completed change, 202 for one the backend accepted and will finish later.
  const declaresJsonBody = (response.headers.get('content-type') ?? '').includes('json');
  if (response.status === 204 || !declaresJsonBody) {
    return undefined as T;
  }

  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError(response.status, 'invalid_response', 'The server returned an invalid response');
  }
}

export async function apiClient<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { globalError = true, globalLoading = true, ...requestOptions } = options;
  const finishRequest = globalLoading ? beginHttpRequest() : () => undefined;

  try {
    return await executeRequest<T>(path, requestOptions, false);
  } catch (error) {
    const apiError = normalizeTransportError(error);
    if (globalError && apiError.status !== 401 && apiError.code !== 'request_aborted') {
      publishHttpError({ status: apiError.status, code: apiError.code });
    }
    throw apiError;
  } finally {
    finishRequest();
  }
}
