// Default MSW request handlers for component/API-contract tests. Individual tests override
// these with server.use(...) to exercise success / validation-error / server-error paths.

import { http, HttpResponse } from 'msw';

export const handlers = [
  // Happy-path backend health (architecture.md §8).
  http.get('/api/health', () => HttpResponse.json({ status: 'UP' })),
  // Default to anonymous; tests that need a signed-in user override this with server.use().
  http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })),
  // Default to no epics so screens that load the team's epics (e.g. the board's epic filter,
  // HTS-030) don't error on the unhandled request; tests that assert on epics override this.
  http.get('/api/epics', () => HttpResponse.json([])),
];
