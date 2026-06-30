// Health query hook — proves SPA↔backend connectivity through the /api proxy
// (architecture.md §4). Used by the BackendStatus widget (ticket HTS-003 AC-3).

import { useQuery } from '@tanstack/react-query';
import { apiClient } from './client';
import type { HealthStatus } from './types';

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: () => apiClient.get<HealthStatus>('/health'),
    // Health is cheap and informational; don't hammer it or retry aggressively.
    retry: false,
    refetchOnWindowFocus: false,
  });
}
