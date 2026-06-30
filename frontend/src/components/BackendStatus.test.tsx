// API-contract tests (MSW) for the backend status widget:
//  - GET /api/health success → widget shows UP.
//  - GET /api/health failure → widget shows the error state (not a blank screen).

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import { server } from '../test/msw/server';
import { renderWithProviders } from '../test/renderWithProviders';
import { BackendStatus } from './BackendStatus';

describe('BackendStatus widget', () => {
  it('shows UP when GET /api/health succeeds', async () => {
    // Default handler already returns { status: 'UP' }.
    renderWithProviders(<BackendStatus />);

    await waitFor(() => {
      expect(screen.getByText('UP')).toBeInTheDocument();
    });
  });

  it('shows the error state when GET /api/health fails (not a blank screen)', async () => {
    server.use(
      http.get('/api/health', () =>
        HttpResponse.json(
          {
            timestamp: '2026-06-30T10:00:00Z',
            status: 503,
            error: 'Service Unavailable',
            code: 'BACKEND_DOWN',
            message: 'down',
            fieldErrors: [],
          },
          { status: 503 },
        ),
      ),
    );

    renderWithProviders(<BackendStatus />);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/backend unreachable/i);
    });
  });
});
