"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

export function LogoutButton() {
  const router = useRouter();
  const [pending, setPending] = useState(false);

  async function handleLogout() {
    setPending(true);
    try {
      await fetch("/api/auth/logout", { method: "POST" });
    } catch {
      // quiet fallback
    } finally {
      router.push("/login");
      router.refresh();
    }
  }

  return (
    <button
      type="button"
      onClick={handleLogout}
      disabled={pending}
      style={{
        background: "transparent",
        color: "inherit",
        border: "1px solid currentColor",
        padding: "6px 14px",
        borderRadius: "6px",
        cursor: "pointer",
        fontSize: "0.88rem"
      }}
    >
      {pending ? "Logging out…" : "Log out"}
    </button>
  );
}
