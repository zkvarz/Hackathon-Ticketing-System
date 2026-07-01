// Board UI tests (HTS-026): five workflow-ordered columns with labels + counts, cards in the
// correct column (modified-desc within a column), team switching refetches, create/open entry
// points navigate, and empty/error states render.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { BoardPage } from './BoardPage';

const teams = [
  { id: 't1', name: 'Payments', epicCount: 0, ticketCount: 4, createdAt: 'x', modifiedAt: 'x' },
  { id: 't2', name: 'Billing', epicCount: 0, ticketCount: 1, createdAt: 'x', modifiedAt: 'x' },
];

function tk(over: Partial<Record<string, unknown>>) {
  return {
    id: 'x',
    teamId: 't1',
    epicId: null,
    epicTitle: null,
    type: 'bug',
    state: 'new',
    title: 'A ticket',
    body: 'B',
    createdBy: 'u1',
    createdByEmail: 'u@example.com',
    createdAt: '2026-06-30T10:00:00Z',
    modifiedAt: '2026-06-30T10:00:00Z',
    ...over,
  };
}

function teamsHandler() {
  return http.get('/api/teams', () => HttpResponse.json(teams));
}

function renderBoard() {
  return renderWithProviders(
    <Routes>
      <Route path="/board" element={<BoardPage />} />
      <Route path="/tickets/new" element={<div>NEW TICKET PAGE</div>} />
      <Route path="/tickets/:id" element={<div>TICKET VIEW</div>} />
    </Routes>,
    { route: '/board' },
  );
}

function column(label: string) {
  return (screen.getByRole('heading', { name: label }).closest('.board__column') as HTMLElement);
}

describe('BoardPage', () => {
  it('renders five workflow-ordered columns and places cards with counts (positive/boundary)', async () => {
    server.use(
      teamsHandler(),
      // Server returns modified-desc; "Newer" precedes "Older" in the New column.
      http.get('/api/tickets', () =>
        HttpResponse.json([
          tk({ id: 'a', state: 'new', title: 'Newer', modifiedAt: '2026-07-02T10:00:00Z' }),
          tk({ id: 'b', state: 'new', title: 'Older', modifiedAt: '2026-07-01T10:00:00Z' }),
          tk({ id: 'c', state: 'in_progress', title: 'Doing', epicTitle: 'Checkout' }),
          tk({ id: 'd', state: 'done', title: 'Done ticket' }),
        ]),
      ),
    );

    renderBoard();
    await screen.findByText('Newer');

    // Column order (FR-B2).
    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent);
    expect(headings).toEqual([
      'New',
      'Ready for implementation',
      'In progress',
      'Ready for acceptance',
      'Done',
    ]);

    // Card placement + intra-column order.
    const newCol = column('New');
    const titles = within(newCol).getAllByRole('button').map((b) => b.textContent);
    expect(titles[0]).toContain('Newer');
    expect(titles[1]).toContain('Older');
    expect(within(newCol).getByLabelText('New count')).toHaveTextContent('2');

    const inProgress = column('In progress');
    expect(within(inProgress).getByText('Doing')).toBeInTheDocument();
    expect(within(inProgress).getByText('Checkout')).toBeInTheDocument();

    expect(within(column('Done')).getByText('Done ticket')).toBeInTheDocument();
  });

  it('switches the board when the team changes (positive)', async () => {
    server.use(
      teamsHandler(),
      http.get('/api/tickets', ({ request }) => {
        const teamId = new URL(request.url).searchParams.get('teamId');
        return HttpResponse.json([
          teamId === 't1'
            ? tk({ id: 'a', title: 'Payments ticket' })
            : tk({ id: 'z', teamId: 't2', title: 'Billing ticket' }),
        ]);
      }),
    );

    renderBoard();
    await screen.findByText('Payments ticket');

    await userEvent.selectOptions(screen.getByLabelText('Team'), 't2');
    expect(await screen.findByText('Billing ticket')).toBeInTheDocument();
    expect(screen.queryByText('Payments ticket')).not.toBeInTheDocument();
  });

  it('renders five empty columns with a hint for a team with no tickets (boundary)', async () => {
    server.use(teamsHandler(), http.get('/api/tickets', () => HttpResponse.json([])));

    renderBoard();
    // All five columns present, each showing the empty-column hint.
    expect(await screen.findByRole('heading', { name: 'New' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(5);
    expect(screen.getAllByText('No tickets')).toHaveLength(5);
  });

  it('shows an error state when the board fails to load (negative)', async () => {
    server.use(
      teamsHandler(),
      http.get('/api/tickets', () => new HttpResponse(null, { status: 500 })),
    );

    renderBoard();
    expect(await screen.findByText(/could not load the board/i)).toBeInTheDocument();
  });

  it('navigates to create and to a ticket (positive)', async () => {
    server.use(
      teamsHandler(),
      http.get('/api/tickets', () => HttpResponse.json([tk({ id: 'a', title: 'Open me' })])),
    );

    renderBoard();
    const u = userEvent.setup();
    await screen.findByText('Open me');

    await u.click(screen.getByRole('button', { name: /open me/i }));
    expect(await screen.findByText('TICKET VIEW')).toBeInTheDocument();
  });

  it('starts ticket creation from the New-ticket button (positive)', async () => {
    server.use(teamsHandler(), http.get('/api/tickets', () => HttpResponse.json([])));

    renderBoard();
    const u = userEvent.setup();
    await screen.findByRole('heading', { name: 'New' });
    await u.click(screen.getByRole('button', { name: 'New ticket' }));
    expect(await screen.findByText('NEW TICKET PAGE')).toBeInTheDocument();
  });
});
