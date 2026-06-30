// Placeholder route pages for the baseline shell. Real screens replace these in later
// epics (auth: HTS-006/008/012; board: HTS-026; teams: HTS-016; epics: HTS-018; ticket
// details: HTS-020). Each just identifies its route so navigation/fallback are verifiable.

import { useParams } from 'react-router-dom';

function Placeholder({ title }: { title: string }) {
  return (
    <section className="page-placeholder">
      <h1>{title}</h1>
      <p>This screen is implemented in a later ticket.</p>
    </section>
  );
}

export const BoardPage = () => <Placeholder title="Board" />;
export const EpicsPage = () => <Placeholder title="Epics" />;

export const TicketDetailsPage = () => {
  const { id } = useParams();
  return <Placeholder title={`Ticket ${id ?? ''}`.trim()} />;
};

export const NotFoundPage = () => <Placeholder title="Page not found" />;
