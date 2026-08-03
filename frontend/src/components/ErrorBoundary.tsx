import { Component, type ErrorInfo, type ReactNode } from "react";
import { Sentry } from "../sentry";

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/** Catches render errors anywhere below it, forwards to Sentry when configured, and shows a
 *  friendly fallback with a reload action instead of a blank white screen. */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    Sentry.captureException(error, { extra: { componentStack: info.componentStack } });
  }

  render(): ReactNode {
    if (this.state.error) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-canvas p-6 text-center">
          <h1 className="text-xl font-semibold text-fg">Something went wrong</h1>
          <p className="max-w-md text-sm text-muted">
            An unexpected error stopped this page from loading. Reloading usually fixes it.
          </p>
          <button type="button" onClick={() => window.location.reload()} className="btn btn-primary">
            Reload page
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
