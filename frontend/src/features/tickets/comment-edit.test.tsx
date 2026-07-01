// Comment edit/delete tests (HTS-040): author-only controls, inline edit with an "edited"
// indicator, delete-with-confirm, cancel-restores, and graceful 403 handling. MSW drives the
// nested PUT/DELETE comment endpoints. currentUserId is passed directly (the panel takes it as a
// prop), so no AuthProvider is needed.

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { CommentsPanel } from './CommentsPanel';

function comment(
  over: Partial<{
    id: string;
    authorId: string;
    authorEmail: string;
    body: string;
    createdAt: string;
    editedAt: string | null;
  }>,
) {
  return {
    id: over.id ?? 'c1',
    ticketId: '1',
    authorId: over.authorId ?? 'u1',
    authorEmail: over.authorEmail ?? 'me@example.com',
    body: over.body ?? 'A comment',
    createdAt: over.createdAt ?? '2026-06-30T10:00:00Z',
    editedAt: over.editedAt ?? null,
  };
}

/** The <li> containing the given text. */
function itemWith(text: string) {
  return screen.getByText(text).closest('li') as HTMLElement;
}

describe('CommentsPanel edit/delete (HTS-040)', () => {
  it('shows edit/delete controls only on the current user\'s own comments (AC-1)', async () => {
    server.use(
      http.get('/api/tickets/1/comments', () =>
        HttpResponse.json([
          comment({ id: 'c1', authorId: 'u1', body: 'Mine' }),
          comment({ id: 'c2', authorId: 'u2', authorEmail: 'other@example.com', body: 'Theirs' }),
        ]),
      ),
    );

    renderWithProviders(<CommentsPanel ticketId="1" currentUserId="u1" />);
    await screen.findByText('Mine');

    expect(within(itemWith('Mine')).getByRole('button', { name: 'Edit' })).toBeInTheDocument();
    expect(within(itemWith('Mine')).getByRole('button', { name: 'Delete' })).toBeInTheDocument();
    expect(within(itemWith('Theirs')).queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    expect(within(itemWith('Theirs')).queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
  });

  it('edits a comment and shows the "edited" indicator (AC-2, positive)', async () => {
    let edited = false;
    const bodies: string[] = [];
    server.use(
      http.get('/api/tickets/1/comments', () =>
        HttpResponse.json([
          edited
            ? comment({ id: 'c1', body: 'Updated', editedAt: '2026-06-30T12:00:00Z' })
            : comment({ id: 'c1', body: 'Original' }),
        ]),
      ),
      http.put('/api/tickets/1/comments/c1', async ({ request }) => {
        bodies.push(((await request.json()) as { body: string }).body);
        edited = true;
        return HttpResponse.json(comment({ id: 'c1', body: 'Updated', editedAt: '2026-06-30T12:00:00Z' }));
      }),
    );

    renderWithProviders(<CommentsPanel ticketId="1" currentUserId="u1" />);
    await screen.findByText('Original');

    const u = userEvent.setup();
    await u.click(within(itemWith('Original')).getByRole('button', { name: 'Edit' }));
    const box = screen.getByRole('textbox', { name: 'Edit comment' });
    await u.clear(box);
    await u.type(box, 'Updated');
    await u.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Updated')).toBeInTheDocument();
    expect(screen.getByText(/\(edited\)/)).toBeInTheDocument();
    expect(bodies).toEqual(['Updated']);
  });

  it('blocks saving a blank edit (negative)', async () => {
    server.use(
      http.get('/api/tickets/1/comments', () =>
        HttpResponse.json([comment({ id: 'c1', body: 'Original' })]),
      ),
    );

    renderWithProviders(<CommentsPanel ticketId="1" currentUserId="u1" />);
    await screen.findByText('Original');

    const u = userEvent.setup();
    await u.click(within(itemWith('Original')).getByRole('button', { name: 'Edit' }));
    const box = screen.getByRole('textbox', { name: 'Edit comment' });
    await u.clear(box);

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  it('cancelling an edit restores the original body (boundary)', async () => {
    server.use(
      http.get('/api/tickets/1/comments', () =>
        HttpResponse.json([comment({ id: 'c1', body: 'Original' })]),
      ),
    );

    renderWithProviders(<CommentsPanel ticketId="1" currentUserId="u1" />);
    await screen.findByText('Original');

    const u = userEvent.setup();
    await u.click(within(itemWith('Original')).getByRole('button', { name: 'Edit' }));
    const box = screen.getByRole('textbox', { name: 'Edit comment' });
    await u.clear(box);
    await u.type(box, 'Discarded draft');
    await u.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.getByText('Original')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Edit comment' })).not.toBeInTheDocument();
  });

  it('confirms then deletes a comment (AC-3)', async () => {
    let deleted = false;
    server.use(
      http.get('/api/tickets/1/comments', () =>
        HttpResponse.json(deleted ? [] : [comment({ id: 'c1', body: 'Delete me' })]),
      ),
      http.delete('/api/tickets/1/comments/c1', () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderWithProviders(<CommentsPanel ticketId="1" currentUserId="u1" />);
    await screen.findByText('Delete me');

    const u = userEvent.setup();
    await u.click(within(itemWith('Delete me')).getByRole('button', { name: 'Delete' }));
    // Confirmation appears; confirm it.
    await u.click(screen.getByRole('button', { name: 'Confirm delete' }));

    await waitFor(() => expect(screen.queryByText('Delete me')).not.toBeInTheDocument());
    expect(screen.getByText(/no comments yet/i)).toBeInTheDocument();
  });

  it('handles a 403 on edit gracefully (AC-4)', async () => {
    server.use(
      http.get('/api/tickets/1/comments', () =>
        HttpResponse.json([comment({ id: 'c1', body: 'Original' })]),
      ),
      http.put('/api/tickets/1/comments/c1', () =>
        HttpResponse.json(
          {
            timestamp: 'x',
            status: 403,
            error: 'Forbidden',
            code: 'FORBIDDEN',
            message: 'You can only modify your own comments.',
            fieldErrors: [],
          },
          { status: 403 },
        ),
      ),
    );

    renderWithProviders(<CommentsPanel ticketId="1" currentUserId="u1" />);
    await screen.findByText('Original');

    const u = userEvent.setup();
    await u.click(within(itemWith('Original')).getByRole('button', { name: 'Edit' }));
    const box = screen.getByRole('textbox', { name: 'Edit comment' });
    await u.clear(box);
    await u.type(box, 'Attempted edit');
    await u.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(/only modify your own comments/i)).toBeInTheDocument();
  });
});
