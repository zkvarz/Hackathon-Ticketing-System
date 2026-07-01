// Ticket activity history (FR-S6 stretch; HTS-042). Read-only, chronological list of a ticket's
// changes — who changed what, when. One entry per changed field on create/edit and per state
// transition (recorded by HTS-041). state/type values are shown with their human-readable labels;
// other fields show the raw old→new strings captured at change time. Loading/empty/error states
// reuse the shared components (HTS-032).

import { useQuery } from '@tanstack/react-query';
import { listActivity, type TicketActivity } from '../../api/activity';
import {
  TICKET_STATE_LABELS,
  TICKET_TYPE_LABELS,
  type TicketState,
  type TicketType,
} from '../../api/tickets';
import { Loading } from '../../components/Loading';
import { Empty } from '../../components/Empty';
import { ErrorState } from '../../components/ErrorState';

// Friendly noun for each tracked field (used in "changed the <noun>").
const FIELD_NOUNS: Record<string, string> = {
  team: 'team',
  epic: 'epic',
  type: 'type',
  state: 'state',
  title: 'title',
  body: 'description',
};

function formatValue(field: string, value: string | null): string {
  if (value == null || value === '') return '—';
  if (field === 'state') return TICKET_STATE_LABELS[value as TicketState] ?? value;
  if (field === 'type') return TICKET_TYPE_LABELS[value as TicketType] ?? value;
  return value;
}

function describe(entry: TicketActivity): string {
  if (entry.field === 'created') return 'created the ticket';
  const noun = FIELD_NOUNS[entry.field] ?? entry.field;
  return `changed the ${noun} from “${formatValue(entry.field, entry.oldValue)}” to “${formatValue(
    entry.field,
    entry.newValue,
  )}”`;
}

export function ActivityPanel({ ticketId }: { ticketId: string }) {
  const activityQuery = useQuery({
    queryKey: ['activity', ticketId],
    queryFn: () => listActivity(ticketId),
  });

  const entries = activityQuery.data ?? [];

  return (
    <section className="activity">
      <h2>Activity</h2>

      {activityQuery.isLoading && <Loading label="Loading activity…" />}
      {activityQuery.isError && (
        <ErrorState
          message="Could not load activity."
          onRetry={() => void activityQuery.refetch()}
        />
      )}
      {activityQuery.data && entries.length === 0 && (
        <Empty title="No activity yet" message="Changes to this ticket will appear here." />
      )}

      {entries.length > 0 && (
        <ol className="activity__list">
          {entries.map((entry) => (
            <li key={entry.id} className="activity__entry">
              <p className="activity__text">
                <span className="activity__actor">{entry.actorEmail}</span> {describe(entry)}
              </p>
              <time className="activity__time" dateTime={entry.at}>
                {new Date(entry.at).toLocaleString()}
              </time>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
