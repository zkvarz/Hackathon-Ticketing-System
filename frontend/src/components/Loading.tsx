// Shared loading state (NFR-3). Reused across screens in later epics.

interface LoadingProps {
  label?: string;
}

export function Loading({ label = 'Loading…' }: LoadingProps) {
  return (
    <div role="status" aria-live="polite" className="state state--loading">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
