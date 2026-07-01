// Ticket create / edit / details view (FR-S6, FR-K1..K3, FR-K6; HTS-020, wireframe image3).
// One component serves both modes: `/tickets/new` (create) and `/tickets/:id` (edit + details).
// It shows all fields incl. created-by/at + modified-at metadata, edits type/team/epic/title/
// body/state (state/type shown as human-readable labels, sent as canonical values), and offers
// Save + Delete-with-confirmation. Team↔epic linkage refinement lands in HTS-022; the comments
// panel in HTS-024.

import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/client';
import { listTeams } from '../../api/teams';
import { listEpics } from '../../api/epics';
import {
  createTicket,
  deleteTicket,
  getTicket,
  updateTicket,
  TICKET_STATES,
  TICKET_STATE_LABELS,
  TICKET_TYPES,
  TICKET_TYPE_LABELS,
  type TicketInput,
  type TicketState,
  type TicketType,
} from '../../api/tickets';
import { Loading } from '../../components/Loading';
import { ErrorState } from '../../components/ErrorState';
import { CommentsPanel } from './CommentsPanel';

interface FormState {
  teamId: string;
  epicId: string;
  type: TicketType;
  state: TicketState;
  title: string;
  body: string;
}

const BLANK: FormState = {
  teamId: '',
  epicId: '',
  type: 'bug',
  state: 'new',
  title: '',
  body: '',
};

/**
 * Turn a backend error into a {field: message} map for inline display. Bean-validation errors
 * arrive as fieldErrors[]; the epic-same-team rule (FR-E7/FR-K5, HTS-021) arrives as a top-level
 * EPIC_TEAM_MISMATCH code with no field, so map it onto the epic field defensively — the UI
 * shouldn't let it happen (HTS-022 clears the epic on team change), but a race must not crash.
 */
function fieldErrorsOf(err: unknown): Record<string, string> {
  if (err instanceof ApiError) {
    const map: Record<string, string> = {};
    for (const fe of err.fieldErrors) map[fe.field] = fe.message;
    if (err.code === 'EPIC_TEAM_MISMATCH' && !map.epicId) {
      map.epicId = 'That epic belongs to a different team. Pick an epic from this team.';
    }
    return map;
  }
  return {};
}

export function TicketDetailsPage() {
  const { id } = useParams();
  const isCreate = !id;
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const teamsQuery = useQuery({ queryKey: ['teams'], queryFn: listTeams });

  const ticketQuery = useQuery({
    queryKey: ['ticket', id],
    queryFn: () => getTicket(id!),
    enabled: !isCreate,
  });

  const [form, setForm] = useState<FormState>(BLANK);
  const [seeded, setSeeded] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  // Seed the form from the loaded ticket (edit) once.
  useEffect(() => {
    if (!isCreate && ticketQuery.data && !seeded) {
      const t = ticketQuery.data;
      setForm({
        teamId: t.teamId,
        epicId: t.epicId ?? '',
        type: t.type,
        state: t.state,
        title: t.title,
        body: t.body,
      });
      setSeeded(true);
    }
  }, [isCreate, ticketQuery.data, seeded]);

  // In create mode, default the team to the first available (like the other screens).
  const teams = teamsQuery.data ?? [];
  useEffect(() => {
    if (isCreate && !form.teamId && teams.length > 0) {
      setForm((f) => ({ ...f, teamId: teams[0].id }));
    }
  }, [isCreate, form.teamId, teams]);

  const epicsQuery = useQuery({
    queryKey: ['epics', form.teamId],
    queryFn: () => listEpics(form.teamId),
    enabled: !!form.teamId,
  });
  const epics = epicsQuery.data ?? [];

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  // Changing the team clears the selected epic (FR-K5) — the epic dropdown then reloads for the
  // new team (its query is keyed by teamId), so a now-cross-team epic can never be submitted.
  function changeTeam(teamId: string) {
    setForm((f) => ({ ...f, teamId, epicId: '' }));
    setFieldErrors((e) => ({ ...e, epicId: '', teamId: '' }));
  }

  const payload = (): TicketInput => ({
    teamId: form.teamId,
    epicId: form.epicId || null,
    type: form.type,
    state: form.state,
    title: form.title.trim(),
    body: form.body.trim(),
  });

  const saveMutation = useMutation({
    mutationFn: () => (isCreate ? createTicket(payload()) : updateTicket(id!, payload())),
    onSuccess: (ticket) => {
      setFieldErrors({});
      setFormError(null);
      void queryClient.invalidateQueries({ queryKey: ['tickets'] });
      if (isCreate && ticket) {
        navigate(`/tickets/${ticket.id}`);
      } else {
        setSaved(true);
        void queryClient.invalidateQueries({ queryKey: ['ticket', id] });
      }
    },
    onError: (err) => {
      setFieldErrors(fieldErrorsOf(err));
      setFormError(err instanceof ApiError ? err.message : 'Could not save the ticket.');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteTicket(id!),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tickets'] });
      navigate('/board');
    },
    onError: (err) =>
      setFormError(err instanceof ApiError ? err.message : 'Could not delete the ticket.'),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setSaved(false);
    setFormError(null);
    const errors: Record<string, string> = {};
    if (!form.teamId) errors.teamId = 'Team is required.';
    if (!form.title.trim()) errors.title = 'Title is required.';
    if (!form.body.trim()) errors.body = 'Body is required.';
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});
    saveMutation.mutate();
  }

  if (!isCreate && ticketQuery.isLoading) return <Loading label="Loading ticket…" />;
  if (!isCreate && ticketQuery.isError) {
    return (
      <ErrorState message="Could not load this ticket." onRetry={() => void ticketQuery.refetch()} />
    );
  }

  const ticket = ticketQuery.data;

  return (
    <section className="ticket-view">
      <h1>{isCreate ? 'New ticket' : 'Ticket'}</h1>

      {!isCreate && ticket && (
        <p className="ticket-view__meta">
          Created by {ticket.createdByEmail} on{' '}
          {new Date(ticket.createdAt).toLocaleString()} · Last modified{' '}
          {new Date(ticket.modifiedAt).toLocaleString()}
        </p>
      )}

      <form className="ticket-form" onSubmit={submit} noValidate>
        <label htmlFor="ticket-team">Team</label>
        <select
          id="ticket-team"
          value={form.teamId}
          onChange={(e) => changeTeam(e.target.value)}
        >
          <option value="">Select a team…</option>
          {teams.map((team) => (
            <option key={team.id} value={team.id}>
              {team.name}
            </option>
          ))}
        </select>
        {fieldErrors.teamId && <p className="form__error" role="alert">{fieldErrors.teamId}</p>}

        <label htmlFor="ticket-epic">Epic</label>
        <select
          id="ticket-epic"
          value={form.epicId}
          onChange={(e) => set('epicId', e.target.value)}
          disabled={!form.teamId || epics.length === 0}
        >
          <option value="">No epic</option>
          {epics.map((epic) => (
            <option key={epic.id} value={epic.id}>
              {epic.title}
            </option>
          ))}
        </select>
        {fieldErrors.epicId && <p className="form__error" role="alert">{fieldErrors.epicId}</p>}

        <label htmlFor="ticket-type">Type</label>
        <select
          id="ticket-type"
          value={form.type}
          onChange={(e) => set('type', e.target.value as TicketType)}
        >
          {TICKET_TYPES.map((t) => (
            <option key={t} value={t}>
              {TICKET_TYPE_LABELS[t]}
            </option>
          ))}
        </select>

        <label htmlFor="ticket-state">State</label>
        <select
          id="ticket-state"
          value={form.state}
          onChange={(e) => set('state', e.target.value as TicketState)}
        >
          {TICKET_STATES.map((s) => (
            <option key={s} value={s}>
              {TICKET_STATE_LABELS[s]}
            </option>
          ))}
        </select>

        <label htmlFor="ticket-title">Title</label>
        <input
          id="ticket-title"
          value={form.title}
          maxLength={200}
          onChange={(e) => set('title', e.target.value)}
        />
        {fieldErrors.title && <p className="form__error" role="alert">{fieldErrors.title}</p>}

        <label htmlFor="ticket-body">Description</label>
        <textarea
          id="ticket-body"
          value={form.body}
          maxLength={10000}
          onChange={(e) => set('body', e.target.value)}
        />
        {fieldErrors.body && <p className="form__error" role="alert">{fieldErrors.body}</p>}

        <div className="ticket-form__actions">
          <button type="submit" disabled={saveMutation.isPending}>
            {isCreate ? 'Create ticket' : 'Save'}
          </button>
          {!isCreate &&
            (confirmingDelete ? (
              <>
                <span>Delete this ticket?</span>
                <button type="button" onClick={() => deleteMutation.mutate()}>
                  Confirm delete
                </button>
                <button type="button" onClick={() => setConfirmingDelete(false)}>
                  Cancel
                </button>
              </>
            ) : (
              <button type="button" onClick={() => setConfirmingDelete(true)}>
                Delete
              </button>
            ))}
        </div>

        {formError && <p className="form__error" role="alert">{formError}</p>}
        {saved && <p className="form__success" role="status">Saved.</p>}
      </form>

      {/* Comments exist only for a persisted ticket (HTS-024). */}
      {!isCreate && id && <CommentsPanel ticketId={id} />}
    </section>
  );
}
