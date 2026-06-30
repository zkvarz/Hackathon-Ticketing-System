// Auth context (HTS-012). Bootstraps the current user from GET /api/auth/me and exposes
// login/logout. Because the session lives in an HttpOnly cookie, a page refresh re-establishes
// auth by re-fetching `me` (NFR-2). Route guards + global 401 handling are added in HTS-014.

import { createContext, useCallback, useContext, useEffect, useMemo, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getCurrentUser, login as loginRequest, logout as logoutRequest } from '../api/auth';
import { setUnauthorizedHandler } from '../api/client';
import type { UserResponse } from '../api/types';

const AUTH_KEY = ['auth', 'me'] as const;

interface AuthContextValue {
  user: UserResponse | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<UserResponse>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: AUTH_KEY,
    queryFn: getCurrentUser,
    retry: false,
    staleTime: Infinity,
  });

  const login = useCallback(
    async (email: string, password: string) => {
      const user = await loginRequest(email.trim(), password);
      queryClient.setQueryData(AUTH_KEY, user);
      return user as UserResponse;
    },
    [queryClient],
  );

  // Any 401 from a data call clears auth state; RequireAuth then redirects to login (HTS-014).
  useEffect(() => {
    setUnauthorizedHandler(() => queryClient.setQueryData(AUTH_KEY, null));
    return () => setUnauthorizedHandler(null);
  }, [queryClient]);

  const logout = useCallback(async () => {
    try {
      await logoutRequest();
    } finally {
      queryClient.setQueryData(AUTH_KEY, null);
    }
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({ user: data ?? null, isLoading, login, logout }),
    [data, isLoading, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
