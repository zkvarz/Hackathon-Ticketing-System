// Sign-up screen (FR-S1, HTS-006). Email + password + confirm with client-side validation
// (UX only — the server stays authoritative, FR-K8 spirit), submitting to POST /api/auth/signup
// and rendering loading / success (check-email) / error states (NFR-3).

import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { signup } from '../../api/auth';
import { ApiError } from '../../api/client';

// Mirror the server bounds (architecture.md §6 / AMB-1). The server re-validates regardless.
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 128;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

interface FieldErrors {
  email?: string;
  password?: string;
  confirm?: string;
}

function validate(email: string, password: string, confirm: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!email.trim()) {
    errors.email = 'Email is required.';
  } else if (!EMAIL_RE.test(email.trim())) {
    errors.email = 'Enter a valid email address.';
  }
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

export function SignupPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);

    const found = validate(email, password, confirm);
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setSubmitting(true);
    try {
      await signup({ email: email.trim(), password });
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 409 && err.code === 'EMAIL_TAKEN') {
          setErrors({ email: 'This email is already registered.' });
        } else if (err.status === 400 && err.fieldErrors.length > 0) {
          // Map server field errors back onto the matching inputs.
          const mapped: FieldErrors = {};
          for (const fe of err.fieldErrors) {
            if (fe.field === 'email' || fe.field === 'password') {
              mapped[fe.field] = fe.message;
            }
          }
          setErrors(mapped);
          if (Object.keys(mapped).length === 0) {
            setFormError(err.message);
          }
        } else {
          // 500 and anything else: generic message; the form stays usable for a retry.
          setFormError('Something went wrong. Please try again.');
        }
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
          We&apos;ve sent a verification link to <strong>{email.trim()}</strong>. Follow it to
          activate your account, then log in.
        </p>
        <Link to="/login">Back to log in</Link>
      </section>
    );
  }

  return (
    <section className="auth-page">
      <h1>Sign up</h1>
      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={errors.email ? true : undefined}
          />
          {errors.email && <p className="field__error" role="alert">{errors.email}</p>}
        </div>

        <div className="field">
          <label htmlFor="password">Password</label>
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
          <label htmlFor="confirm">Confirm password</label>
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
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p>
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </section>
  );
}
