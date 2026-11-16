import { NextRequest, NextResponse } from "next/server";
import { backendFetch, extractClientIp } from "@/lib/api";
import { cookieOptions, ACCESS_COOKIE } from "@/lib/cookies";

export const runtime = "nodejs";

export async function POST(request: NextRequest) {
  try {
    const payload = await request.json();
    const clientIp = extractClientIp(request);
    const extraHeaders: Record<string, string> = {};
    if (clientIp) {
      extraHeaders["X-Forwarded-For"] = clientIp;
    }

    const upstream = await backendFetch(
      "/api/auth/register",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      null,
      extraHeaders
    );

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
      { status: 201 }
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
