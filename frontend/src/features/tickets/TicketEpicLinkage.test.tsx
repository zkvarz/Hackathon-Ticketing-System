// Team/epic dropdown linkage tests (HTS-022): the epic dropdown lists only the selected team's
// epics, changing the team clears the selected epic and reloads options, submitting with no epic
// is allowed, and a defensive EPIC_TEAM_MISMATCH surfaces as an epic field error.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { TicketDetailsPage } from './TicketDetailsPage';

const teams = [
  { id: 't1', name: 'Payments', epicCount: 1, ticketCount: 0, createdAt: 'x', modifiedAt: 'x' },
  { id: 't2', name: 'Billing', epicCount: 1, ticketCount: 0, createdAt: 'x', modifiedAt: 'x' },
];

function epicsHandler(byTeam: Record<string, { id: string; title: string }[]>) {
  return http.get('/api/epics', ({ request }) => {
    const teamId = new URL(request.url).searchParams.get('teamId') ?? '';
    return HttpResponse.json(
      (byTeam[teamId] ?? []).map((e) => ({
        id: e.id,
        teamId,
        title: e.title,
        description: null,
        ticketCount: 0,
        createdAt: 'x',
        modifiedAt: 'x',
      })),
    );
  });
}

function renderCreate() {
  return renderWithProviders(
    <Routes>
      <Route path="/tickets/new" element={<TicketDetailsPage />} />
      <Route path="/tickets/:id" element={<div>VIEW PAGE</div>} />
    </Routes>,
    { route: '/tickets/new' },
  );
}

describe('Ticket team/epic linkage', () => {
  it("shows only the selected team's epics (positive)", async () => {
    server.use(
      http.get('/api/teams', () => HttpResponse.json(teams)),
      epicsHandler({ t1: [{ id: 'e1', title: 'Checkout' }], t2: [{ id: 'e2', title: 'Invoices' }] }),
    );

    renderCreate();
    await screen.findByRole('option', { name: 'Checkout' }); // epics load async
    const epic = screen.getByLabelText('Epic');
    expect(within(epic).queryByRole('option', { name: 'Invoices' })).not.toBeInTheDocument();
  });

  it('clears the selected epic and reloads options when the team changes (negative)', async () => {
    server.use(
      http.get('/api/teams', () => HttpResponse.json(teams)),
      epicsHandler({ t1: [{ id: 'e1', title: 'Checkout' }], t2: [{ id: 'e2', title: 'Invoices' }] }),
    );

    renderCreate();
    const u = userEvent.setup();
    await screen.findByRole('option', { name: 'Checkout' }); // wait for t1's epics
    const epic = screen.getByLabelText('Epic') as HTMLSelectElement;
    await u.selectOptions(epic, 'e1');
    expect(epic.value).toBe('e1');

    await u.selectOptions(screen.getByLabelText('Team'), 't2');

    // Epic cleared, and the new team's options are loaded.
    expect((screen.getByLabelText('Epic') as HTMLSelectElement).value).toBe('');
    expect(await screen.findByRole('option', { name: 'Invoices' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Checkout' })).not.toBeInTheDocument();
  });

  it('allows submitting with no epic (boundary)', async () => {
    let sent: { epicId?: string | null } | null = null;
    server.use(
      http.get('/api/teams', () => HttpResponse.json(teams)),
      epicsHandler({ t1: [{ id: 'e1', title: 'Checkout' }] }),
      http.post('/api/tickets', async ({ request }) => {
        sent = (await request.json()) as { epicId?: string | null };
        return HttpResponse.json({ id: '5' }, { status: 201 });
      }),
    );

    renderCreate();
    const u = userEvent.setup();
    await u.type(await screen.findByLabelText('Title'), 'No epic ticket');
    await u.type(screen.getByLabelText('Description'), 'Body');
    await u.click(screen.getByRole('button', { name: 'Create ticket' }));

    expect(await screen.findByText('VIEW PAGE')).toBeInTheDocument();
    expect(sent!.epicId).toBeNull();
  });

  it('surfaces a backend EPIC_TEAM_MISMATCH as an epic field error (negative)', async () => {
    server.use(
      http.get('/api/teams', () => HttpResponse.json(teams)),
      epicsHandler({ t1: [{ id: 'e1', title: 'Checkout' }] }),
      http.post('/api/tickets', () =>
        HttpResponse.json(
          {
            timestamp: 'x',
            status: 400,
            error: 'Bad Request',
            code: 'EPIC_TEAM_MISMATCH',
            message: 'The selected epic belongs to a different team than the ticket.',
            fieldErrors: [],
          },
          { status: 400 },
        ),
      ),
    );

    renderCreate();
    const u = userEvent.setup();
    await screen.findByRole('option', { name: 'Checkout' });
    await u.type(screen.getByLabelText('Title'), 'Mismatch');
    await u.type(screen.getByLabelText('Description'), 'Body');
    await u.selectOptions(screen.getByLabelText('Epic'), 'e1');
    await u.click(screen.getByRole('button', { name: 'Create ticket' }));

    // The distinct epic-field message (the top-level banner also mentions "different team").
    expect(await screen.findByText(/pick an epic from this team/i)).toBeInTheDocument();
  });

  it('disables the epic dropdown when the team has no epics (boundary)', async () => {
    server.use(
      http.get('/api/teams', () => HttpResponse.json(teams)),
      epicsHandler({ t1: [] }),
    );

    renderCreate();
    expect(await screen.findByLabelText('Epic')).toBeDisabled();
  });
});
