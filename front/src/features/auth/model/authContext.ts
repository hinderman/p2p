import { createContext, useContext } from 'react';

import type {
  AuthenticatedSession,
  AuthStatus,
  SignInCredentials,
} from './auth.types';

export type AuthContextValue = {
  status: AuthStatus;
  session: AuthenticatedSession | null;
  establishSession: (session: AuthenticatedSession) => void;
  signIn: (credentials: SignInCredentials) => Promise<void>;
  signOut: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth debe utilizarse dentro de AuthProvider');
  }
  return context;
}
