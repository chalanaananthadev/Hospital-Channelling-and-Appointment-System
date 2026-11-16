import { NextRequest, NextResponse } from "next/server";
import { ACCESS_COOKIE } from "@/lib/cookies";

// Role-to-home URL mapping for UX routing
const ROLE_HOME: Record<string, string> = {
  CUSTOMER: "/patient",
  DOCTOR: "/doctor",
  MANAGER: "/manager",
  ADMIN: "/admin"
};

/**
 * Decode base64url JWT payload WITHOUT signature verification.
 * The Next.js frontend must never hold the JWT secret key.
 * NOTE: This frontend middleware handles UX routing only. Real security and
 * authorization boundaries are strictly enforced server-side by Spring Security.
 */
function decodeJwtPayload(token: string): { role?: string; exp?: number } | null {
  try {
    const parts = token.split(".");
    if (parts.length < 2) {
      return null;
    }
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const json = atob(base64);
    return JSON.parse(json);
  } catch {
    return null;
  }
}

export function middleware(request: NextRequest) {
  const pathname = request.nextUrl.pathname;
  const token = request.cookies.get(ACCESS_COOKIE)?.value;

  // 1. If no cookie exists, redirect to login with next query parameter
  if (!token) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("next", pathname);
    return NextResponse.redirect(loginUrl);
  }

  // 2. Decode JWT payload without verifying signature
  const payload = decodeJwtPayload(token);

  // If token is malformed or expired, clear cookie and redirect to login
  if (!payload || !payload.exp || payload.exp * 1000 < Date.now()) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("next", pathname);
    const response = NextResponse.redirect(loginUrl);
    response.cookies.delete(ACCESS_COOKIE);
    return response;
  }

  const role = payload.role ?? "";
  const allowedPrefix = ROLE_HOME[role];

  // 3. Verify user's role matches target path
  // Rules:
  // - CUSTOMER only on /patient
  // - DOCTOR only on /doctor
  // - MANAGER only on /manager
  // - ADMIN only on /admin
  let matches = false;
  if (role === "CUSTOMER" && pathname.startsWith("/patient")) {
    matches = true;
  } else if (role === "DOCTOR" && pathname.startsWith("/doctor")) {
    matches = true;
  } else if (role === "MANAGER" && pathname.startsWith("/manager")) {
    matches = true;
  } else if (role === "ADMIN" && pathname.startsWith("/admin")) {
    matches = true;
  }

  if (!matches) {
    // Redirect user to their own area if logged in with different role
    const homePath = allowedPrefix ?? "/login";
    return NextResponse.redirect(new URL(homePath, request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/patient/:path*",
    "/doctor/:path*",
    "/manager/:path*",
    "/admin/:path*"
  ]
};
