import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
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

  return <div>{email ? <p>Logged in as {email}</p> : <p>Loading…</p>}</div>;
}
