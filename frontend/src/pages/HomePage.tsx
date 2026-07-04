import { useEffect, useState } from "react";
import { Navigate, Link } from "react-router-dom";
import { me } from "../api";

export function HomePage() {
  const token = localStorage.getItem("token");
  const [email, setEmail] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!token) return;
    me(token)
      .then((user) => setEmail(user.email))
      .catch(() => setFailed(true));
  }, [token]);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  if (failed) {
    localStorage.removeItem("token");
    return <Navigate to="/login" replace />;
  }

  return (
    <div>
      {email ? <p>Logged in as {email}</p> : <p>Loading…</p>}
      <nav>
        <Link to="/jobs">Browse jobs</Link> · <Link to="/jobs/paste">Paste a job</Link> ·{" "}
        <Link to="/applications">My applications</Link>
      </nav>
    </div>
  );
}
