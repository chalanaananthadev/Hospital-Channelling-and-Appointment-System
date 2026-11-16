"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { LogoutButton } from "./LogoutButton";

interface UserProfile {
  userId: string;
  role: "CUSTOMER" | "DOCTOR" | "MANAGER" | "ADMIN";
  fullName: string;
  email: string;
  mustChangePassword: boolean;
}

const ROLE_LINKS: Record<string, { label: string; href: string }> = {
  CUSTOMER: { label: "Patient Dashboard", href: "/patient" },
  DOCTOR: { label: "Doctor Portal", href: "/doctor" },
  MANAGER: { label: "Manager Desk", href: "/manager" },
  ADMIN: { label: "Admin Console", href: "/admin" }
};

export function SiteHeader() {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    fetch("/api/auth/me")
      .then((res) => {
        if (!res.ok) {
          return null;
        }
        return res.json();
      })
      .then((data) => {
        if (active) {
          setUser(data);
          setLoading(false);
        }
      })
      .catch(() => {
        if (active) {
          setUser(null);
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <header className="topbar">
      <Link className="brand" href="/">
        Lanka Care · MediChannel
      </Link>
      <nav className="nav" style={{ display: "flex", alignItems: "center", gap: "16px" }}>
        <Link href="/doctors">Doctors</Link>
        {loading ? (
          <span style={{ fontSize: "0.9rem", opacity: 0.7 }}>Loading…</span>
        ) : user ? (
          <>
            {ROLE_LINKS[user.role] && (
              <Link href={ROLE_LINKS[user.role].href} style={{ fontWeight: 600 }}>
                {ROLE_LINKS[user.role].label}
              </Link>
            )}
            <span style={{ fontSize: "0.92rem", fontWeight: 500 }}>
              {user.fullName}
            </span>
            <LogoutButton />
          </>
        ) : (
          <>
            <Link href="/login">Log in</Link>
            <Link href="/register">Register</Link>
          </>
        )}
      </nav>
    </header>
  );
}
