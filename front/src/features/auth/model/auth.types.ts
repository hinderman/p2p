export type AuthenticatedSession = {
  userAccountId: string;
  personId: string;
  roles: string[];
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
};

export type SignInCredentials = {
  email: string;
  password: string;
};

export type AuthStatus = 'checking' | 'authenticated' | 'anonymous';

export function isAuthenticatedSession(value: unknown): value is AuthenticatedSession {
  if (typeof value !== 'object' || value === null) return false;

  const session = value as Partial<AuthenticatedSession>;
  return (
    typeof session.userAccountId === 'string' &&
    typeof session.personId === 'string' &&
    Array.isArray(session.roles) &&
    session.roles.every((role) => typeof role === 'string') &&
    typeof session.accessToken === 'string' &&
    session.accessToken.length > 0 &&
    typeof session.refreshToken === 'string' &&
    session.refreshToken.length > 0 &&
    typeof session.accessTokenExpiresAt === 'string' &&
    Number.isFinite(Date.parse(session.accessTokenExpiresAt))
  );
}
