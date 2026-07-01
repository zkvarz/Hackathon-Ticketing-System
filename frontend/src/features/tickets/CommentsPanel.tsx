// Comments panel for the ticket view (FR-C1/C3/C4, FR-S6; HTS-024 + HTS-040 stretch). Lists a
// ticket's comments oldest-first (author email + time) and posts new non-empty comments. Posting,
// editing, or deleting a comment does not reorder the board (FR-C5) — only the comment list is
// refetched, never the ticket/board queries. Edit/delete controls (HTS-040) appear only on the
// current user's own comments; edited comments show an "edited" indicator.

import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addComment,
  deleteComment,
  editComment,
  listComments,
  type Comment,
} from '../../api/comments';
import { ApiError } from '../../api/client';
import { Loading } from '../../components/Loading';
import { Empty } from '../../components/Empty';
import { ErrorState } from '../../components/ErrorState';

export function CommentsPanel({
  ticketId,
  currentUserId,
}: {
  ticketId: string;
  currentUserId?: string;
}) {
  const queryClient = useQueryClient();
  const commentsQuery = useQuery({
    queryKey: ['comments', ticketId],
    queryFn: () => listComments(ticketId),
  });

  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);

  // Per-comment UI state: which comment is being edited (and its draft) or confirmed for delete.
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editBody, setEditBody] = useState('');
  const [confirmingDeleteId, setConfirmingDeleteId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const invalidate = () =>
    // Only the comment list — not the ticket/board — refreshes (FR-C5).
    void queryClient.invalidateQueries({ queryKey: ['comments', ticketId] });

  const addMutation = useMutation({
    mutationFn: () => addComment(ticketId, body.trim()),
    onSuccess: () => {
      setBody('');
      setError(null);
      invalidate();
    },
    onError: (err) =>
      setError(err instanceof ApiError ? err.message : 'Could not post the comment.'),
  });

  const editMutation = useMutation({
    mutationFn: (vars: { id: string; body: string }) =>
      editComment(ticketId, vars.id, vars.body.trim()),
    onSuccess: () => {
      setEditingId(null);
      setEditBody('');
      setActionError(null);
      invalidate();
    },
    onError: (err) =>
      setActionError(err instanceof ApiError ? err.message : 'Could not save the edit.'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteComment(ticketId, id),
    onSuccess: () => {
      setConfirmingDeleteId(null);
      setActionError(null);
      invalidate();
    },
    onError: (err) =>
      setActionError(err instanceof ApiError ? err.message : 'Could not delete the comment.'),
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

  function startEdit(c: Comment) {
    setActionError(null);
    setConfirmingDeleteId(null);
    setEditingId(c.id);
    setEditBody(c.body);
  }

  function saveEdit(id: string) {
    if (!editBody.trim()) return; // blank edit blocked client-side (button also disabled)
    editMutation.mutate({ id, body: editBody });
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

      {actionError && <p className="form__error" role="alert">{actionError}</p>}

      {comments.length > 0 && (
        <ul className="comments__list">
          {comments.map((c) => {
            const isOwn = !!currentUserId && c.authorId === currentUserId;
            const isEditing = editingId === c.id;
            return (
              <li key={c.id} className="comment">
                <p className="comment__meta">
                  <span className="comment__author">{c.authorEmail}</span>{' '}
                  <time dateTime={c.createdAt}>{new Date(c.createdAt).toLocaleString()}</time>
                  {c.editedAt && <span className="comment__edited"> (edited)</span>}
                </p>

                {isEditing ? (
                  <div className="comment__edit">
                    <label htmlFor={`edit-${c.id}`} className="visually-hidden">
                      Edit comment
                    </label>
                    <textarea
                      id={`edit-${c.id}`}
                      aria-label="Edit comment"
                      value={editBody}
                      maxLength={10000}
                      onChange={(e) => setEditBody(e.target.value)}
                    />
                    <div className="comment__actions">
                      <button
                        type="button"
                        onClick={() => saveEdit(c.id)}
                        disabled={editMutation.isPending || !editBody.trim()}
                      >
                        Save
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setEditingId(null);
                          setEditBody('');
                        }}
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                ) : (
                  <p className="comment__body">{c.body}</p>
                )}

                {isOwn && !isEditing && (
                  <div className="comment__actions">
                    {confirmingDeleteId === c.id ? (
                      <>
                        <span>Delete this comment?</span>
                        <button
                          type="button"
                          onClick={() => deleteMutation.mutate(c.id)}
                          disabled={deleteMutation.isPending}
                        >
                          Confirm delete
                        </button>
                        <button type="button" onClick={() => setConfirmingDeleteId(null)}>
                          Cancel
                        </button>
                      </>
                    ) : (
                      <>
                        <button type="button" onClick={() => startEdit(c)}>
                          Edit
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setActionError(null);
                            setConfirmingDeleteId(c.id);
                          }}
                        >
                          Delete
                        </button>
                      </>
                    )}
                  </div>
                )}
              </li>
            );
          })}
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
