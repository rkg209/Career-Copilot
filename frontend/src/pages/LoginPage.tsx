import { useState, type FormEvent } from "react";
import { useNavigate, Link } from "react-router-dom";
import { login } from "../api";
import { AuthLayout } from "../components/AuthLayout";
import { Alert, Button, Field, Input } from "../components/ui";

export function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const { token } = await login(email, password);
      localStorage.setItem("token", token);
      navigate("/");
    } catch {
      setError("Invalid email or password");
    }
  }

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Log in to your Career Copilot account"
      footer={
        <>
          No account?{" "}
          <Link to="/register" className="font-medium text-accent hover:underline">
            Register
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Field label="Email" htmlFor="email">
          <Input
            id="email"
            type="email"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </Field>
        <Field label="Password" htmlFor="password">
          <Input
            id="password"
            type="password"
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </Field>
        {error && <Alert variant="error">{error}</Alert>}
        <Button type="submit" variant="primary" className="w-full">
          Log in
        </Button>
      </form>
    </AuthLayout>
  );
}
