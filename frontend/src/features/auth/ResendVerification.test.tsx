// Resend verification control tests (HTS-010): generic confirmation, in-flight disable
// (no double submit), standalone email validation, and MSW 202/500 behavior.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { ResendVerification } from './ResendVerification';

describe('ResendVerification', () => {
  it('calls the API once and shows a generic confirmation (positive)', async () => {
    let calls = 0;
    server.use(
      http.post('/api/auth/resend', () => {
        calls += 1;
        return new HttpResponse(null, { status: 202 });
      }),
    );

    renderWithProviders(<ResendVerification email="a@b.com" />);
    await userEvent.click(screen.getByRole('button', { name: /resend verification email/i }));

    expect(await screen.findByText(/a new email has been sent/i)).toBeInTheDocument();
    expect(calls).toBe(1);
  });

  it('disables the button while in flight so a double-click sends once (negative)', async () => {
    let calls = 0;
    server.use(
      http.post('/api/auth/resend', async () => {
        calls += 1;
        await delay(50);
        return new HttpResponse(null, { status: 202 });
      }),
    );

    renderWithProviders(<ResendVerification email="a@b.com" />);
    const button = screen.getByRole('button', { name: /resend verification email/i });
    const user = userEvent.setup();
    await user.click(button);
    await user.click(button); // button is disabled now → no second request

    await waitFor(() => {
      expect(screen.getByText(/a new email has been sent/i)).toBeInTheDocument();
    });
    expect(calls).toBe(1);
  });

  it('validates the email before calling when standalone (boundary)', async () => {
    let calls = 0;
    server.use(
      http.post('/api/auth/resend', () => {
        calls += 1;
        return new HttpResponse(null, { status: 202 });
      }),
    );

    renderWithProviders(<ResendVerification />); // standalone → renders its own input
    await userEvent.click(screen.getByRole('button', { name: /resend verification email/i }));

    expect(await screen.findByText(/enter a valid email address/i)).toBeInTheDocument();
    expect(calls).toBe(0);
  });

  it('shows a non-blocking error on 500 and keeps the control usable (MSW)', async () => {
    server.use(
      http.post('/api/auth/resend', () => new HttpResponse(null, { status: 500 })),
    );

    renderWithProviders(<ResendVerification email="a@b.com" />);
    await userEvent.click(screen.getByRole('button', { name: /resend verification email/i }));

    expect(await screen.findByText(/could not send right now/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /resend verification email/i })).toBeEnabled();
  });
});
