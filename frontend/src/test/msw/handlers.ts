// Default MSW request handlers for component/API-contract tests. Individual tests override
// these with server.use(...) to exercise success / validation-error / server-error paths.

import { http, HttpResponse } from 'msw';

export const handlers = [
  // Happy-path backend health (architecture.md §8).
  http.get('/api/health', () => HttpResponse.json({ status: 'UP' })),
];
