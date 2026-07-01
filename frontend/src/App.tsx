// Application root: wires a top-level error boundary and the TanStack Query provider around the
// router (architecture.md §11; HTS-032). The app-wide ToastProvider lives in the router root
// (router.tsx) so every route — and tests that mount `routes` directly — share one toaster.

import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { createQueryClient } from './queryClient';
import { router } from './router';
import { ErrorBoundary } from './components/ErrorBoundary';

const queryClient = createQueryClient();

export function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
