// Epic management screen (FR-S8, HTS-018): a team selector drives the list of that team's
// epics (title, ticket count, modified); create (team fixed to the selection), edit
// title/description (team not editable, FR-E2), and delete (disabled while referenced).

import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listTeams } from '../../api/teams';
import { createEpic, deleteEpic, listEpics, updateEpic, type Epic } from '../../api/epics';
import { ApiError } from '../../api/client';
import { Loading } from '../../components/Loading';
import { Empty } from '../../components/Empty';
import { ErrorState } from '../../components/ErrorState';

function messageFor(err: unknown, fallback: string): string {
  if (err instanceof ApiError) {
    if (err.code === 'EPIC_HAS_TICKETS') return 'This epic has tickets and cannot be deleted.';
    return err.message || fallback;
  }
  return fallback;
}

export function EpicsPage() {
  const queryClient = useQueryClient();
  const { data: teams } = useQuery({ queryKey: ['teams'], queryFn: listTeams });

  const [teamId, setTeamId] = useState('');
  useEffect(() => {
    if (!teamId && teams && teams.length > 0) {
      setTeamId(teams[0].id);
    }
  }, [teams, teamId]);

  const epicsQuery = useQuery({
    queryKey: ['epics', teamId],
    queryFn: () => listEpics(teamId),
    enabled: !!teamId,
  });

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [createError, setCreateError] = useState<string | null>(null);
  const [rowError, setRowError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['epics', teamId] });

  const createMutation = useMutation({
    mutationFn: () => createEpic(teamId, { title, description }),
    onSuccess: () => {
      setTitle('');
      setDescription('');
      setCreateError(null);
      void invalidate();
    },
    onError: (err) => setCreateError(messageFor(err, 'Could not create the epic.')),
  });

  const updateMutation = useMutation({
    mutationFn: () => updateEpic(editingId!, { title: editTitle, description: editDescription }),
    onSuccess: () => {
      setEditingId(null);
      setRowError(null);
      void invalidate();
    },
    onError: (err) => setRowError(messageFor(err, 'Could not update the epic.')),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteEpic(id),
    onSuccess: () => {
      setConfirmingId(null);
      setRowError(null);
      void invalidate();
    },
    onError: (err) => setRowError(messageFor(err, 'Could not delete the epic.')),
  });

  function submitCreate(e: React.FormEvent) {
    e.preventDefault();
    setCreateError(null);
    if (!title.trim()) {
      setCreateError('Title is required.');
      return;
    }
    createMutation.mutate();
  }

  function startEdit(epic: Epic) {
    setRowError(null);
    setEditingId(epic.id);
    setEditTitle(epic.title);
    setEditDescription(epic.description ?? '');
  }

  function submitEdit(e: React.FormEvent) {
    e.preventDefault();
    if (!editTitle.trim()) {
      setRowError('Title is required.');
      return;
    }
    updateMutation.mutate();
  }

  const epics = epicsQuery.data;

  return (
    <section className="epics-page">
      <h1>Epics</h1>

      <div className="epics-team-select">
        <label htmlFor="team-select">Team</label>
        <select id="team-select" value={teamId} onChange={(e) => setTeamId(e.target.value)}>
          {(teams ?? []).map((team) => (
            <option key={team.id} value={team.id}>
              {team.name}
            </option>
          ))}
        </select>
      </div>

      {teams && teams.length === 0 && (
        <Empty title="No teams yet" message="Create a team before adding epics." />
      )}

      {teamId && (
        <form className="epics-create" onSubmit={submitCreate}>
          <label htmlFor="epic-title">New epic</label>
          <input
            id="epic-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Epic title"
          />
          <label htmlFor="epic-description">Description</label>
          <textarea
            id="epic-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
          <button type="submit" disabled={createMutation.isPending}>
            Add epic
          </button>
          {createError && <p className="form__error" role="alert">{createError}</p>}
        </form>
      )}

      {rowError && <p className="form__error" role="alert">{rowError}</p>}

      {epicsQuery.isLoading && <Loading label="Loading epics…" />}
      {epicsQuery.isError && (
        <ErrorState message="Could not load epics." onRetry={() => void epicsQuery.refetch()} />
      )}
      {epics && epics.length === 0 && (
        <Empty title="No epics yet" message="Create the first epic for this team above." />
      )}

      {epics && epics.length > 0 && (
        <table className="epics-table">
          <thead>
            <tr>
              <th>Title</th>
              <th>Tickets</th>
              <th>Modified</th>
              <th aria-label="actions" />
            </tr>
          </thead>
          <tbody>
            {epics.map((epic) => {
              const referenced = epic.ticketCount > 0;
              return (
                <tr key={epic.id}>
                  <td>
                    {editingId === epic.id ? (
                      <form onSubmit={submitEdit}>
                        <input
                          aria-label={`Edit title of ${epic.title}`}
                          value={editTitle}
                          onChange={(e) => setEditTitle(e.target.value)}
                        />
                        <textarea
                          aria-label={`Edit description of ${epic.title}`}
                          value={editDescription}
                          onChange={(e) => setEditDescription(e.target.value)}
                        />
                        <button type="submit">Save</button>
                        <button type="button" onClick={() => setEditingId(null)}>
                          Cancel
                        </button>
                      </form>
                    ) : (
                      epic.title
                    )}
                  </td>
                  <td>{epic.ticketCount}</td>
                  <td>{new Date(epic.modifiedAt).toLocaleDateString()}</td>
                  <td>
                    {editingId !== epic.id && (
                      <>
                        <button type="button" onClick={() => startEdit(epic)}>
                          Edit
                        </button>
                        {confirmingId === epic.id ? (
                          <>
                            <span>Delete?</span>
                            <button type="button" onClick={() => deleteMutation.mutate(epic.id)}>
                              Confirm
                            </button>
                            <button type="button" onClick={() => setConfirmingId(null)}>
                              Cancel
                            </button>
                          </>
                        ) : (
                          <button
                            type="button"
                            disabled={referenced}
                            title={referenced ? 'Remove its tickets first.' : undefined}
                            onClick={() => setConfirmingId(epic.id)}
                          >
                            Delete
                          </button>
                        )}
                      </>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </section>
  );
}
