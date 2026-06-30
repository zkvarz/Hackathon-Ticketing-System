// Team management screen (FR-S7, HTS-016): list with counts, create, rename, and delete
// (disabled while the team has epics/tickets). Loading/empty/error states per NFR-3.

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createTeam,
  deleteTeam,
  isReferenced,
  listTeams,
  renameTeam,
  type Team,
} from '../../api/teams';
import { ApiError } from '../../api/client';
import { Loading } from '../../components/Loading';
import { Empty } from '../../components/Empty';
import { ErrorState } from '../../components/ErrorState';

const TEAMS_KEY = ['teams'];

function messageFor(err: unknown, fallback: string): string {
  if (err instanceof ApiError) {
    if (err.code === 'NAME_TAKEN') return 'A team with that name already exists.';
    if (err.code === 'TEAM_HAS_CHILDREN') return 'This team has epics or tickets and cannot be deleted.';
    return err.message || fallback;
  }
  return fallback;
}

export function TeamsPage() {
  const queryClient = useQueryClient();
  const { data: teams, isLoading, isError, refetch } = useQuery({
    queryKey: TEAMS_KEY,
    queryFn: listTeams,
  });

  const [newName, setNewName] = useState('');
  const [createError, setCreateError] = useState<string | null>(null);
  const [rowError, setRowError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState('');
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: TEAMS_KEY });

  const createMutation = useMutation({
    mutationFn: (name: string) => createTeam(name),
    onSuccess: () => {
      setNewName('');
      setCreateError(null);
      void invalidate();
    },
    onError: (err) => setCreateError(messageFor(err, 'Could not create the team.')),
  });

  const renameMutation = useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) => renameTeam(id, name),
    onSuccess: () => {
      setEditingId(null);
      setRowError(null);
      void invalidate();
    },
    onError: (err) => setRowError(messageFor(err, 'Could not rename the team.')),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteTeam(id),
    onSuccess: () => {
      setConfirmingId(null);
      setRowError(null);
      void invalidate();
    },
    onError: (err) => setRowError(messageFor(err, 'Could not delete the team.')),
  });

  function submitCreate(e: React.FormEvent) {
    e.preventDefault();
    setCreateError(null);
    if (!newName.trim()) {
      setCreateError('Team name is required.');
      return;
    }
    createMutation.mutate(newName);
  }

  function startRename(team: Team) {
    setRowError(null);
    setEditingId(team.id);
    setEditName(team.name);
  }

  function submitRename(e: React.FormEvent) {
    e.preventDefault();
    if (!editName.trim() || !editingId) {
      setRowError('Team name is required.');
      return;
    }
    renameMutation.mutate({ id: editingId, name: editName });
  }

  return (
    <section className="teams-page">
      <h1>Teams</h1>

      <form className="teams-create" onSubmit={submitCreate}>
        <label htmlFor="new-team">New team</label>
        <input
          id="new-team"
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder="Team name"
        />
        <button type="submit" disabled={createMutation.isPending}>
          Add team
        </button>
        {createError && <p className="form__error" role="alert">{createError}</p>}
      </form>

      {rowError && <p className="form__error" role="alert">{rowError}</p>}

      {isLoading && <Loading label="Loading teams…" />}
      {isError && <ErrorState message="Could not load teams." onRetry={() => void refetch()} />}
      {teams && teams.length === 0 && (
        <Empty title="No teams yet" message="Create your first team above." />
      )}

      {teams && teams.length > 0 && (
        <table className="teams-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Epics</th>
              <th>Tickets</th>
              <th>Modified</th>
              <th aria-label="actions" />
            </tr>
          </thead>
          <tbody>
            {teams.map((team) => {
              const referenced = isReferenced(team);
              return (
                <tr key={team.id}>
                  <td>
                    {editingId === team.id ? (
                      <form onSubmit={submitRename}>
                        <input
                          aria-label={`Rename ${team.name}`}
                          value={editName}
                          onChange={(e) => setEditName(e.target.value)}
                        />
                        <button type="submit">Save</button>
                        <button type="button" onClick={() => setEditingId(null)}>
                          Cancel
                        </button>
                      </form>
                    ) : (
                      team.name
                    )}
                  </td>
                  <td>{team.epicCount}</td>
                  <td>{team.ticketCount}</td>
                  <td>{new Date(team.modifiedAt).toLocaleDateString()}</td>
                  <td>
                    {editingId !== team.id && (
                      <>
                        <button type="button" onClick={() => startRename(team)}>
                          Rename
                        </button>
                        {confirmingId === team.id ? (
                          <>
                            <span>Delete?</span>
                            <button type="button" onClick={() => deleteMutation.mutate(team.id)}>
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
                            title={referenced ? 'Remove its epics and tickets first.' : undefined}
                            onClick={() => setConfirmingId(team.id)}
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
