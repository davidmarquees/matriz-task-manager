import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import * as authApi from '../api/auth';
import type { CurrentUser } from '../api/auth';
import { SESSION_EXPIRED_EVENT } from '../api/client';

type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous'; sessionExpired: boolean }
  | { status: 'authenticated'; user: CurrentUser };

interface AuthContextValue {
  state: AuthState;
  login(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
  refresh(): Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** Keeps the authenticated user; the server session is the only source of truth. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  const refresh = useCallback(async () => {
    try {
      setState({ status: 'authenticated', user: await authApi.getMe() });
    } catch {
      setState({ status: 'anonymous', sessionExpired: false });
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    const onExpired = () => setState({ status: 'anonymous', sessionExpired: true });
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      refresh,
      async login(email, password) {
        const user = await authApi.login(email, password);
        setState({ status: 'authenticated', user });
      },
      async logout() {
        try {
          await authApi.logout();
        } finally {
          setState({ status: 'anonymous', sessionExpired: false });
        }
      },
    }),
    [state, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return value;
}
