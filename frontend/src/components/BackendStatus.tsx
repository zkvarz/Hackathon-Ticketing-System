// "Backend status" widget (ticket HTS-003): calls GET /api/health through the nginx
// /api proxy to prove SPA↔backend connectivity. Demonstrates the NFR-3 state primitives
// in their loading / error / success forms.

import { useHealth } from '../api/health';
import { Loading } from './Loading';
import { ErrorState } from './ErrorState';

export function BackendStatus() {
  const { data, isLoading, isError, refetch } = useHealth();

  if (isLoading) {
    return <Loading label="Checking backend…" />;
  }

  if (isError || data?.status !== 'UP') {
    return (
      <ErrorState
        title="Backend unreachable"
        message="Could not reach the backend health endpoint."
        onRetry={() => void refetch()}
      />
    );
  }

  return (
    <div role="status" className="backend-status backend-status--up">
      Backend: <strong>{data.status}</strong>
    </div>
  );
}
