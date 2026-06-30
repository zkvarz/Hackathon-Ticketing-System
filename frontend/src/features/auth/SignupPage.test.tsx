// Sign-up screen tests (HTS-006): client validation (positive/negative/boundary) and
// API-contract behavior via MSW (201 success, 409 email-taken, 400 field errors, 500 generic).

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { SignupPage } from './SignupPage';

const created = {
  id: '00000000-0000-0000-0000-000000000001',
  email: 'alice@example.com',
  emailVerified: false,
  createdAt: '2026-06-30T10:00:00Z',
};

async function fillForm(
  email: string,
  password: string,
  confirm: string = password,
) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.type(screen.getByLabelText('Confirm password'), confirm);
  return user;
}

describe('SignupPage', () => {
  it('submits a trimmed payload once and shows the check-email success state (positive)', async () => {
    const bodies: Array<{ email: string; password: string }> = [];
    server.use(
      http.post('/api/auth/signup', async ({ request }) => {
        bodies.push((await request.json()) as { email: string; password: string });
        return HttpResponse.json(created, { status: 201 });
      }),
    );

    renderWithProviders(<SignupPage />);
    const user = await fillForm('  alice@example.com  ', 'password1');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /check your email/i })).toBeInTheDocument();
    });
    expect(bodies).toHaveLength(1);
    expect(bodies[0]).toEqual({ email: 'alice@example.com', password: 'password1' });
  });

  it('blocks submit when confirm password does not match (negative)', async () => {
    let called = false;
    server.use(
      http.post('/api/auth/signup', () => {
        called = true;
        return HttpResponse.json(created, { status: 201 });
      }),
    );

    renderWithProviders(<SignupPage />);
    const user = await fillForm('alice@example.com', 'password1', 'password2');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText(/passwords do not match/i)).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it('blocks submit on an invalid email (negative)', async () => {
    renderWithProviders(<SignupPage />);
    const user = await fillForm('not-an-email', 'password1');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText(/enter a valid email address/i)).toBeInTheDocument();
  });

  it('blocks a 7-char password but allows 8 (boundary)', async () => {
    const bodies: unknown[] = [];
    server.use(
      http.post('/api/auth/signup', async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json(created, { status: 201 });
      }),
    );

    renderWithProviders(<SignupPage />);
    const user = userEvent.setup();

    // 7 chars → blocked.
    await user.type(screen.getByLabelText('Email'), 'a@b.com');
    await user.type(screen.getByLabelText('Password'), '1234567');
    await user.type(screen.getByLabelText('Confirm password'), '1234567');
    await user.click(screen.getByRole('button', { name: /create account/i }));
    expect(await screen.findByText(/at least 8 characters/i)).toBeInTheDocument();
    expect(bodies).toHaveLength(0);

    // Extend to 8 chars → allowed, submits.
    await user.type(screen.getByLabelText('Password'), '8');
    await user.type(screen.getByLabelText('Confirm password'), '8');
    await user.click(screen.getByRole('button', { name: /create account/i }));
    await waitFor(() => expect(bodies).toHaveLength(1));
  });

  it('maps a 409 EMAIL_TAKEN to an email field error (MSW)', async () => {
    server.use(
      http.post('/api/auth/signup', () =>
        HttpResponse.json(
          {
            timestamp: '2026-06-30T10:00:00Z',
            status: 409,
            error: 'Conflict',
            code: 'EMAIL_TAKEN',
            message: 'taken',
            fieldErrors: [],
          },
          { status: 409 },
        ),
      ),
    );

    renderWithProviders(<SignupPage />);
    const user = await fillForm('alice@example.com', 'password1');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText(/already registered/i)).toBeInTheDocument();
  });

  it('maps 400 field errors back onto fields (MSW)', async () => {
    server.use(
      http.post('/api/auth/signup', () =>
        HttpResponse.json(
          {
            timestamp: '2026-06-30T10:00:00Z',
            status: 400,
            error: 'Bad Request',
            code: 'VALIDATION_FAILED',
            message: 'Request validation failed.',
            fieldErrors: [{ field: 'password', message: 'Too weak per server.' }],
          },
          { status: 400 },
        ),
      ),
    );

    renderWithProviders(<SignupPage />);
    const user = await fillForm('alice@example.com', 'password1');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText(/too weak per server/i)).toBeInTheDocument();
  });

  it('shows a generic error on 500 and keeps the form usable (MSW)', async () => {
    server.use(
      http.post('/api/auth/signup', () =>
        HttpResponse.json(
          {
            timestamp: '2026-06-30T10:00:00Z',
            status: 500,
            error: 'Internal Server Error',
            code: 'INTERNAL',
            message: 'boom',
            fieldErrors: [],
          },
          { status: 500 },
        ),
      ),
    );

    renderWithProviders(<SignupPage />);
    const user = await fillForm('alice@example.com', 'password1');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();
    // Form still present and usable (button enabled again).
    expect(screen.getByRole('button', { name: /create account/i })).toBeEnabled();
  });
});
