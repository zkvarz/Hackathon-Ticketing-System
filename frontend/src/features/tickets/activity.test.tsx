// Ticket activity history tests (HTS-042): renders entries chronologically with actor/field/
// old→new/time (state & type shown as human-readable labels), an empty state when there is no
// activity, and an error state on failure. MSW drives the activity endpoint.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, within } from '@testing-library/react';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { ActivityPanel } from './ActivityPanel';

function entry(
  over: Partial<{
    id: string;
    actorEmail: string;
    field: string;
    oldValue: string | null;
    newValue: string | null;
    at: string;
  }>,
) {
  return {
    id: over.id ?? 'a1',
    ticketId: '1',
    actorEmail: over.actorEmail ?? 'me@example.com',
    field: over.field ?? 'created',
    oldValue: over.oldValue ?? null,
    newValue: over.newValue ?? null,
    at: over.at ?? '2026-06-30T10:00:00Z',
  };
}

describe('ActivityPanel (HTS-042)', () => {
  it('renders entries chronologically with actor, change, and labels (AC-1, positive)', async () => {
    server.use(
      http.get('/api/tickets/1/activity', () =>
        HttpResponse.json([
          entry({ id: 'a1', actorEmail: 'alice@example.com', field: 'created' }),
          entry({
            id: 'a2',
            actorEmail: 'bob@example.com',
            field: 'state',
            oldValue: 'new',
            newValue: 'in_progress',
          }),
          entry({
            id: 'a3',
            actorEmail: 'bob@example.com',
            field: 'title',
            oldValue: 'Old title',
            newValue: 'New title',
          }),
        ]),
      ),
    );

    renderWithProviders(<ActivityPanel ticketId="1" />);
    await screen.findByText(/created the ticket/i);

    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(3);
    // Order preserved as returned by the API (backend sorts oldest-first).
    expect(within(items[0]).getByText('alice@example.com')).toBeInTheDocument();
    expect(items[0]).toHaveTextContent(/created the ticket/i);
    // state uses human-readable labels, not the wire values.
    expect(items[1]).toHaveTextContent(/changed the state from “New” to “In progress”/i);
    expect(items[1]).not.toHaveTextContent('in_progress');
    // free-text fields show the raw old→new strings.
    expect(items[2]).toHaveTextContent(/changed the title from “Old title” to “New title”/i);
  });

  it('shows an empty state when there is no activity (AC-2, boundary)', async () => {
    server.use(http.get('/api/tickets/1/activity', () => HttpResponse.json([])));

    renderWithProviders(<ActivityPanel ticketId="1" />);

    expect(await screen.findByText(/no activity yet/i)).toBeInTheDocument();
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
  });

  it('shows an error state when the request fails (AC-3, negative)', async () => {
    server.use(
      http.get('/api/tickets/1/activity', () => new HttpResponse(null, { status: 500 })),
    );

    renderWithProviders(<ActivityPanel ticketId="1" />);

    expect(await screen.findByText(/could not load activity/i)).toBeInTheDocument();
  });
});
