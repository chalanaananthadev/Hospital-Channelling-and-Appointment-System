"use client";

import { FormEvent, Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

const ROLE_HOME: Record<string, string> = {
  CUSTOMER: "/patient",
  DOCTOR: "/doctor",
  MANAGER: "/manager",
  ADMIN: "/admin"
};

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  // Validate next redirect to prevent open redirect attacks
  const rawNext = searchParams.get("next");
  const validNext = rawNext && rawNext.startsWith("/") && !rawNext.startsWith("//") ? rawNext : null;

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setPending(true);
    const form = new FormData(event.currentTarget);
    try {
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
      const destination = validNext ?? ROLE_HOME[body.role] ?? "/";
      router.push(destination);
      router.refresh();
    } catch {
      setPending(false);
      setError("Service temporarily unavailable");
    }
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
          <button type="submit" disabled={pending}>
            {pending ? "Signing in…" : "Sign in"}
          </button>
        </form>
      </section>
    </main>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<main><div className="card">Loading…</div></main>}>
      <LoginForm />
    </Suspense>
  );
}
