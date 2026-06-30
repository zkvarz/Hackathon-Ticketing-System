// Factory for the TanStack Query client (architecture.md §11). A factory (not a shared
// singleton) keeps each test isolated with its own cache.

import { QueryClient } from '@tanstack/react-query';

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        refetchOnWindowFocus: false,
      },
    },
  });
}
