import { NextRequest, NextResponse } from "next/server";
import { backendFetch, extractClientIp } from "@/lib/api";
import { getAccessToken } from "@/lib/cookies";

export const runtime = "nodejs";

export async function GET(request: NextRequest) {
  const token = await getAccessToken();
  if (!token) {
    return NextResponse.json({ message: "Authentication required" }, { status: 401 });
  }

  const clientIp = extractClientIp(request);
  const extraHeaders: Record<string, string> = {};
  if (clientIp) {
    extraHeaders["X-Forwarded-For"] = clientIp;
  }

  try {
    const upstream = await backendFetch("/api/auth/me", { method: "GET" }, token, extraHeaders);
    const body = await upstream.json().catch(() => ({}));
    const response = NextResponse.json(body, { status: upstream.status });
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch {
    return NextResponse.json(
      { message: "Service temporarily unavailable" },
      { status: 502 }
    );
  }
}
