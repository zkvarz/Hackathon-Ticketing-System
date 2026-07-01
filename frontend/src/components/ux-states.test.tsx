// UX building-block tests (HTS-032): the error-model parser maps the backend contract, the
// toaster shows/auto-dismisses/manually-dismisses, and the error boundary catches a render
// error and recovers on reset.

import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { ApiError } from '../api/client';
import type { ApiErrorBody } from '../api/types';
import { codeOf, fieldErrorsOf, messageOf } from '../lib/apiError';
import { ToastProvider, useToast } from './toast/ToastProvider';
import { ErrorBoundary } from './ErrorBoundary';

function apiError(over: Partial<ApiErrorBody>): ApiError {
  const body: ApiErrorBody = {
    timestamp: 't',
    status: 400,
    error: 'Bad Request',
    code: 'VALIDATION_FAILED',
    message: 'Request validation failed.',
    fieldErrors: [],
    ...over,
  };
  return new ApiError(body.status, body, body.message);
}

describe('error-model parser (HTS-031 contract)', () => {
  // Boundary: multiple field errors each map to their field.
  it('maps every fieldError to a {field: message} record', () => {
    const err = apiError({
      fieldErrors: [
        { field: 'title', message: 'must not be blank' },
        { field: 'teamId', message: 'must not be null' },
      ],
    });
    expect(fieldErrorsOf(err)).toEqual({
      title: 'must not be blank',
      teamId: 'must not be null',
    });
    expect(codeOf(err)).toBe('VALIDATION_FAILED');
  });

  // Boundary: an error with only a message (no field errors) surfaces that message, no fields.
  it('surfaces the message and no fields when there are no field errors', () => {
    const err = apiError({ code: 'NAME_TAKEN', message: "A team named 'x' already exists." });
    expect(fieldErrorsOf(err)).toEqual({});
    expect(messageOf(err)).toBe("A team named 'x' already exists.");
  });

  // Negative: a non-API error falls back to the provided default and UNKNOWN code.
  it('falls back for non-API errors', () => {
    expect(messageOf('boom', 'Fallback.')).toBe('Fallback.');
    expect(fieldErrorsOf(new Error('x'))).toEqual({});
    expect(codeOf(undefined)).toBe('UNKNOWN');
  });
});

describe('toaster', () => {
  function Harness() {
    const { success, error } = useToast();
    return (
      <>
        <button type="button" onClick={() => success('Saved')}>
          ok
        </button>
        <button type="button" onClick={() => error('Failed')}>
          bad
        </button>
      </>
    );
  }

  // Positive: a success toast appears (polite) then auto-dismisses. The duration is comfortably
  // large so the appear-assert can't race the dismissal; waitFor then observes the removal.
  it('shows a success toast and auto-dismisses it', async () => {
    render(
      <ToastProvider duration={600}>
        <Harness />
      </ToastProvider>,
    );
    await userEvent.click(screen.getByRole('button', { name: 'ok' }));

    expect(screen.getByRole('status')).toHaveTextContent('Saved');
    await waitFor(() => expect(screen.queryByText('Saved')).not.toBeInTheDocument(), {
      timeout: 2000,
    });
  });

  // Positive/boundary: an error toast is assertive and can be dismissed manually.
  it('shows an assertive error toast dismissible by the user', async () => {
    render(
      <ToastProvider duration={0}>
        <Harness />
      </ToastProvider>,
    );
    await userEvent.click(screen.getByRole('button', { name: 'bad' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Failed');
    await userEvent.click(screen.getByRole('button', { name: /dismiss notification/i }));
    expect(screen.queryByText('Failed')).not.toBeInTheDocument();
  });
});

describe('error boundary', () => {
  function Boom() {
    throw new Error('kaboom');
    return null;
  }

  // Negative: a thrown render error is caught and shows the recoverable fallback.
  it('renders a fallback when a child throws', () => {
    // Silence the expected React error log for this render.
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong');
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
    spy.mockRestore();
  });

  // Positive: resetting the boundary re-renders children (recovery).
  it('recovers when reset after the child stops throwing', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});

    function Toggle() {
      const [ok, setOk] = useState(false);
      return (
        <ErrorBoundary fallback={(reset) => (
          <button type="button" onClick={() => { setOk(true); reset(); }}>
            recover
          </button>
        )}>
          {ok ? <p>recovered content</p> : <Boom />}
        </ErrorBoundary>
      );
    }

    render(<Toggle />);
    await userEvent.click(screen.getByRole('button', { name: 'recover' }));
    expect(screen.getByText('recovered content')).toBeInTheDocument();
    spy.mockRestore();
  });
});
