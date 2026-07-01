const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export interface UserResponse {
  id: string;
  email: string;
}

export interface AuthResponse {
  token: string;
  expiresIn: number;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed with status ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export function register(email: string, password: string): Promise<UserResponse> {
  return request<UserResponse>("/api/v1/auth/register", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return request<AuthResponse>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function me(token: string): Promise<UserResponse> {
  return request<UserResponse>("/api/v1/auth/me", {
    headers: { Authorization: `Bearer ${token}` },
  });
}
