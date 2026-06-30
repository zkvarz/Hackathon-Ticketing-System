// API client tests:
//  - Negative: a 4xx/5xx response surfaces a typed, parsed error-model object.
//  - Boundary: an empty/204 response and a malformed JSON body do not crash the client.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../test/msw/server';
import { ApiError, apiClient } from './client';
import type { ApiErrorBody } from './types';

describe('apiClient', () => {
  it('parses the standardized error model on a 4xx response (negative)', async () => {
    const body: ApiErrorBody = {
      timestamp: '2026-06-30T10:00:00Z',
      status: 409,
      error: 'Conflict',
      code: 'TEAM_HAS_CHILDREN',
      message: 'Team has tickets or epics and cannot be deleted.',
      fieldErrors: [{ field: 'name', message: 'must be unique' }],
    };
    server.use(
      http.delete('/api/teams/:id', () => HttpResponse.json(body, { status: 409 })),
    );

    const err = await apiClient
      .delete('/teams/abc')
      .then(() => null)
      .catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('TEAM_HAS_CHILDREN');
    expect(err.message).toBe('Team has tickets or epics and cannot be deleted.');
    expect(err.fieldErrors).toEqual([{ field: 'name', message: 'must be unique' }]);
    expect(err.body).toEqual(body);
  });

  it('surfaces a generic ApiError on a 5xx with no parseable body (negative)', async () => {
    server.use(
      http.get('/api/boom', () => new HttpResponse('', { status: 500 })),
    );

    const err = await apiClient
      .get('/boom')
      .then(() => null)
      .catch((e) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(500);
    expect(err.code).toBe('UNKNOWN');
    expect(err.body).toBeNull();
    expect(err.message).toContain('500');
  });

  it('returns null for a 204 No Content response (boundary)', async () => {
    server.use(
      http.post('/api/things', () => new HttpResponse(null, { status: 204 })),
    );

    await expect(apiClient.post('/things', { a: 1 })).resolves.toBeNull();
  });

  it('returns null for a 2xx with a malformed JSON body instead of throwing (boundary)', async () => {
    server.use(
      http.get('/api/garbage', () =>
        new HttpResponse('not-json{', {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );

    await expect(apiClient.get('/garbage')).resolves.toBeNull();
  });
});
