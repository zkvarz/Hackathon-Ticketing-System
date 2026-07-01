// Auth API calls (architecture.md §8/§9). Thin typed wrappers over the shared client.

import { ApiError, apiClient } from './client';
import type { UserResponse } from './types';

export interface SignupPayload {
  email: string;
  password: string;
}

/** POST /api/auth/signup — register a new (unverified) account. */
export function signup(payload: SignupPayload) {
  return apiClient.post<UserResponse>('/auth/signup', payload);
}

/** POST /api/auth/login — authenticate and start a session. Throws ApiError on 401/403. */
export function login(email: string, password: string) {
  return apiClient.post<UserResponse>('/auth/login', { email, password });
}

/** POST /api/auth/logout — invalidate the session. */
export function logout() {
  return apiClient.post<void>('/auth/logout');
}

/** GET /api/auth/me — current user, or null when unauthenticated (401). */
export async function getCurrentUser(): Promise<UserResponse | null> {
  try {
    return await apiClient.get<UserResponse>('/auth/me');
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) return null;
    throw err;
  }
}

/** GET /api/auth/verify?token=… — verify an email address (HTS-008). */
export function verifyEmail(token: string) {
  return apiClient.get<void>(`/auth/verify?token=${encodeURIComponent(token)}`);
}

/** POST /api/auth/resend — request a fresh verification email (HTS-010). */
export function resendVerification(email: string) {
  return apiClient.post<void>('/auth/resend', { email });
}

/**
 * POST /api/auth/forgot-password — request a password reset email (HTS-038). The server always
 * responds generically (no enumeration), so the UI shows the same confirmation either way.
 */
export function forgotPassword(email: string) {
  return apiClient.post<void>('/auth/forgot-password', { email });
}

/**
 * POST /api/auth/reset-password — set a new password using the emailed token (HTS-038). Throws
 * ApiError with code TOKEN_INVALID (400) when the token is unknown/expired/consumed.
 */
export function resetPassword(token: string, password: string) {
  return apiClient.post<void>('/auth/reset-password', { token, password });
}
