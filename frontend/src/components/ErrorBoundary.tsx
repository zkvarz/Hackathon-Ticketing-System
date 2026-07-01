// Top-level React error boundary (HTS-032, NFR-3). A render error in any screen is caught here
// and shown as a recoverable fallback instead of blanking the whole app. "Try again" resets the
// boundary so a transient error can recover without a full reload.

import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  /** Optional custom fallback; receives a reset callback. */
  fallback?: (reset: () => void) => ReactNode;
}

interface State {
  hasError: boolean;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Log for diagnostics; the user sees only the safe fallback.
    console.error('Unhandled render error', error, info);
  }

  private reset = () => this.setState({ hasError: false });

  render() {
    if (!this.state.hasError) return this.props.children;
    if (this.props.fallback) return this.props.fallback(this.reset);

    return (
      <div role="alert" className="state state--error error-boundary">
        <p className="state__title">Something went wrong</p>
        <p className="state__message">
          An unexpected error occurred. You can try again, or reload the page.
        </p>
        <button type="button" onClick={this.reset}>
          Try again
        </button>
      </div>
    );
  }
}
