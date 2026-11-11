"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

const ROLE_HOME: Record<string, string> = {
  CUSTOMER: "/patient",
  DOCTOR: "/doctor",
  MANAGER: "/manager",
  ADMIN: "/admin"
};

export default function LoginPage() {
  const router = useRouter();
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setPending(true);
    const form = new FormData(event.currentTarget);
    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: form.get("email"),
        password: form.get("password")
      })
    });
    const body = await response.json().catch(() => ({}));
    setPending(false);
    if (!response.ok) {
      setError(body.message ?? "Invalid email or password");
      return;
    }
    router.push(ROLE_HOME[body.role] ?? "/");
    router.refresh();
  }

  return (
    <main>
      <section className="card">
        <h1>Log in</h1>
        <p className="muted">One generic error is returned for a wrong password or a locked account.</p>
        <form onSubmit={onSubmit}>
          <label>
            Email
            <input name="email" type="email" required autoComplete="username" />
          </label>
          <label>
            Password
            <input name="password" type="password" required autoComplete="current-password" />
          </label>
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={pending}>{pending ? "Signing in…" : "Sign in"}</button>
        </form>
      </section>
    </main>
  );
}
