// App shell routing tests (positive + SPA-fallback boundary):
//  - the layout renders the header and the active route's page (authenticated, since app
//    routes are guarded from HTS-014);
//  - an unknown deep path renders the not-found page (the SPA equivalent of nginx fallback,
//    so direct navigation never yields a blank screen).

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { server } from './test/msw/server';
import { createQueryClient } from './queryClient';
import { routes } from './router';

const authedUser = {
  id: '00000000-0000-0000-0000-000000000001',
  email: 'user@example.com',
  emailVerified: true,
  createdAt: '2026-06-30T10:00:00Z',
};

function authenticate() {
  server.use(http.get('/api/auth/me', () => HttpResponse.json(authedUser)));
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = createQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

describe('App shell', () => {
  it('renders the header and the active route page on /board (authenticated)', async () => {
    authenticate();
    renderAt('/board');

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Board' })).toBeInTheDocument();
    });
    expect(screen.getByText('Ticketing System')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Board' })).toBeInTheDocument();
  });

  it('renders the ticket detail route for its id param (authenticated)', async () => {
    authenticate();
    server.use(
      http.get('/api/teams', () =>
        HttpResponse.json([{ id: 't1', name: 'Payments', epicCount: 0, ticketCount: 1, createdAt: 'x', modifiedAt: 'x' }]),
      ),
      http.get('/api/epics', () => HttpResponse.json([])),
      http.get('/api/tickets/TCK-1042/comments', () => HttpResponse.json([])),
      http.get('/api/tickets/TCK-1042', () =>
        HttpResponse.json({
          id: 'TCK-1042',
          teamId: 't1',
          epicId: null,
          epicTitle: null,
          type: 'bug',
          state: 'new',
          title: 'Sample ticket',
          body: 'Body',
          createdBy: 'u1',
          createdByEmail: 'user@example.com',
          createdAt: '2026-06-30T10:00:00Z',
          modifiedAt: '2026-06-30T10:00:00Z',
        }),
      ),
    );
    renderAt('/tickets/TCK-1042');

    expect(await screen.findByDisplayValue('Sample ticket')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Ticket' })).toBeInTheDocument();
  });

  it('renders the not-found page for an unknown path (SPA fallback)', () => {
    renderAt('/totally/unknown/deep/path');
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });
});
