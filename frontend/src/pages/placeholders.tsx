// Placeholder route pages for the baseline shell. Real screens replace these as epics land
// (teams: HTS-016; epics: HTS-018; ticket view: HTS-020; board: HTS-026). Each just identifies
// its route so navigation/fallback are verifiable.

function Placeholder({ title }: { title: string }) {
  return (
    <section className="page-placeholder">
      <h1>{title}</h1>
      <p>This screen is implemented in a later ticket.</p>
    </section>
  );
}

export const BoardPage = () => <Placeholder title="Board" />;

export const NotFoundPage = () => <Placeholder title="Page not found" />;
