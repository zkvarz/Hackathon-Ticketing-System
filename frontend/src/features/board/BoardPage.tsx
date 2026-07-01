// Kanban board (FR-B1/B2/B3/B8/B10, FR-S5; HTS-026, wireframe image1). A team selector drives the
// board query (HTS-025); tickets are grouped into the five workflow-ordered columns (already
// modified-desc within a column from the server). Cards show type/title/epic and open the ticket;
// a New-ticket button starts create. Drag-drop (HTS-028) and filters (HTS-030) come later.

import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { listTeams } from '../../api/teams';
import {
  listTickets,
  TICKET_STATES,
  TICKET_STATE_LABELS,
  TICKET_TYPE_LABELS,
  type Ticket,
} from '../../api/tickets';
import { Loading } from '../../components/Loading';
import { Empty } from '../../components/Empty';
import { ErrorState } from '../../components/ErrorState';

export function BoardPage() {
  const navigate = useNavigate();
  const teamsQuery = useQuery({ queryKey: ['teams'], queryFn: listTeams });
  const teams = teamsQuery.data ?? [];

  const [teamId, setTeamId] = useState('');
  useEffect(() => {
    if (!teamId && teams.length > 0) setTeamId(teams[0].id);
  }, [teams, teamId]);

  const boardQuery = useQuery({
    queryKey: ['tickets', teamId],
    queryFn: () => listTickets(teamId),
    enabled: !!teamId,
  });
  const tickets = boardQuery.data ?? [];

  return (
    <section className="board">
      <div className="board__toolbar">
        <h1>Board</h1>
        <label htmlFor="board-team">Team</label>
        <select id="board-team" value={teamId} onChange={(e) => setTeamId(e.target.value)}>
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

      {teamsQuery.data && teams.length === 0 && (
        <Empty title="No teams yet" message="Create a team before using the board." />
      )}

      {teamId && boardQuery.isLoading && <Loading label="Loading board…" />}
      {teamId && boardQuery.isError && (
        <ErrorState message="Could not load the board." onRetry={() => void boardQuery.refetch()} />
      )}

      {teamId && boardQuery.data && (
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
