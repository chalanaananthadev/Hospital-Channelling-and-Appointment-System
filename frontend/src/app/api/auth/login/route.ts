import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api";
import { cookieOptions, ACCESS_COOKIE } from "@/lib/cookies";

export const runtime = "nodejs";

export async function POST(request: NextRequest) {
  try {
    const payload = await request.json();
    const forwardedFor = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim();
    const headers: Record<string, string> = {};
    if (forwardedFor) {
      headers["X-Forwarded-For"] = forwardedFor;
    }

    const upstream = await backendFetch("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(payload),
      headers
    });

    const body = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      return NextResponse.json(body, { status: upstream.status });
    }

    const response = NextResponse.json(
      {
        userId: body.userId,
        role: body.role,
        fullName: body.fullName,
        mustChangePassword: body.mustChangePassword
      },
      { status: 200 }
    );
    response.cookies.set(ACCESS_COOKIE, body.accessToken, await cookieOptions());
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch {
    return NextResponse.json(
      { message: "Service temporarily unavailable" },
      { status: 502 }
    );
  }
}
