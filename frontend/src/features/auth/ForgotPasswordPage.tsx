// Forgot-password request screen (HTS-038, FR-S stretch). Collects an email and calls
// POST /api/auth/forgot-password, then shows a generic confirmation regardless of whether the
// account exists (no enumeration — mirrors the backend, HTS-037). Loading / error states (NFR-3).

import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { forgotPassword } from '../../api/auth';
import { ApiError } from '../../api/client';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFieldError(null);
    setFormError(null);

    const trimmed = email.trim();
    if (!trimmed) {
      setFieldError('Email is required.');
      return;
    }
    if (!EMAIL_RE.test(trimmed)) {
      setFieldError('Enter a valid email address.');
      return;
    }

    setSubmitting(true);
    try {
      await forgotPassword(trimmed);
      setDone(true);
    } catch (err) {
      // The endpoint is generic on success; only real transport/server failures land here.
      if (err instanceof ApiError) {
        setFormError('Something went wrong. Please try again.');
      } else {
        setFormError('Network error. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <section className="auth-page">
        <h1>Check your email</h1>
        <p role="status">
          If an account exists for <strong>{email.trim()}</strong>, we&apos;ve sent a link to reset
          your password. The link expires shortly, so use it soon.
        </p>
        <Link to="/login">Back to log in</Link>
      </section>
    );
  }

  return (
    <section className="auth-page">
      <h1>Forgot your password?</h1>
      <p>Enter your email and we&apos;ll send you a link to reset it.</p>
      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={fieldError ? true : undefined}
          />
          {fieldError && <p className="field__error" role="alert">{fieldError}</p>}
        </div>

        {formError && <p className="form__error" role="alert">{formError}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Sending…' : 'Send reset link'}
        </button>
      </form>
      <p>
        Remembered it? <Link to="/login">Log in</Link>
      </p>
    </section>
  );
}
