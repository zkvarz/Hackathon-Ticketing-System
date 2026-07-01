// Password reset screens (HTS-038): request (ForgotPasswordPage) + reset (ResetPasswordPage).
// Client validation (positive/negative/boundary) and API-contract behavior via MSW (forgot 202,
// reset 200 → routes to login, reset 400 TOKEN_INVALID surfaced). Reset navigation uses classic
// MemoryRouter/Routes with a /login stub so success routing can be asserted.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { createQueryClient } from '../../queryClient';
import { ToastProvider } from '../../components/toast/ToastProvider';
import { ForgotPasswordPage } from './ForgotPasswordPage';
import { ResetPasswordPage } from './ResetPasswordPage';

const tokenInvalidBody = {
  timestamp: '2026-06-30T10:00:00Z',
  status: 400,
  error: 'Bad Request',
  code: 'TOKEN_INVALID',
  message: 'The password reset link is invalid or has expired.',
  fieldErrors: [],
};

// Render ResetPasswordPage inside a Routes tree with a /login stub so post-success navigation
// (navigate('/login')) can be asserted (see frontend-test-router-patterns).
function renderReset(route: string) {
  const queryClient = createQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider duration={0}>
        <MemoryRouter initialEntries={[route]}>
          <Routes>
            <Route path="/reset-password" element={<ResetPasswordPage />} />
            <Route path="/login" element={<h1>Log in stub</h1>} />
            <Route path="/forgot-password" element={<h1>Forgot stub</h1>} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('ForgotPasswordPage', () => {
  it('submits a trimmed email once and shows a generic confirmation (positive, AC-1/AC-4)', async () => {
    const bodies: Array<{ email: string }> = [];
    server.use(
      http.post('/api/auth/forgot-password', async ({ request }) => {
        bodies.push((await request.json()) as { email: string });
        return new HttpResponse(JSON.stringify({ status: 'sent' }), {
          status: 202,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );

    renderWithProviders(<ForgotPasswordPage />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Email'), '  alice@example.com  ');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /check your email/i })).toBeInTheDocument();
    });
    expect(bodies).toEqual([{ email: 'alice@example.com' }]);
  });

  it('blocks an invalid email client-side without calling the API (negative)', async () => {
    let called = false;
    server.use(
      http.post('/api/auth/forgot-password', () => {
        called = true;
        return new HttpResponse(null, { status: 202 });
      }),
    );

    renderWithProviders(<ForgotPasswordPage />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Email'), 'not-an-email');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByText(/valid email address/i)).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it('shows a generic error on 500 and keeps the form usable (MSW, AC-4)', async () => {
    server.use(
      http.post('/api/auth/forgot-password', () =>
        HttpResponse.json(
          { timestamp: 't', status: 500, error: 'e', code: 'INTERNAL', message: 'boom', fieldErrors: [] },
          { status: 500 },
        ),
      ),
    );

    renderWithProviders(<ForgotPasswordPage />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Email'), 'alice@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /send reset link/i })).toBeEnabled();
  });
});

describe('ResetPasswordPage', () => {
  it('submits a valid new password with the token and routes to login (positive, AC-2)', async () => {
    const bodies: Array<{ token: string; password: string }> = [];
    server.use(
      http.post('/api/auth/reset-password', async ({ request }) => {
        bodies.push((await request.json()) as { token: string; password: string });
        return HttpResponse.json({ status: 'reset' });
      }),
    );

    renderReset('/reset-password?token=good-token');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('New password'), 'new-password1');
    await user.type(screen.getByLabelText('Confirm new password'), 'new-password1');
    await user.click(screen.getByRole('button', { name: /update password/i }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /log in stub/i })).toBeInTheDocument();
    });
    expect(bodies).toEqual([{ token: 'good-token', password: 'new-password1' }]);
  });

  it('blocks submit when the passwords do not match (negative, AC-3)', async () => {
    let called = false;
    server.use(
      http.post('/api/auth/reset-password', () => {
        called = true;
        return HttpResponse.json({ status: 'reset' });
      }),
    );

    renderReset('/reset-password?token=good-token');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('New password'), 'new-password1');
    await user.type(screen.getByLabelText('Confirm new password'), 'different1');
    await user.click(screen.getByRole('button', { name: /update password/i }));

    expect(await screen.findByText(/passwords do not match/i)).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it('blocks a 7-char password but allows 8 (boundary, AC-3)', async () => {
    const bodies: unknown[] = [];
    server.use(
      http.post('/api/auth/reset-password', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ status: 'reset' });
      }),
    );

    renderReset('/reset-password?token=good-token');
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('New password'), '1234567');
    await user.type(screen.getByLabelText('Confirm new password'), '1234567');
    await user.click(screen.getByRole('button', { name: /update password/i }));
    expect(await screen.findByText(/at least 8 characters/i)).toBeInTheDocument();
    expect(bodies).toHaveLength(0);

    await user.type(screen.getByLabelText('New password'), '8');
    await user.type(screen.getByLabelText('Confirm new password'), '8');
    await user.click(screen.getByRole('button', { name: /update password/i }));
    await waitFor(() => expect(bodies).toHaveLength(1));
  });

  it('surfaces a server TOKEN_INVALID error inline (MSW, AC-3)', async () => {
    server.use(
      http.post('/api/auth/reset-password', () =>
        HttpResponse.json(tokenInvalidBody, { status: 400 }),
      ),
    );

    renderReset('/reset-password?token=bad-token');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('New password'), 'new-password1');
    await user.type(screen.getByLabelText('Confirm new password'), 'new-password1');
    await user.click(screen.getByRole('button', { name: /update password/i }));

    expect(await screen.findByText(/invalid or has expired/i)).toBeInTheDocument();
    // Still on the reset screen (not routed to login).
    expect(screen.getByRole('heading', { name: /choose a new password/i })).toBeInTheDocument();
  });

  it('shows the invalid-link state when the token is missing (negative)', async () => {
    renderReset('/reset-password');

    expect(
      await screen.findByRole('heading', { name: /invalid or expired/i }),
    ).toBeInTheDocument();
    // No password fields are offered without a token.
    expect(screen.queryByLabelText('New password')).not.toBeInTheDocument();
  });
});
