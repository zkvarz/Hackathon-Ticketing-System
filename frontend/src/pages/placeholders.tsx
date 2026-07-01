// Remaining placeholder route page. Feature screens have replaced the rest as epics landed
// (teams: HTS-016; epics: HTS-018; ticket view: HTS-020; board: HTS-026). Only the not-found
// fallback stays a placeholder until a dedicated 404 screen is designed.

function Placeholder({ title }: { title: string }) {
  return (
    <section className="page-placeholder">
      <h1>{title}</h1>
      <p>This screen is implemented in a later ticket.</p>
    </section>
  );
}

export const NotFoundPage = () => <Placeholder title="Page not found" />;
