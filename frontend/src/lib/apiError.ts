// Central mapping of the backend error model (architecture.md §8 / HTS-031) to UI text
// (HTS-032). Every screen turns a failure into a banner/toast message and inline field errors
// the same way, so the FE stays in lockstep with the server's `code`/`message`/`fieldErrors`
// contract.

import { ApiError } from '../api/client';

/** Human-readable message for any thrown value, falling back when it isn't an ApiError. */
export function messageOf(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (err instanceof ApiError) return err.message || fallback;
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}

/**
 * Map an ApiError's `fieldErrors[]` to a `{field: message}` record for inline display. Non-API
 * errors (or errors without field errors) yield an empty map. Callers can layer feature-specific
 * `code`→field mappings on top of this base.
 */
export function fieldErrorsOf(err: unknown): Record<string, string> {
  const map: Record<string, string> = {};
  if (err instanceof ApiError) {
    for (const fe of err.fieldErrors) map[fe.field] = fe.message;
  }
  return map;
}

/** The stable machine-readable code, or a sentinel for non-API errors. */
export function codeOf(err: unknown): string {
  return err instanceof ApiError ? err.code : 'UNKNOWN';
}
