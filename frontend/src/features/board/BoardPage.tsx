// Kanban board (FR-B1/B2/B3/B8/B10, FR-S5, FR-B9; HTS-026 + HTS-030, wireframe image1). A team
// selector drives the board query (HTS-025); tickets are grouped into the five workflow-ordered
// columns (already modified-desc within a column from the server). Cards show type/title/epic and
// open the ticket; a New-ticket button starts create. Filter/search controls (title search, type,
// epic, clear + a result count) drive the server-side filtered query (HTS-029), combined with AND.
// Drag-drop (HTS-028) comes later.

import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { listTeams } from '../../api/teams';
import { listEpics } from '../../api/epics';
import {
  listTickets,
  TICKET_STATES,
  TICKET_STATE_LABELS,
  TICKET_TYPES,
  TICKET_TYPE_LABELS,
  type Ticket,
  type TicketType,
} from '../../api/tickets';
import { Loading } from '../../components/Loading';
import { Empty } from '../../components/Empty';
import { ErrorState } from '../../components/ErrorState';

/** Debounce delay (ms) for the title search so each keystroke doesn't fire a query (FR-B9). */
const SEARCH_DEBOUNCE_MS = 250;

export function BoardPage() {
  const navigate = useNavigate();
  const teamsQuery = useQuery({ queryKey: ['teams'], queryFn: listTeams });
  const teams = teamsQuery.data ?? [];

  const [teamId, setTeamId] = useState('');
  useEffect(() => {
    if (!teamId && teams.length > 0) setTeamId(teams[0].id);
  }, [teams, teamId]);

  // Filter state (HTS-030). `search` is the live input; `debouncedQ` is what the query uses.
  const [typeFilter, setTypeFilter] = useState<TicketType | ''>('');
  const [epicFilter, setEpicFilter] = useState('');
  const [search, setSearch] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');

  useEffect(() => {
    const handle = setTimeout(() => setDebouncedQ(search.trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [search]);

  // Epic options are scoped to the selected team.
  const epicsQuery = useQuery({
    queryKey: ['epics', teamId],
    queryFn: () => listEpics(teamId),
    enabled: !!teamId,
  });
  const epics = epicsQuery.data ?? [];

  const filters = {
    type: typeFilter || undefined,
    epicId: epicFilter || undefined,
    q: debouncedQ || undefined,
  };
  const hasActiveFilters = Boolean(typeFilter || epicFilter || debouncedQ);

  const boardQuery = useQuery({
    queryKey: ['tickets', teamId, typeFilter, epicFilter, debouncedQ],
    queryFn: () => listTickets(teamId, filters),
    enabled: !!teamId,
  });
  const tickets = boardQuery.data ?? [];

  // Changing the team resets the epic filter (a now-cross-team epic must not stay selected); the
  // epic dropdown reloads for the new team.
  function changeTeam(nextTeamId: string) {
    setTeamId(nextTeamId);
    setEpicFilter('');
  }

  function clearFilters() {
    setTypeFilter('');
    setEpicFilter('');
    setSearch('');
    setDebouncedQ('');
  }

  const filteredEmpty = Boolean(teamId) && boardQuery.data != null && tickets.length === 0;

  return (
    <section className="board">
      <div className="board__toolbar">
        <h1>Board</h1>
        <label htmlFor="board-team">Team</label>
        <select id="board-team" value={teamId} onChange={(e) => changeTeam(e.target.value)}>
          {teams.map((team) => (
            <option key={team.id} value={team.id}>
              {team.name}
            </option>
          ))}
        </select>
        <button type="button" onClick={() => navigate('/tickets/new')}>
          New ticket
        </button>
      </div>

      <div className="board__filters" role="search">
        <input
          type="search"
          aria-label="Search tickets"
          placeholder="Search titles…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select
          aria-label="Filter by type"
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value as TicketType | '')}
        >
          <option value="">All types</option>
          {TICKET_TYPES.map((t) => (
            <option key={t} value={t}>
              {TICKET_TYPE_LABELS[t]}
            </option>
          ))}
        </select>
        <select
          aria-label="Filter by epic"
          value={epicFilter}
          onChange={(e) => setEpicFilter(e.target.value)}
          disabled={epics.length === 0}
        >
          <option value="">All epics</option>
          {epics.map((epic) => (
            <option key={epic.id} value={epic.id}>
              {epic.title}
            </option>
          ))}
        </select>
        <button type="button" onClick={clearFilters} disabled={!hasActiveFilters}>
          Clear
        </button>
        {boardQuery.data != null && (
          <span className="board__result-count" aria-label="Result count">
            {tickets.length} {tickets.length === 1 ? 'ticket' : 'tickets'}
          </span>
        )}
      </div>

      {teamsQuery.data && teams.length === 0 && (
        <Empty title="No teams yet" message="Create a team before using the board." />
      )}

      {teamId && boardQuery.isLoading && <Loading label="Loading board…" />}
      {teamId && boardQuery.isError && (
        <ErrorState message="Could not load the board." onRetry={() => void boardQuery.refetch()} />
      )}

      {/* Filtered-to-empty is an empty state, not an error (AC-4). */}
      {boardQuery.data != null && filteredEmpty && hasActiveFilters && (
        <Empty
          title="No matching tickets"
          message="No tickets match the current filters."
          action={
            <button type="button" onClick={clearFilters}>
              Clear filters
            </button>
          }
        />
      )}

      {boardQuery.data != null && !(filteredEmpty && hasActiveFilters) && (
        <div className="board__columns">
          {TICKET_STATES.map((state) => {
            const column = tickets.filter((t) => t.state === state);
            return (
              <div key={state} className="board__column" aria-label={TICKET_STATE_LABELS[state]}>
                <div className="board__column-head">
                  <h2>{TICKET_STATE_LABELS[state]}</h2>
                  <span className="board__count" aria-label={`${TICKET_STATE_LABELS[state]} count`}>
                    {column.length}
                  </span>
                </div>
                {column.length === 0 ? (
                  <p className="board__empty-col">No tickets</p>
                ) : (
                  <ul className="board__cards">
                    {column.map((ticket) => (
                      <li key={ticket.id}>
                        <TicketCard ticket={ticket} onOpen={() => navigate(`/tickets/${ticket.id}`)} />
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

function TicketCard({ ticket, onOpen }: { ticket: Ticket; onOpen: () => void }) {
  return (
    <button type="button" className="ticket-card" onClick={onOpen}>
      <span className="ticket-card__type" data-type={ticket.type}>
        {TICKET_TYPE_LABELS[ticket.type]}
      </span>
      <span className="ticket-card__title">{ticket.title}</span>
      {ticket.epicTitle && <span className="ticket-card__epic">{ticket.epicTitle}</span>}
      <time className="ticket-card__time" dateTime={ticket.modifiedAt}>
        {new Date(ticket.modifiedAt).toLocaleDateString()}
      </time>
    </button>
  );
}
