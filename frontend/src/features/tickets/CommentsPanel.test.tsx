// Comments panel tests (HTS-024): oldest-first list with author + time, posting adds a comment,
// empty body blocked client-side, server 400 surfaced, and the empty state.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { CommentsPanel } from './CommentsPanel';

function comment(over: Partial<{ id: string; body: string; createdAt: string }>) {
  return {
    id: over.id ?? 'c1',
    ticketId: '1',
    authorId: 'u1',
    authorEmail: 'u@example.com',
    body: over.body ?? 'A comment',
    createdAt: over.createdAt ?? '2026-06-30T10:00:00Z',
  };
}

describe('CommentsPanel', () => {
  it('renders comments oldest-first with author and time (positive)', async () => {
    server.use(
      http.get('/api/tickets/1/comments', () =>
        HttpResponse.json([
          comment({ id: 'c1', body: 'First', createdAt: '2026-06-30T10:00:00Z' }),
          comment({ id: 'c2', body: 'Second', createdAt: '2026-06-30T11:00:00Z' }),
        ]),
      ),
    );

    renderWithProviders(<CommentsPanel ticketId="1" />);

    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('First');
    expect(items[1]).toHaveTextContent('Second');
    expect(screen.getAllByText('u@example.com').length).toBeGreaterThan(0);
  });

  it('shows the empty state when there are no comments (boundary)', async () => {
    server.use(http.get('/api/tickets/1/comments', () => HttpResponse.json([])));

    renderWithProviders(<CommentsPanel ticketId="1" />);
    expect(await screen.findByText(/no comments yet/i)).toBeInTheDocument();
  });

  it('posts a non-empty comment and shows it (positive)', async () => {
    let posted = false;
    server.use(
      http.get('/api/tickets/1/comments', () =>
        HttpResponse.json(posted ? [comment({ id: 'c1', body: 'Reproduced it' })] : []),
      ),
      http.post('/api/tickets/1/comments', async ({ request }) => {
        const b = (await request.json()) as { body: string };
        posted = true;
        return HttpResponse.json(comment({ id: 'c1', body: b.body }), { status: 201 });
      }),
    );

    renderWithProviders(<CommentsPanel ticketId="1" />);
    await screen.findByText(/no comments yet/i);

    const u = userEvent.setup();
    await u.type(screen.getByLabelText('Add a comment'), 'Reproduced it');
    await u.click(screen.getByRole('button', { name: 'Post comment' }));

    expect(await screen.findByText('Reproduced it')).toBeInTheDocument();
  });

  it('disables posting an empty/whitespace comment (negative, client-side)', async () => {
    server.use(http.get('/api/tickets/1/comments', () => HttpResponse.json([])));

    renderWithProviders(<CommentsPanel ticketId="1" />);
    await screen.findByText(/no comments yet/i);

    const post = screen.getByRole('button', { name: 'Post comment' });
    expect(post).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Add a comment'), '   ');
    expect(post).toBeDisabled();
  });

  it('surfaces a server 400 when posting fails (negative)', async () => {
    server.use(
      http.get('/api/tickets/1/comments', () => HttpResponse.json([])),
      http.post('/api/tickets/1/comments', () =>
        HttpResponse.json(
          {
            timestamp: 'x',
            status: 400,
            error: 'Bad Request',
            code: 'VALIDATION_FAILED',
            message: 'Comment body is required.',
            fieldErrors: [{ field: 'body', message: 'Comment body is required.' }],
          },
          { status: 400 },
        ),
      ),
    );

    renderWithProviders(<CommentsPanel ticketId="1" />);
    await screen.findByText(/no comments yet/i);

    const u = userEvent.setup();
    await u.type(screen.getByLabelText('Add a comment'), 'x');
    await u.click(screen.getByRole('button', { name: 'Post comment' }));

    expect(await screen.findByText(/comment body is required/i)).toBeInTheDocument();
  });
});
