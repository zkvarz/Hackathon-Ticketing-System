// Shared error state (NFR-3). Surfaces a message and an optional retry action so
// failures never render as a blank screen. Reused across screens in later epics.

interface ErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
}

export function ErrorState({
  title = 'Something went wrong',
  message = 'Please try again.',
  onRetry,
}: ErrorStateProps) {
  return (
    <div role="alert" className="state state--error">
      <p className="state__title">{title}</p>
      <p className="state__message">{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}
