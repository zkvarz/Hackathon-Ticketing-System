// Positive component tests: the NFR-3 state primitives render their respective states.

import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Loading } from './Loading';
import { Empty } from './Empty';
import { ErrorState } from './ErrorState';

describe('UX state components (NFR-3)', () => {
  it('Loading renders a live status with its label', () => {
    render(<Loading label="Fetching teams…" />);
    const status = screen.getByRole('status');
    expect(status).toHaveTextContent('Fetching teams…');
  });

  it('Empty renders title and message', () => {
    render(<Empty title="No teams" message="Create your first team." />);
    expect(screen.getByText('No teams')).toBeInTheDocument();
    expect(screen.getByText('Create your first team.')).toBeInTheDocument();
  });

  it('Error renders an alert and triggers retry', async () => {
    const onRetry = vi.fn();
    render(<ErrorState title="Boom" message="It failed." onRetry={onRetry} />);

    expect(screen.getByRole('alert')).toHaveTextContent('Boom');
    expect(screen.getByText('It failed.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(onRetry).toHaveBeenCalledOnce();
  });
});
