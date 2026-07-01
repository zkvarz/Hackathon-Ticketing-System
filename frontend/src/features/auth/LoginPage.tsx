// Login screen (FR-S4, HTS-012). Authenticates via the auth context, routes to the board on
// success, shows a generic error on bad credentials, and offers a resend control when the
// account's email is not verified (FR-A7 path).

import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { ApiError } from '../../api/client';
import { ResendVerification } from './ResendVerification';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from =
    (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/board';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [notVerified, setNotVerified] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFieldError(null);
    setFormError(null);
    setNotVerified(false);

    if (!email.trim() || !password) {
      setFieldError('Email and password are required.');
      return;
    }

    setSubmitting(true);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 403 && err.code === 'EMAIL_NOT_VERIFIED') {
        setNotVerified(true);
      } else if (err instanceof ApiError && err.status === 401) {
        setFormError('Invalid email or password.');
      } else {
        setFormError('Something went wrong. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-page">
      <h1>Log in</h1>
      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>

        {fieldError && <p className="field__error" role="alert">{fieldError}</p>}
        {formError && <p className="form__error" role="alert">{formError}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>

      {notVerified && (
        <div className="login__not-verified" role="alert">
          <p>Your email address isn&apos;t verified yet.</p>
          <ResendVerification email={email.trim()} />
        </div>
      )}

      <p>
        <Link to="/forgot-password">Forgot password?</Link>
      </p>
      <p>
        Need an account? <Link to="/signup">Sign up</Link>
      </p>
    </section>
  );
}
