"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";

export default function ChangePasswordPage() {
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSuccess(false);

    const form = new FormData(event.currentTarget);
    const currentPassword = String(form.get("currentPassword") ?? "");
    const newPassword = String(form.get("newPassword") ?? "");
    const confirmPassword = String(form.get("confirmPassword") ?? "");

    if (newPassword !== confirmPassword) {
      setError("New password and confirmation do not match.");
      return;
    }

    if (newPassword.length < 8) {
      setError("New password must be at least 8 characters long.");
      return;
    }

    setPending(true);
    try {
      const response = await fetch("/api/auth/change-password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentPassword, newPassword })
      });

      setPending(false);

      if (response.status === 204) {
        setSuccess(true);
        (event.target as HTMLFormElement).reset();
        return;
      }

      const body = await response.json().catch(() => ({}));
      setError(body.message ?? "Failed to change password. Please verify current credentials.");
    } catch {
      setPending(false);
      setError("Service temporarily unavailable. Please try again.");
    }
  }

  return (
    <main>
      <section className="card" style={{ maxWidth: 440, margin: "2rem auto" }}>
        <h1>Change Password</h1>
        <p className="muted">
          Required for initial logins and accounts with temporary passwords.
        </p>

        {error && <div className="banner error">{error}</div>}
        {success && (
          <div
            className="banner success"
            style={{
              background: "#e6ffed",
              color: "#1a7f37",
              padding: "0.75rem",
              borderRadius: "6px",
              marginBottom: "1rem"
            }}
          >
            Password updated successfully.
          </div>
        )}

        <form onSubmit={onSubmit}>
          <label>
            Current Password
            <input
              name="currentPassword"
              type="password"
              required
              autoComplete="current-password"
            />
          </label>

          <label>
            New Password
            <input
              name="newPassword"
              type="password"
              required
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
            />
            <small className="muted">At least 8 characters, with letters, numbers, and symbols.</small>
          </label>

          <label>
            Confirm New Password
            <input
              name="confirmPassword"
              type="password"
              required
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
            />
          </label>

          <button type="submit" disabled={pending} style={{ marginTop: "1rem" }}>
            {pending ? "Updating..." : "Update Password"}
          </button>
        </form>

        <p className="muted" style={{ marginTop: "1.5rem" }}>
          Return to <Link href="/login">Login</Link>
        </p>
      </section>
    </main>
  );
}
