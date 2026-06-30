// Email verification result screen (FR-S2, HTS-008). Reads ?token= from the URL, calls
// GET /api/auth/verify, and renders loading / success (Continue to login) / invalid-or-expired
// (with a resend affordance, wired in HTS-010). No auto-login (FR-A9).

import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useSearchParams } from 'react-router-dom';
import { verifyEmail } from '../../api/auth';
import { Loading } from '../../components/Loading';
import { ResendVerification } from './ResendVerification';

export function VerifyPage() {
  const [params] = useSearchParams();
  const token = params.get('token');

  const query = useQuery({
    queryKey: ['verify', token],
    queryFn: () => verifyEmail(token as string),
    enabled: !!token, // missing token → never calls the API (AC-3)
    retry: false,
  });

  // Missing token, or the verify call failed → invalid/expired state with a resend affordance.
  if (!token || query.isError) {
    return (
      <section className="auth-page">
        <h1>Link invalid or expired</h1>
        <p role="alert">
          This verification link is invalid or has expired. Request a new one and try again.
        </p>
        {/* Resend control (HTS-010): standalone here, so it collects the email itself. */}
        <ResendVerification />
        <p>
          <Link to="/login">Back to log in</Link>
        </p>
      </section>
    );
  }

  if (query.isSuccess) {
    return (
      <section className="auth-page">
        <h1>Email verified</h1>
        <p role="status">Your email is verified. You can now log in.</p>
        <Link to="/login">Continue to login</Link>
      </section>
    );
  }

  // Token present, request in flight.
  return (
    <section className="auth-page">
      <Loading label="Verifying your email…" />
    </section>
  );
}
