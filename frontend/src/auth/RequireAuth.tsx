// Route guard (HTS-014, FR-A12). Renders the protected routes only when authenticated; while
// the initial `me` lookup is in flight it shows a top-level loading state (avoiding a flash of
// the login screen), and when unauthenticated it redirects to /login preserving the intended
// destination so login can return the user there.

import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { Loading } from '../components/Loading';

export function RequireAuth() {
  const { user, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="app-loading">
        <Loading label="Loading…" />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
