// Auth API calls (architecture.md §8/§9). Thin typed wrappers over the shared client.

import { apiClient } from './client';
import type { UserResponse } from './types';

export interface SignupPayload {
  email: string;
  password: string;
}

/** POST /api/auth/signup — register a new (unverified) account. */
export function signup(payload: SignupPayload) {
  return apiClient.post<UserResponse>('/auth/signup', payload);
}

/** GET /api/auth/verify?token=… — verify an email address (HTS-008). */
export function verifyEmail(token: string) {
  return apiClient.get<void>(`/auth/verify?token=${encodeURIComponent(token)}`);
}

/** POST /api/auth/resend — request a fresh verification email (HTS-010). */
export function resendVerification(email: string) {
  return apiClient.post<void>('/auth/resend', { email });
}
