// Test render helper: wraps UI in a fresh QueryClient + a MemoryRouter so components that
// use TanStack Query and React Router render in isolation.

import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { createQueryClient } from '../queryClient';

interface Options {
  route?: string;
}

export function renderWithProviders(ui: ReactElement, { route = '/' }: Options = {}) {
  const queryClient = createQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}
