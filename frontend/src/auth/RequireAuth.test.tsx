// Route guard tests (HTS-014): authenticated access, redirect-to-login when anonymous, the
// loading state while `me` is in flight, and a mid-session 401 clearing auth → redirect.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { server } from '../test/msw/server';
import { AuthProvider } from './AuthContext';
import { RequireAuth } from './RequireAuth';
import { apiClient } from '../api/client';

const user = {
  id: '1',
  email: 'user@example.com',
  emailVerified: true,
  createdAt: '2026-06-30T10:00:00Z',
};

// A protected page that also exposes a button which triggers a 401 data call (mid-session expiry).
function ProtectedBoard() {
  return (
    <div>
      <h1>Board</h1>
      <button type="button" onClick={() => void apiClient.get('/teams').catch(() => {})}>
        load
      </button>
    </div>
  );
}

function renderApp(initialPath: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<h1>Log in</h1>} />
            <Route element={<RequireAuth />}>
              <Route path="/board" element={<ProtectedBoard />} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('RequireAuth', () => {
  it('renders the guarded route when authenticated (positive)', async () => {
    server.use(http.get('/api/auth/me', () => HttpResponse.json(user)));
    renderApp('/board');
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Board' })).toBeInTheDocument();
    });
  });

  it('redirects to login when unauthenticated (negative)', async () => {
    server.use(http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })));
    renderApp('/board');
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    });
  });

  it('shows a loading state while me is in flight, then the route (boundary)', async () => {
    server.use(
      http.get('/api/auth/me', async () => {
        await delay(50);
        return HttpResponse.json(user);
      }),
    );
    renderApp('/board');

    expect(screen.getByRole('status')).toHaveTextContent(/loading/i);
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Board' })).toBeInTheDocument();
    });
  });

  it('redirects to login when a mid-session data call returns 401', async () => {
    server.use(
      http.get('/api/auth/me', () => HttpResponse.json(user)),
      http.get('/api/teams', () => new HttpResponse(null, { status: 401 })),
    );
    renderApp('/board');

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Board' })).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: 'load' }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    });
  });
});
