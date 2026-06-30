// Shared empty state (NFR-3). Reused across screens in later epics.

interface EmptyProps {
  title?: string;
  message?: string;
  action?: React.ReactNode;
}

export function Empty({ title = 'Nothing here yet', message, action }: EmptyProps) {
  return (
    <div className="state state--empty">
      <p className="state__title">{title}</p>
      {message && <p className="state__message">{message}</p>}
      {action}
    </div>
  );
}
