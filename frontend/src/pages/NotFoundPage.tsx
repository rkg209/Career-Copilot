import { Link } from "react-router-dom";
import { AppLayout } from "../components/AppLayout";

export function NotFoundPage() {
  return (
    <AppLayout>
      <div className="flex min-h-[50vh] flex-col items-center justify-center gap-3 text-center">
        <p className="text-sm font-medium text-accent">404</p>
        <h1 className="text-2xl font-semibold text-fg">Page not found</h1>
        <p className="max-w-md text-sm text-muted">
          The page you're looking for doesn't exist or may have moved.
        </p>
        <Link to="/" className="btn btn-primary mt-2">
          Back to home
        </Link>
      </div>
    </AppLayout>
  );
}
