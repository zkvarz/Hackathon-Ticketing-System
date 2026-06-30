// Epic management screen tests (HTS-018): team-scoped list/counts, create (+blank blocked),
// delete enabled only for unreferenced epics, and team switching refetches — all via MSW.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { EpicsPage } from './EpicsPage';

const teams = [
  { id: 't1', name: 'Payments', epicCount: 1, ticketCount: 0, createdAt: 'x', modifiedAt: 'x' },
  { id: 't2', name: 'Billing', epicCount: 0, ticketCount: 0, createdAt: 'x', modifiedAt: 'x' },
];

function epic(over: Partial<{ id: string; teamId: string; title: string; ticketCount: number }>) {
  return {
    id: over.id ?? 'e1',
    teamId: over.teamId ?? 't1',
    title: over.title ?? 'Checkout',
    description: null,
    ticketCount: over.ticketCount ?? 0,
    createdAt: '2026-06-30T10:00:00Z',
    modifiedAt: '2026-06-30T10:00:00Z',
  };
}

function teamsHandler() {
  return http.get('/api/teams', () => HttpResponse.json(teams));
}

describe('EpicsPage', () => {
  it('lists the selected team\'s epics with counts (positive)', async () => {
    server.use(
      teamsHandler(),
      http.get('/api/epics', ({ request }) => {
        const teamId = new URL(request.url).searchParams.get('teamId');
        return HttpResponse.json(teamId === 't1' ? [epic({ title: 'Checkout', ticketCount: 3 })] : []);
      }),
    );

    renderWithProviders(<EpicsPage />);

    const row = (await screen.findByText('Checkout')).closest('tr')!;
    expect(within(row).getByText('3')).toBeInTheDocument();
  });

  it('shows the empty state when the team has no epics (boundary)', async () => {
    server.use(
      teamsHandler(),
      http.get('/api/epics', () => HttpResponse.json([])),
    );

    renderWithProviders(<EpicsPage />);
    expect(await screen.findByText(/no epics yet/i)).toBeInTheDocument();
  });

  it('blocks create when the title is blank (negative)', async () => {
    let posted = false;
    server.use(
      teamsHandler(),
      http.get('/api/epics', () => HttpResponse.json([])),
      http.post('/api/epics', () => {
        posted = true;
        return HttpResponse.json(epic({}), { status: 201 });
      }),
    );

    renderWithProviders(<EpicsPage />);
    await screen.findByText(/no epics yet/i);

    await userEvent.click(screen.getByRole('button', { name: /add epic/i }));

    expect(await screen.findByText(/title is required/i)).toBeInTheDocument();
    expect(posted).toBe(false);
  });

  it('creates an epic for the selected team (positive)', async () => {
    let created = false;
    server.use(
      teamsHandler(),
      http.get('/api/epics', () => HttpResponse.json(created ? [epic({ title: 'Onboarding' })] : [])),
      http.post('/api/epics', () => {
        created = true;
        return HttpResponse.json(epic({ title: 'Onboarding' }), { status: 201 });
      }),
    );

    renderWithProviders(<EpicsPage />);
    await screen.findByText(/no epics yet/i);

    const u = userEvent.setup();
    await u.type(screen.getByLabelText('New epic'), 'Onboarding');
    await u.click(screen.getByRole('button', { name: /add epic/i }));

    expect(await screen.findByText('Onboarding')).toBeInTheDocument();
  });

  it('disables delete for a referenced epic, enables it otherwise (boundary)', async () => {
    server.use(
      teamsHandler(),
      http.get('/api/epics', () =>
        HttpResponse.json([
          epic({ id: 'e1', title: 'Free', ticketCount: 0 }),
          epic({ id: 'e2', title: 'Busy', ticketCount: 2 }),
        ]),
      ),
    );

    renderWithProviders(<EpicsPage />);

    const freeRow = (await screen.findByText('Free')).closest('tr')!;
    const busyRow = screen.getByText('Busy').closest('tr')!;
    expect(within(freeRow).getByRole('button', { name: 'Delete' })).toBeEnabled();
    expect(within(busyRow).getByRole('button', { name: 'Delete' })).toBeDisabled();
  });

  it('refetches the list when the team is switched (boundary)', async () => {
    server.use(
      teamsHandler(),
      http.get('/api/epics', ({ request }) => {
        const teamId = new URL(request.url).searchParams.get('teamId');
        return HttpResponse.json(
          teamId === 't1' ? [epic({ title: 'Checkout' })] : [epic({ title: 'Invoices', teamId: 't2' })],
        );
      }),
    );

    renderWithProviders(<EpicsPage />);
    await screen.findByText('Checkout');

    await userEvent.selectOptions(screen.getByLabelText('Team'), 't2');

    expect(await screen.findByText('Invoices')).toBeInTheDocument();
  });
});
