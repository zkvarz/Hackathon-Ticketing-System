// Team management screen tests (HTS-016): list/empty/counts, create (+409), delete enabled
// only for empty teams (+409 defensive), all via MSW.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { TeamsPage } from './TeamsPage';

function team(over: Partial<{ id: string; name: string; epicCount: number; ticketCount: number }>) {
  return {
    id: over.id ?? '1',
    name: over.name ?? 'Payments',
    epicCount: over.epicCount ?? 0,
    ticketCount: over.ticketCount ?? 0,
    createdAt: '2026-06-30T10:00:00Z',
    modifiedAt: '2026-06-30T10:00:00Z',
  };
}

describe('TeamsPage', () => {
  it('renders the list with counts (positive)', async () => {
    server.use(
      http.get('/api/teams', () =>
        HttpResponse.json([team({ id: '1', name: 'Payments', epicCount: 2, ticketCount: 5 })]),
      ),
    );

    renderWithProviders(<TeamsPage />);

    const row = (await screen.findByText('Payments')).closest('tr')!;
    expect(within(row).getByText('2')).toBeInTheDocument();
    expect(within(row).getByText('5')).toBeInTheDocument();
  });

  it('shows the empty state when there are no teams (boundary)', async () => {
    server.use(http.get('/api/teams', () => HttpResponse.json([])));
    renderWithProviders(<TeamsPage />);
    expect(await screen.findByText(/no teams yet/i)).toBeInTheDocument();
  });

  it('creates a team and shows it after refetch (positive)', async () => {
    let created = false;
    server.use(
      http.get('/api/teams', () =>
        HttpResponse.json(created ? [team({ id: '1', name: 'Billing' })] : []),
      ),
      http.post('/api/teams', () => {
        created = true;
        return HttpResponse.json(team({ id: '1', name: 'Billing' }), { status: 201 });
      }),
    );

    renderWithProviders(<TeamsPage />);
    await screen.findByText(/no teams yet/i);

    const u = userEvent.setup();
    await u.type(screen.getByLabelText('New team'), 'Billing');
    await u.click(screen.getByRole('button', { name: /add team/i }));

    expect(await screen.findByText('Billing')).toBeInTheDocument();
  });

  it('shows a message when create hits a duplicate name 409 (negative)', async () => {
    server.use(
      http.get('/api/teams', () => HttpResponse.json([])),
      http.post('/api/teams', () =>
        HttpResponse.json(
          { timestamp: 't', status: 409, error: 'Conflict', code: 'NAME_TAKEN', message: 'x', fieldErrors: [] },
          { status: 409 },
        ),
      ),
    );

    renderWithProviders(<TeamsPage />);
    await screen.findByText(/no teams yet/i);

    const u = userEvent.setup();
    await u.type(screen.getByLabelText('New team'), 'Payments');
    await u.click(screen.getByRole('button', { name: /add team/i }));

    expect(await screen.findByText(/already exists/i)).toBeInTheDocument();
  });

  it('disables delete for a referenced team but enables it for an empty one (boundary)', async () => {
    server.use(
      http.get('/api/teams', () =>
        HttpResponse.json([
          team({ id: '1', name: 'Empty', epicCount: 0, ticketCount: 0 }),
          team({ id: '2', name: 'Busy', epicCount: 1, ticketCount: 0 }),
        ]),
      ),
    );

    renderWithProviders(<TeamsPage />);

    const emptyRow = (await screen.findByText('Empty')).closest('tr')!;
    const busyRow = screen.getByText('Busy').closest('tr')!;

    expect(within(emptyRow).getByRole('button', { name: 'Delete' })).toBeEnabled();
    expect(within(busyRow).getByRole('button', { name: 'Delete' })).toBeDisabled();
  });

  it('deletes an empty team after inline confirm (positive)', async () => {
    let deleted = false;
    server.use(
      http.get('/api/teams', () =>
        HttpResponse.json(deleted ? [] : [team({ id: '1', name: 'Empty' })]),
      ),
      http.delete('/api/teams/1', () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderWithProviders(<TeamsPage />);
    await screen.findByText('Empty');

    const u = userEvent.setup();
    await u.click(screen.getByRole('button', { name: 'Delete' }));
    await u.click(screen.getByRole('button', { name: 'Confirm' }));

    await waitFor(() => {
      expect(screen.getByText(/no teams yet/i)).toBeInTheDocument();
    });
  });
});
