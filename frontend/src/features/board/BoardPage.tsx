// Kanban board (FR-B1/B2/B3/B4/B5/B6/B8/B10, FR-S5, FR-B9, FR-K7; HTS-026 + HTS-030 + HTS-028,
// wireframe image1). A team selector drives the board query (HTS-025); tickets are grouped into
// the five workflow-ordered columns (modified-desc within a column from the server). Cards show
// type/title/epic and open the ticket; a New-ticket button starts create. Filter/search controls
// drive the server-side filtered query (HTS-029). Cards are draggable between any two columns
// (dnd-kit): a drop optimistically moves the card and PATCHes its state, reverting with an error
// toast on failure (FR-B5).

import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useVirtualizer } from '@tanstack/react-virtual';
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCorners,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import { listTeams } from '../../api/teams';
import { listEpics } from '../../api/epics';
import {
  changeTicketState,
  listTickets,
  TICKET_STATES,
  TICKET_STATE_LABELS,
  TICKET_TYPES,
  TICKET_TYPE_LABELS,
  type Ticket,
  type TicketState,
  type TicketType,
} from '../../api/tickets';
import { Loading } from '../../components/Loading';
import { Empty } from '../../components/Empty';
import { ErrorState } from '../../components/ErrorState';
import { useToast } from '../../components/toast/ToastProvider';

/** Debounce delay (ms) for the title search so each keystroke doesn't fire a query (FR-B9). */
const SEARCH_DEBOUNCE_MS = 250;

/** Estimated rendered height of a card + gap (px), used to size the virtualized column (HTS-043). */
const CARD_ESTIMATE_PX = 96;
/** Extra rows rendered above/below the viewport so a drag or fast scroll doesn't reveal blanks. */
const CARD_OVERSCAN = 6;

export function BoardPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
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

  // The exact key of the currently displayed board; the optimistic move mutates this cache entry.
  const boardKey = ['tickets', teamId, typeFilter, epicFilter, debouncedQ] as const;

  const boardQuery = useQuery({
    queryKey: boardKey,
    queryFn: () => listTickets(teamId, filters),
    enabled: !!teamId,
  });
  const tickets = boardQuery.data ?? [];

  // Optimistic state move (FR-B4/B5/B6). On drop: snapshot the board, move the card to the top of
  // the target column locally, PATCH the state; on failure roll back and show an error toast; on
  // success re-sync from the server (authoritative modified-desc order).
  const moveMutation = useMutation({
    mutationFn: ({ id, toState }: { id: string; toState: TicketState }) =>
      changeTicketState(id, toState),
    onMutate: async ({ id, toState }) => {
      await queryClient.cancelQueries({ queryKey: boardKey });
      const previous = queryClient.getQueryData<Ticket[]>(boardKey);
      if (previous) {
        const moved = previous.find((t) => t.id === id);
        if (moved) {
          const rest = previous.filter((t) => t.id !== id);
          // Front of the array = top of its column (most-recently-modified).
          queryClient.setQueryData<Ticket[]>(boardKey, [{ ...moved, state: toState }, ...rest]);
        }
      }
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) queryClient.setQueryData(boardKey, context.previous);
      toast.error('Move failed — the card was returned to its column.');
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tickets'] });
    },
  });

  const sensors = useSensors(
    // A small drag threshold so a plain click still opens the ticket (doesn't start a drag).
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor),
  );

  function onDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over) return;
    const toState = over.id as TicketState;
    const card = tickets.find((t) => t.id === active.id);
    if (!card || card.state === toState) return;
    moveMutation.mutate({ id: String(active.id), toState });
  }

  // Changing the team resets the epic filter (a now-cross-team epic must not stay selected).
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
        <DndContext sensors={sensors} collisionDetection={closestCorners} onDragEnd={onDragEnd}>
          <div className="board__columns">
            {TICKET_STATES.map((state) => {
              const column = tickets.filter((t) => t.state === state);
              return (
                <DroppableColumn key={state} state={state} count={column.length}>
                  {column.length === 0 ? (
                    <p className="board__empty-col">No tickets</p>
                  ) : (
                    <VirtualCards
                      tickets={column}
                      onOpen={(ticketId) => navigate(`/tickets/${ticketId}`)}
                    />
                  )}
                </DroppableColumn>
              );
            })}
          </div>
        </DndContext>
      )}
    </section>
  );
}

function DroppableColumn({
  state,
  count,
  children,
}: {
  state: TicketState;
  count: number;
  children: React.ReactNode;
}) {
  const { setNodeRef, isOver } = useDroppable({ id: state });
  return (
    <div
      ref={setNodeRef}
      className={`board__column${isOver ? ' board__column--over' : ''}`}
      aria-label={TICKET_STATE_LABELS[state]}
    >
      <div className="board__column-head">
        <h2>{TICKET_STATE_LABELS[state]}</h2>
        <span className="board__count" aria-label={`${TICKET_STATE_LABELS[state]} count`}>
          {count}
        </span>
      </div>
      {children}
    </div>
  );
}

/**
 * Virtualized card list for one column (HTS-043, FR-B10). Only the cards in (or near) the viewport
 * are mounted, so a column with hundreds/thousands of tickets stays cheap to render and scroll. The
 * scroll container is the drag-and-drop auto-scroll target, and drop targets are the columns (not
 * individual cards) — the drop handler resolves the moved card from the full ticket data, not the
 * DOM — so drag-and-drop stays correct even across cards that aren't currently rendered.
 */
function VirtualCards({
  tickets,
  onOpen,
}: {
  tickets: Ticket[];
  onOpen: (ticketId: string) => void;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const virtualizer = useVirtualizer({
    count: tickets.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => CARD_ESTIMATE_PX,
    overscan: CARD_OVERSCAN,
  });

  return (
    <div ref={scrollRef} className="board__column-scroll">
      <ul
        className="board__cards"
        style={{ height: virtualizer.getTotalSize(), position: 'relative' }}
      >
        {virtualizer.getVirtualItems().map((item) => {
          const ticket = tickets[item.index];
          return (
            <li
              key={ticket.id}
              className="board__card-item"
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                transform: `translateY(${item.start}px)`,
              }}
            >
              <DraggableCard ticket={ticket} onOpen={() => onOpen(ticket.id)} />
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function DraggableCard({ ticket, onOpen }: { ticket: Ticket; onOpen: () => void }) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: ticket.id,
  });
  const style = transform
    ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0)` }
    : undefined;
  return (
    <button
      type="button"
      ref={setNodeRef}
      style={style}
      className={`ticket-card${isDragging ? ' ticket-card--dragging' : ''}`}
      onClick={onOpen}
      {...listeners}
      {...attributes}
    >
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
