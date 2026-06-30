// Login + logout tests (HTS-012). Uses the classic MemoryRouter/Routes (not the data router)
// so programmatic navigation works under jsdom+MSW; the real app uses the data router in a
// browser where this is a non-issue. MSW drives the auth endpoints.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { server } from '../../test/msw/server';
import { AuthProvider } from '../../auth/AuthContext';
import { AppLayout } from '../../layout/AppLayout';
import { LoginPage } from './LoginPage';
import { BoardPage } from '../../pages/placeholders';

const user = {
  id: '00000000-0000-0000-0000-000000000001',
  email: 'user@example.com',
  emailVerified: true,
  createdAt: '2026-06-30T10:00:00Z',
};

function renderApp(initialPath: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<AppLayout />}>
              <Route path="/board" element={<BoardPage />} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('LoginPage', () => {
  it('logs in with valid credentials and routes to the board (positive)', async () => {
    let calls = 0;
    server.use(
      http.post('/api/auth/login', () => {
        calls += 1;
        return HttpResponse.json(user);
      }),
    );

    renderApp('/login');
    const u = userEvent.setup();
    await u.type(screen.getByLabelText('Email'), 'user@example.com');
    await u.type(screen.getByLabelText('Password'), 'password1');
    await u.click(screen.getByRole('button', { name: /^log in$/i }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Board' })).toBeInTheDocument();
    });
    expect(calls).toBe(1);
    expect(screen.getByText('user@example.com')).toBeInTheDocument();
  });

  it('blocks submit when fields are empty (negative)', async () => {
    let calls = 0;
    server.use(
      http.post('/api/auth/login', () => {
        calls += 1;
        return HttpResponse.json(user);
      }),
    );

    renderApp('/login');
    await userEvent.click(screen.getByRole('button', { name: /^log in$/i }));

    expect(await screen.findByText(/email and password are required/i)).toBeInTheDocument();
    expect(calls).toBe(0);
  });

  it('shows a generic error on 401 bad credentials (negative)', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json(
          { timestamp: 't', status: 401, error: 'Unauthorized', code: 'BAD_CREDENTIALS', message: 'x', fieldErrors: [] },
          { status: 401 },
        ),
      ),
    );

    renderApp('/login');
    const u = userEvent.setup();
    await u.type(screen.getByLabelText('Email'), 'user@example.com');
    await u.type(screen.getByLabelText('Password'), 'wrong');
    await u.click(screen.getByRole('button', { name: /^log in$/i }));

    expect(await screen.findByText(/invalid email or password/i)).toBeInTheDocument();
  });

  it('surfaces a resend affordance on 403 EMAIL_NOT_VERIFIED (boundary)', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json(
          { timestamp: 't', status: 403, error: 'Forbidden', code: 'EMAIL_NOT_VERIFIED', message: 'x', fieldErrors: [] },
          { status: 403 },
        ),
      ),
    );

    renderApp('/login');
    const u = userEvent.setup();
    await u.type(screen.getByLabelText('Email'), 'user@example.com');
    await u.type(screen.getByLabelText('Password'), 'password1');
    await u.click(screen.getByRole('button', { name: /^log in$/i }));

    expect(await screen.findByText(/isn.t verified yet/i)).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /resend verification email/i }),
    ).toBeInTheDocument();
  });

  it('logs out from the header and returns to login', async () => {
    server.use(
      http.get('/api/auth/me', () => HttpResponse.json(user)),
      http.post('/api/auth/logout', () => new HttpResponse(null, { status: 204 })),
    );

    renderApp('/board');

    await waitFor(() => {
      expect(screen.getByText('user@example.com')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: /log out/i }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    });
  });
});
