// Comments panel for the ticket view (FR-C1/C3/C4, FR-S6; HTS-024, wireframe image3). Lists a
// ticket's comments oldest-first (author email + time) and posts new non-empty comments. Posting
// a comment does not reorder the board (FR-C5) — only the comment list is refetched, never the
// ticket/board queries.

import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { addComment, listComments } from '../../api/comments';
import { ApiError } from '../../api/client';
import { Loading } from '../../components/Loading';
import { Empty } from '../../components/Empty';
import { ErrorState } from '../../components/ErrorState';

export function CommentsPanel({ ticketId }: { ticketId: string }) {
  const queryClient = useQueryClient();
  const commentsQuery = useQuery({
    queryKey: ['comments', ticketId],
    queryFn: () => listComments(ticketId),
  });

  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);

  const addMutation = useMutation({
    mutationFn: () => addComment(ticketId, body.trim()),
    onSuccess: () => {
      setBody('');
      setError(null);
      // Only the comment list — not the ticket/board — refreshes (FR-C5).
      void queryClient.invalidateQueries({ queryKey: ['comments', ticketId] });
    },
    onError: (err) =>
      setError(err instanceof ApiError ? err.message : 'Could not post the comment.'),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!body.trim()) {
      setError('Comment cannot be empty.');
      return;
    }
    setError(null);
    addMutation.mutate();
  }

  const comments = commentsQuery.data ?? [];

  return (
    <section className="comments">
      <h2>Comments</h2>

      {commentsQuery.isLoading && <Loading label="Loading comments…" />}
      {commentsQuery.isError && (
        <ErrorState
          message="Could not load comments."
          onRetry={() => void commentsQuery.refetch()}
        />
      )}
      {commentsQuery.data && comments.length === 0 && (
        <Empty title="No comments yet" message="Be the first to comment on this ticket." />
      )}

      {comments.length > 0 && (
        <ul className="comments__list">
          {comments.map((c) => (
            <li key={c.id} className="comment">
              <p className="comment__meta">
                <span className="comment__author">{c.authorEmail}</span>{' '}
                <time dateTime={c.createdAt}>{new Date(c.createdAt).toLocaleString()}</time>
              </p>
              <p className="comment__body">{c.body}</p>
            </li>
          ))}
        </ul>
      )}

      <form className="comments__form" onSubmit={submit}>
        <label htmlFor="new-comment">Add a comment</label>
        <textarea
          id="new-comment"
          value={body}
          onChange={(e) => setBody(e.target.value)}
          maxLength={10000}
        />
        <button type="submit" disabled={addMutation.isPending || !body.trim()}>
          Post comment
        </button>
        {error && <p className="form__error" role="alert">{error}</p>}
      </form>
    </section>
  );
}
