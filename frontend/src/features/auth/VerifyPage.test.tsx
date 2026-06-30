// Verification result screen tests (HTS-008): success, invalid/expired, missing-token
// (no API call), and the loading→terminal boundary. MSW drives the verify endpoint.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { VerifyPage } from './VerifyPage';

const tokenInvalidBody = {
  timestamp: '2026-06-30T10:00:00Z',
  status: 400,
  error: 'Bad Request',
  code: 'TOKEN_INVALID',
  message: 'The verification link is invalid or has expired.',
  fieldErrors: [],
};

describe('VerifyPage', () => {
  it('shows success and a Continue to login link on a valid token (positive)', async () => {
    server.use(
      http.get('/api/auth/verify', () =>
        HttpResponse.json({ status: 'verified', email: 'a@b.com' }),
      ),
    );

    renderWithProviders(<VerifyPage />, { route: '/verify?token=good' });

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /email verified/i })).toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: /continue to login/i })).toHaveAttribute(
      'href',
      '/login',
    );
  });

  it('shows the invalid/expired state with a resend affordance on error (negative)', async () => {
    server.use(
      http.get('/api/auth/verify', () => HttpResponse.json(tokenInvalidBody, { status: 400 })),
    );

    renderWithProviders(<VerifyPage />, { route: '/verify?token=bad' });

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /invalid or expired/i })).toBeInTheDocument();
    });
    expect(
      screen.getByRole('button', { name: /resend verification email/i }),
    ).toBeInTheDocument();
  });

  it('shows the invalid state without calling the API when the token is missing (negative)', async () => {
    let called = false;
    server.use(
      http.get('/api/auth/verify', () => {
        called = true;
        return HttpResponse.json({ status: 'verified', email: 'a@b.com' });
      }),
    );

    renderWithProviders(<VerifyPage />, { route: '/verify' });

    expect(
      await screen.findByRole('heading', { name: /invalid or expired/i }),
    ).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it('shows loading first, then resolves to success (boundary)', async () => {
    server.use(
      http.get('/api/auth/verify', async () => {
        await delay(50);
        return HttpResponse.json({ status: 'verified', email: 'a@b.com' });
      }),
    );

    renderWithProviders(<VerifyPage />, { route: '/verify?token=slow' });

    // Loading state visible while the request is in flight.
    expect(screen.getByText(/verifying your email/i)).toBeInTheDocument();

    // Then it resolves to the success terminal state.
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /email verified/i })).toBeInTheDocument();
    });
  });
});
