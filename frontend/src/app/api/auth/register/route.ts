import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api";
import { cookieOptions, ACCESS_COOKIE } from "@/lib/cookies";

export const runtime = "nodejs";

export async function POST(request: NextRequest) {
  const payload = await request.json();
  const upstream = await backendFetch("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(payload)
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
    { status: 201 }
  );
  response.cookies.set(ACCESS_COOKIE, body.accessToken, await cookieOptions());
  response.headers.set("Cache-Control", "no-store");
  return response;
}
