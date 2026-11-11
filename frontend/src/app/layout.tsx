import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "MediChannel | Lanka Care Hospital",
  description: "Hospital channelling and appointment system"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <header className="topbar">
          <Link className="brand" href="/">Lanka Care · MediChannel</Link>
          <nav className="nav">
            <Link href="/doctors">Doctors</Link>
            <Link href="/login">Log in</Link>
            <Link href="/register">Register</Link>
          </nav>
        </header>
        {children}
      </body>
    </html>
  );
}
