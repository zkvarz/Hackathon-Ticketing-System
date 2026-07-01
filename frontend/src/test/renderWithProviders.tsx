// Test render helper: wraps UI in a fresh QueryClient, the app-wide ToastProvider, and a
// MemoryRouter so components that use TanStack Query, toasts, and React Router render in
// isolation. Toasts use duration=0 (no auto-dismiss timers) for deterministic tests.

import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { createQueryClient } from '../queryClient';
import { ToastProvider } from '../components/toast/ToastProvider';

interface Options {
  route?: string;
}

export function renderWithProviders(ui: ReactElement, { route = '/' }: Options = {}) {
  const queryClient = createQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider duration={0}>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}
