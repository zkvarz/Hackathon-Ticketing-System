// Reset-password screen (HTS-038). Reads ?token= from the URL, collects a new password + confirm
// with client-side validation (length 8..128, match — UX only; the server re-validates), submits
// to POST /api/auth/reset-password, then routes to /login on success. Missing token → invalid
// state; server token errors (TOKEN_INVALID) surfaced inline (NFR-3).

import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { resetPassword } from '../../api/auth';
import { ApiError } from '../../api/client';
import { useToast } from '../../components/toast/ToastProvider';

const PASSWORD_MIN = 8;
const PASSWORD_MAX = 128;

interface FieldErrors {
  password?: string;
  confirm?: string;
}

function validate(password: string, confirm: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!password) {
    errors.password = 'Password is required.';
  } else if (password.length < PASSWORD_MIN) {
    errors.password = `Password must be at least ${PASSWORD_MIN} characters.`;
  } else if (password.length > PASSWORD_MAX) {
    errors.password = `Password must be at most ${PASSWORD_MAX} characters.`;
  }
  if (confirm !== password) {
    errors.confirm = 'Passwords do not match.';
  }
  return errors;
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const navigate = useNavigate();
  const toast = useToast();

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Missing token → the link is unusable; steer the user to request a fresh one.
  if (!token) {
    return (
      <section className="auth-page">
        <h1>Link invalid or expired</h1>
        <p role="alert">
          This password reset link is invalid or has expired. Request a new one and try again.
        </p>
        <p>
          <Link to="/forgot-password">Request a new link</Link>
        </p>
        <p>
          <Link to="/login">Back to log in</Link>
        </p>
      </section>
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);

    const found = validate(password, confirm);
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setSubmitting(true);
    try {
      await resetPassword(token as string, password);
      toast.success('Password updated. Please log in.');
      navigate('/login', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 400 && err.code === 'TOKEN_INVALID') {
          setFormError(
            'This password reset link is invalid or has expired. Request a new one and try again.',
          );
        } else if (err.status === 400 && err.fieldErrors.length > 0) {
          const mapped: FieldErrors = {};
          for (const fe of err.fieldErrors) {
            if (fe.field === 'password') mapped.password = fe.message;
          }
          setErrors(mapped);
          if (Object.keys(mapped).length === 0) setFormError(err.message);
        } else {
          setFormError('Something went wrong. Please try again.');
        }
      } else {
        setFormError('Network error. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-page">
      <h1>Choose a new password</h1>
      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="password">New password</label>
          <input
            id="password"
            name="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={errors.password ? true : undefined}
          />
          {errors.password && <p className="field__error" role="alert">{errors.password}</p>}
        </div>

        <div className="field">
          <label htmlFor="confirm">Confirm new password</label>
          <input
            id="confirm"
            name="confirm"
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            aria-invalid={errors.confirm ? true : undefined}
          />
          {errors.confirm && <p className="field__error" role="alert">{errors.confirm}</p>}
        </div>

        {formError && <p className="form__error" role="alert">{formError}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Updating…' : 'Update password'}
        </button>
      </form>
      <p>
        <Link to="/login">Back to log in</Link>
      </p>
    </section>
  );
}
