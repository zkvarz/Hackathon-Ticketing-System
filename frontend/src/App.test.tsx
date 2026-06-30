// App shell routing tests (positive + SPA-fallback boundary):
//  - the layout renders the header and the active route's page;
//  - an unknown deep path renders the not-found page (the SPA equivalent of nginx fallback,
//    so direct navigation never yields a blank screen — ticket AC-4).

import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { createQueryClient } from './queryClient';
import { routes } from './router';

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
  it('renders the header and the active route page on /board', () => {
    renderAt('/board');

    // Header shell present.
    expect(screen.getByText('Ticketing System')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Board' })).toBeInTheDocument();

    // The active route's page renders inside the layout.
    expect(screen.getByRole('heading', { name: 'Board' })).toBeInTheDocument();
  });

  it('renders a tickets detail route with its id param', () => {
    renderAt('/tickets/TCK-1042');
    expect(screen.getByRole('heading', { name: 'Ticket TCK-1042' })).toBeInTheDocument();
  });

  it('renders the not-found page for an unknown path (SPA fallback)', () => {
    renderAt('/totally/unknown/deep/path');
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });
});
