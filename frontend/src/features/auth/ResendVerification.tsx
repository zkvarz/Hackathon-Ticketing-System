// Shared "Resend verification email" control (HTS-010), reused by the login screen
// (account-not-verified case) and the verification-result error state.
//
// Mirrors the backend's no-enumeration behavior: the confirmation is always generic, never
// revealing whether the account exists. The button is disabled while a request is in flight
// (no double-submit). When no `email` prop is supplied it renders its own validated input.

import { useState, type FormEvent } from 'react';
import { resendVerification } from '../../api/auth';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

interface Props {
  /** When provided (e.g. the email typed on the login form), no input is shown. */
  email?: string;
}

export function ResendVerification({ email: fixedEmail }: Props) {
  const [email, setEmail] = useState(fixedEmail ?? '');
  const [inputError, setInputError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const standalone = fixedEmail === undefined;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setInputError(null);

    const target = (fixedEmail ?? email).trim();
    if (standalone && (!target || !EMAIL_RE.test(target))) {
      setInputError('Enter a valid email address.');
      return;
    }

    setSubmitting(true);
    try {
      await resendVerification(target);
      setDone(true);
    } catch {
      // Non-blocking: keep the control usable for a retry.
      setError('Could not send right now. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <p role="status" className="resend__done">
        If that account needs verification, a new email has been sent.
      </p>
    );
  }

  return (
    <form className="resend" onSubmit={handleSubmit} noValidate>
      {standalone && (
        <div className="field">
          <label htmlFor="resend-email">Email</label>
          <input
            id="resend-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={inputError ? true : undefined}
          />
          {inputError && <p className="field__error" role="alert">{inputError}</p>}
        </div>
      )}
      <button type="submit" disabled={submitting}>
        {submitting ? 'Sending…' : 'Resend verification email'}
      </button>
      {error && <p className="form__error" role="alert">{error}</p>}
    </form>
  );
}
