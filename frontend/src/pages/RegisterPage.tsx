import { useState, type FormEvent } from "react";
import { useNavigate, Link } from "react-router-dom";
import { register } from "../api";
import { AuthLayout } from "../components/AuthLayout";
import { Alert, Button, Field, Input } from "../components/ui";

export function RegisterPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await register(email, password);
      navigate("/login");
    } catch {
      setError("Registration failed — email may already be in use");
    }
  }

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Start tailoring resumes in minutes"
      footer={
        <>
          Already have an account?{" "}
          <Link to="/login" className="font-medium text-accent hover:underline">
            Log in
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
        <Field label="Password" htmlFor="password" hint="Minimum 8 characters">
          <Input
            id="password"
            type="password"
            placeholder="••••••••"
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </Field>
        {error && <Alert variant="error">{error}</Alert>}
        <Button type="submit" variant="primary" className="w-full">
          Register
        </Button>
      </form>
    </AuthLayout>
  );
}
