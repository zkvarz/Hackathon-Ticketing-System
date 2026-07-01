// Ticket view tests (HTS-020): details/edit renders all fields + metadata, save sends the right
// payload, client + server validation surface inline, state labels map to canonical values, and
// delete confirms then navigates. Rendered through Routes so useParams resolves (see the
// frontend-test-router-patterns note).

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { TicketDetailsPage } from './TicketDetailsPage';

const teams = [
  { id: 't1', name: 'Payments', epicCount: 1, ticketCount: 1, createdAt: 'x', modifiedAt: 'x' },
  { id: 't2', name: 'Billing', epicCount: 0, ticketCount: 0, createdAt: 'x', modifiedAt: 'x' },
];

const ticket = {
  id: '1',
  teamId: 't1',
  epicId: 'e1',
  epicTitle: 'Checkout',
  type: 'bug',
  state: 'in_progress',
  title: 'Broken login',
  body: 'Steps to reproduce',
  createdBy: 'u1',
  createdByEmail: 'u@example.com',
  createdAt: '2026-06-30T10:00:00Z',
  modifiedAt: '2026-07-01T09:00:00Z',
};

function baseHandlers() {
  return [
    http.get('/api/teams', () => HttpResponse.json(teams)),
    http.get('/api/epics', ({ request }) => {
      const teamId = new URL(request.url).searchParams.get('teamId');
      return HttpResponse.json(
        teamId === 't1' ? [{ id: 'e1', teamId: 't1', title: 'Checkout', description: null, ticketCount: 1, createdAt: 'x', modifiedAt: 'x' }] : [],
      );
    }),
    // The edit view embeds the comments panel (HTS-024), which lists comments on mount.
    http.get('/api/tickets/1/comments', () => HttpResponse.json([])),
  ];
}

function renderEdit() {
  return renderWithProviders(
    <Routes>
      <Route path="/tickets/:id" element={<TicketDetailsPage />} />
      <Route path="/board" element={<div>BOARD PAGE</div>} />
    </Routes>,
    { route: '/tickets/1' },
  );
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

describe('TicketDetailsPage', () => {
  it('renders all fields including created-by/at and modified-at (positive)', async () => {
    server.use(...baseHandlers(), http.get('/api/tickets/1', () => HttpResponse.json(ticket)));

    renderEdit();

    expect(await screen.findByDisplayValue('Broken login')).toBeInTheDocument();
    expect(screen.getByLabelText('Description')).toHaveValue('Steps to reproduce');
    expect(screen.getByLabelText('State')).toHaveValue('in_progress');
    expect(screen.getByLabelText('Type')).toHaveValue('bug');
    expect(screen.getByText(/created by u@example.com/i)).toBeInTheDocument();
    expect(screen.getByText(/last modified/i)).toBeInTheDocument();
  });

  it('saves an edit with the correct payload (positive)', async () => {
    let sent: unknown = null;
    server.use(
      ...baseHandlers(),
      http.get('/api/tickets/1', () => HttpResponse.json(ticket)),
      http.put('/api/tickets/1', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ ...ticket, title: 'Broken login v2' });
      }),
    );

    renderEdit();
    const u = userEvent.setup();
    const title = await screen.findByDisplayValue('Broken login');
    await u.clear(title);
    await u.type(title, 'Broken login v2');
    await u.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Saved.')).toBeInTheDocument();
    expect(sent).toMatchObject({
      teamId: 't1',
      epicId: 'e1',
      type: 'bug',
      state: 'in_progress',
      title: 'Broken login v2',
      body: 'Steps to reproduce',
    });
  });

  it('blocks save when the title is blank (negative, client-side)', async () => {
    let put = false;
    server.use(
      ...baseHandlers(),
      http.get('/api/tickets/1', () => HttpResponse.json(ticket)),
      http.put('/api/tickets/1', () => {
        put = true;
        return HttpResponse.json(ticket);
      }),
    );

    renderEdit();
    const u = userEvent.setup();
    const title = await screen.findByDisplayValue('Broken login');
    await u.clear(title);
    await u.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(/title is required/i)).toBeInTheDocument();
    expect(put).toBe(false);
  });

  it('surfaces a server 400 field error inline (negative)', async () => {
    server.use(
      ...baseHandlers(),
      http.get('/api/tickets/1', () => HttpResponse.json(ticket)),
      http.put('/api/tickets/1', () =>
        HttpResponse.json(
          {
            timestamp: 'x',
            status: 400,
            error: 'Bad Request',
            code: 'VALIDATION_FAILED',
            message: 'Request validation failed.',
            fieldErrors: [{ field: 'title', message: 'Title must be at most 200 characters.' }],
          },
          { status: 400 },
        ),
      ),
    );

    renderEdit();
    const u = userEvent.setup();
    await screen.findByDisplayValue('Broken login');
    await u.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(/at most 200 characters/i)).toBeInTheDocument();
  });

  it('shows human-readable state labels mapped to canonical values (boundary)', async () => {
    server.use(...baseHandlers());

    renderCreate();
    const state = (await screen.findByLabelText('State')) as HTMLSelectElement;
    const options = within(state).getAllByRole('option') as HTMLOptionElement[];

    expect(options.map((o) => o.value)).toEqual([
      'new',
      'ready_for_implementation',
      'in_progress',
      'ready_for_acceptance',
      'done',
    ]);
    expect(options.map((o) => o.textContent)).toEqual([
      'New',
      'Ready for implementation',
      'In progress',
      'Ready for acceptance',
      'Done',
    ]);
  });

  it('deletes after confirmation then navigates to the board (positive)', async () => {
    let deleted = 0;
    server.use(
      ...baseHandlers(),
      http.get('/api/tickets/1', () => HttpResponse.json(ticket)),
      http.delete('/api/tickets/1', () => {
        deleted += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderEdit();
    const u = userEvent.setup();
    await screen.findByDisplayValue('Broken login');
    await u.click(screen.getByRole('button', { name: 'Delete' }));
    await u.click(screen.getByRole('button', { name: 'Confirm delete' }));

    expect(await screen.findByText('BOARD PAGE')).toBeInTheDocument();
    expect(deleted).toBe(1);
  });

  it('creates a ticket and navigates to it (positive)', async () => {
    let sent: unknown = null;
    server.use(
      ...baseHandlers(),
      http.post('/api/tickets', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ ...ticket, id: '99' }, { status: 201 });
      }),
    );

    renderCreate();
    const u = userEvent.setup();
    // Team defaults to the first team; fill required text fields.
    await u.type(await screen.findByLabelText('Title'), 'New ticket');
    await u.type(screen.getByLabelText('Description'), 'Body text');
    await u.click(screen.getByRole('button', { name: 'Create ticket' }));

    expect(await screen.findByText('VIEW PAGE')).toBeInTheDocument();
    expect(sent).toMatchObject({
      teamId: 't1',
      type: 'bug',
      state: 'new',
      title: 'New ticket',
      body: 'Body text',
    });
  });
});
