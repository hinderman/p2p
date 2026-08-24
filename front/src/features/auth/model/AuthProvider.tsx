import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren,
} from 'react';

import { ApiError } from '../../../core/http/apiClient';
import { registerAuthenticatedSessionController } from '../../../core/http/authenticatedSessionController';
import { createSession, refreshSession, revokeSessions } from '../api/authApi';
import type {
  AuthenticatedSession,
  AuthStatus,
  SignInCredentials,
} from './auth.types';
import { AuthContext } from './authContext';
import { clearStoredSession, loadStoredSession, storeSession } from './sessionStorage';

const REFRESH_MARGIN_MS = 60_000;
const REFRESH_RETRY_MS = 30_000;
const MAX_TIMEOUT_MS = 2_147_000_000;

function isRejectedRefreshToken(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [status, setStatus] = useState<AuthStatus>('checking');
  const [session, setSession] = useState<AuthenticatedSession | null>(null);
  const sessionRef = useRef<AuthenticatedSession | null>(null);
  const refreshInFlight = useRef<Promise<AuthenticatedSession> | null>(null);

  const applySession = useCallback((nextSession: AuthenticatedSession) => {
    storeSession(nextSession);
    sessionRef.current = nextSession;
    setSession(nextSession);
    setStatus('authenticated');
  }, []);

  const clearSession = useCallback(() => {
    clearStoredSession();
    sessionRef.current = null;
    setSession(null);
    setStatus('anonymous');
  }, []);

  const renewSession = useCallback(
    async (refreshToken: string) => {
      if (!refreshInFlight.current) {
        refreshInFlight.current = refreshSession(refreshToken).finally(() => {
          refreshInFlight.current = null;
        });
      }

      try {
        const renewedSession = await refreshInFlight.current;
        applySession(renewedSession);
        return renewedSession;
      } catch (error) {
        if (isRejectedRefreshToken(error)) clearSession();
        throw error;
      }
    },
    [applySession, clearSession],
  );

  useLayoutEffect(
    () =>
      registerAuthenticatedSessionController({
        getAccessToken: () => sessionRef.current?.accessToken ?? null,
        refreshAccessToken: async () => {
          const currentSession = sessionRef.current;
          if (!currentSession) return null;
          return (await renewSession(currentSession.refreshToken)).accessToken;
        },
        invalidateSession: clearSession,
      }),
    [clearSession, renewSession],
  );

  useEffect(() => {
    const storedSession = loadStoredSession();
    if (!storedSession) {
      setStatus('anonymous');
      return;
    }

    const expiration = Date.parse(storedSession.accessTokenExpiresAt);
    if (expiration > Date.now() + REFRESH_MARGIN_MS) {
      sessionRef.current = storedSession;
      setSession(storedSession);
      setStatus('authenticated');
      return;
    }

    sessionRef.current = storedSession;
    setSession(storedSession);
    setStatus('authenticated');
  }, []);

  useEffect(() => {
    if (!session || status !== 'authenticated') return;

    let cancelled = false;
    let timeout: number | undefined;
    const refreshIn = Math.max(
      0,
      Date.parse(session.accessTokenExpiresAt) - Date.now() - REFRESH_MARGIN_MS,
    );

    const schedule = (delay: number) => {
      timeout = window.setTimeout(async () => {
        try {
          await renewSession(session.refreshToken);
        } catch (error) {
          if (!cancelled && !isRejectedRefreshToken(error)) {
            schedule(REFRESH_RETRY_MS);
          }
        }
      }, Math.min(delay, MAX_TIMEOUT_MS));
    };

    schedule(refreshIn);

    return () => {
      cancelled = true;
      if (timeout !== undefined) window.clearTimeout(timeout);
    };
  }, [renewSession, session, status]);

  const signIn = useCallback(
    async (credentials: SignInCredentials) => {
      const authenticatedSession = await createSession(credentials);
      applySession(authenticatedSession);
    },
    [applySession],
  );

  const signOut = useCallback(async () => {
    const revocation = sessionRef.current ? revokeSessions() : Promise.resolve();
    clearSession();
    await revocation.catch(() => undefined);
  }, [clearSession]);

  const value = useMemo(
    () => ({ status, session, establishSession: applySession, signIn, signOut }),
    [applySession, session, signIn, signOut, status],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
