// Application root: wires the TanStack Query provider, the app-wide toast provider, and a
// top-level error boundary around the router (architecture.md §11; HTS-032).

import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { createQueryClient } from './queryClient';
import { router } from './router';
import { ErrorBoundary } from './components/ErrorBoundary';
import { ToastProvider } from './components/toast/ToastProvider';

const queryClient = createQueryClient();

export function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <RouterProvider router={router} />
        </ToastProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
