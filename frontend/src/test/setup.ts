// Global Vitest setup: jest-dom matchers + MSW lifecycle.

import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './msw/server';

// Fail fast on requests that no handler covers, so missing mocks surface as errors.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
