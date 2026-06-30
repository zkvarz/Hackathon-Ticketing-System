// Typed fetch-based API client (architecture.md §11).
// Responsibilities:
//  - same-origin requests under /api (nginx proxies to the backend in Compose, §4);
//  - always send credentials (session cookie) and the CSRF header read from the cookie;
//  - centralize parsing of the standardized error model (§8) into a typed ApiError;
//  - tolerate empty/204 bodies and malformed JSON without throwing unexpectedly.

import type { ApiErrorBody, FieldError } from './types';

/** Base path; same-origin so the browser sends the session cookie automatically. */
export const API_BASE = '/api';

/**
 * Spring Security's default CSRF cookie/header names. The SPA reads the token the server
 * set as a cookie and echoes it back on state-changing requests (architecture.md §9).
 */
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

/** Error thrown for any non-2xx response, carrying the parsed error model when present. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];
  readonly body: ApiErrorBody | null;

  constructor(status: number, body: ApiErrorBody | null, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = body?.code ?? 'UNKNOWN';
    this.fieldErrors = body?.fieldErrors ?? [];
    this.body = body;
  }
}

function readCookie(name: string): string | null {
  // document may be undefined in non-browser contexts; guard defensively.
  if (typeof document === 'undefined' || !document.cookie) return null;
  const match = document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

/** Parse a JSON body, returning null for empty bodies or malformed JSON (boundary cases). */
async function parseJsonSafe<T>(res: Response): Promise<T | null> {
  // 204 No Content and other empty bodies have no JSON to read.
  if (res.status === 204) return null;
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text) as T;
  } catch {
    return null;
  }
}

const STATE_CHANGING = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/**
 * Resolve an `/api/...` path to a request URL. In the browser this stays same-origin (so the
 * session cookie is sent); we resolve against `location.origin` so the underlying fetch always
 * receives an absolute URL (required by Node's fetch under jsdom in tests).
 */
function resolveUrl(path: string): string {
  const relative = `${API_BASE}${path}`;
  if (typeof window !== 'undefined' && window.location?.origin) {
    return new URL(relative, window.location.origin).toString();
  }
  return relative;
}

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
}

/**
 * Core request helper. Resolves with the parsed JSON body (or null for empty responses),
 * and rejects with an {@link ApiError} for any non-2xx status.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T | null> {
  const { body, headers, method = 'GET', ...rest } = options;

  const finalHeaders = new Headers(headers);
  if (body !== undefined && !finalHeaders.has('Content-Type')) {
    finalHeaders.set('Content-Type', 'application/json');
  }
  if (STATE_CHANGING.has(method.toUpperCase())) {
    const token = readCookie(CSRF_COOKIE);
    if (token) finalHeaders.set(CSRF_HEADER, token);
  }

  const res = await fetch(resolveUrl(path), {
    method,
    credentials: 'include',
    headers: finalHeaders,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    ...rest,
  });

  if (!res.ok) {
    const errorBody = await parseJsonSafe<ApiErrorBody>(res);
    const message = errorBody?.message ?? `Request failed with status ${res.status}`;
    throw new ApiError(res.status, errorBody, message);
  }

  return parseJsonSafe<T>(res);
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'DELETE' }),
};
