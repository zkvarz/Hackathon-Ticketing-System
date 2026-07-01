// Board filter/search tests (HTS-030): each control drives the server-side query params (AND),
// the result count updates, no matches shows an empty state (not an error), Clear restores the
// full board, rapid typing debounces to a single query, and switching team resets the epic filter.

import { describe, expect, it, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { BoardPage } from './BoardPage';

const teams = [
  { id: 't1', name: 'Payments', epicCount: 1, ticketCount: 3, createdAt: 'x', modifiedAt: 'x' },
  { id: 't2', name: 'Billing', epicCount: 0, ticketCount: 1, createdAt: 'x', modifiedAt: 'x' },
];

const epics = [
  { id: 'e1', teamId: 't1', title: 'Checkout', description: null, ticketCount: 1, createdAt: 'x', modifiedAt: 'x' },
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

const ALL = [
  tk({ id: 'a', type: 'bug', epicId: 'e1', epicTitle: 'Checkout', title: 'Payment bug', state: 'new' }),
  tk({ id: 'b', type: 'feature', title: 'Add report', state: 'in_progress' }),
  tk({ id: 'c', type: 'bug', title: 'Login crash', state: 'done' }),
  tk({ id: 'z', teamId: 't2', type: 'fix', title: 'Billing tweak', state: 'new' }),
];

// Records every /api/tickets query string and returns server-side-filtered data (teamId + the
// AND of type/epicId/q), so tests can both assert on the emitted params and on the rendered count.
let queries: string[] = [];

function ticketsHandler() {
  return http.get('/api/tickets', ({ request }) => {
    const url = new URL(request.url);
    queries.push(url.search);
    const teamId = url.searchParams.get('teamId');
    const type = url.searchParams.get('type');
    const epicId = url.searchParams.get('epicId');
    const q = url.searchParams.get('q');
    let data = ALL.filter((t) => t.teamId === teamId);
    if (type) data = data.filter((t) => t.type === type);
    if (epicId) data = data.filter((t) => t.epicId === epicId);
    if (q) data = data.filter((t) => (t.title as string).toLowerCase().includes(q.toLowerCase()));
    return HttpResponse.json(data);
  });
}

function baseHandlers() {
  server.use(
    http.get('/api/teams', () => HttpResponse.json(teams)),
    http.get('/api/epics', ({ request }) => {
      const teamId = new URL(request.url).searchParams.get('teamId');
      return HttpResponse.json(teamId === 't1' ? epics : []);
    }),
    ticketsHandler(),
  );
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

function count() {
  return screen.getByLabelText('Result count').textContent;
}

describe('Board filters (HTS-030)', () => {
  beforeEach(() => {
    queries = [];
  });

  // Positive: type + epic filters issue the right params (AND) and the count reflects the result.
  it('applies type and epic filters with the expected query params (positive)', async () => {
    baseHandlers();
    const u = userEvent.setup();
    renderBoard();
    await screen.findByText('Payment bug');
    expect(count()).toContain('3');

    await u.selectOptions(screen.getByLabelText('Filter by type'), 'bug');
    await waitFor(() => expect(count()).toContain('2')); // Payment bug + Login crash
    expect(queries.some((s) => s.includes('type=bug'))).toBe(true);

    await u.selectOptions(await screen.findByLabelText('Filter by epic'), 'e1');
    await waitFor(() => expect(count()).toContain('1')); // only Payment bug (bug AND epic e1)
    expect(queries.some((s) => s.includes('type=bug') && s.includes('epicId=e1'))).toBe(true);
  });

  // Positive/boundary: typing in search narrows the board and the query carries q.
  it('searches by title and narrows the board (positive)', async () => {
    baseHandlers();
    const u = userEvent.setup();
    renderBoard();
    await screen.findByText('Payment bug');

    await u.type(screen.getByLabelText('Search tickets'), 'login');
    expect(await screen.findByText('Login crash')).toBeInTheDocument();
    await waitFor(() => expect(count()).toContain('1'));
    expect(queries.some((s) => s.includes('q=login'))).toBe(true);
  });

  // Boundary: rapid typing debounces to a single query — intermediate keystrokes don't each fire.
  it('debounces search to a single query (boundary)', async () => {
    baseHandlers();
    const u = userEvent.setup();
    renderBoard();
    await screen.findByText('Payment bug');

    await u.type(screen.getByLabelText('Search tickets'), 'pay');
    await waitFor(() => expect(queries.some((s) => s.includes('q=pay'))).toBe(true));

    // No query fired for the intermediate substrings.
    expect(queries.some((s) => /q=p(&|$)/.test(s))).toBe(false);
    expect(queries.some((s) => s.includes('q=pa&') || /q=pa$/.test(s))).toBe(false);
  });

  // Negative: a filter with no matches shows an empty state, not an error.
  it('shows an empty state when nothing matches (negative)', async () => {
    baseHandlers();
    const u = userEvent.setup();
    renderBoard();
    await screen.findByText('Payment bug');

    await u.type(screen.getByLabelText('Search tickets'), 'zzzz-nope');
    expect(await screen.findByText('No matching tickets')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(count()).toContain('0');
  });

  // Boundary: Clear resets all filters and restores the full board.
  it('clears all filters and restores the board (boundary)', async () => {
    baseHandlers();
    const u = userEvent.setup();
    renderBoard();
    await screen.findByText('Payment bug');

    await u.selectOptions(screen.getByLabelText('Filter by type'), 'bug');
    await waitFor(() => expect(count()).toContain('2'));

    await u.click(screen.getByRole('button', { name: 'Clear' }));
    await waitFor(() => expect(count()).toContain('3'));
    expect(screen.getByLabelText('Filter by type')).toHaveValue('');
  });

  // Boundary: switching team resets the epic filter (a cross-team epic must not linger).
  it('resets the epic filter when the team changes (boundary)', async () => {
    baseHandlers();
    const u = userEvent.setup();
    renderBoard();
    await screen.findByText('Payment bug');

    await u.selectOptions(await screen.findByLabelText('Filter by epic'), 'e1');
    await waitFor(() => expect(count()).toContain('1'));

    await u.selectOptions(screen.getByLabelText('Team'), 't2');
    // The board reloads for t2 with no epic filter carried over.
    expect(await screen.findByText('Billing tweak')).toBeInTheDocument();
    expect(screen.getByLabelText('Filter by epic')).toHaveValue('');
    const t2Queries = queries.filter((s) => s.includes('teamId=t2'));
    expect(t2Queries.length).toBeGreaterThan(0);
    expect(t2Queries.every((s) => !s.includes('epicId='))).toBe(true);
  });
});
