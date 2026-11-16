import { NextRequest, NextResponse } from "next/server";
import { backendFetch, extractClientIp } from "@/lib/api";
import { getAccessToken } from "@/lib/cookies";

export const runtime = "nodejs";

export async function POST(request: NextRequest) {
  const token = await getAccessToken();
  if (!token) {
    return NextResponse.json({ message: "Authentication required" }, { status: 401 });
  }

  try {
    const payload = await request.json();
    const clientIp = extractClientIp(request);
    const extraHeaders: Record<string, string> = {};
    if (clientIp) {
      extraHeaders["X-Forwarded-For"] = clientIp;
    }

    const upstream = await backendFetch(
      "/api/auth/change-password",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token,
      extraHeaders
    );

    if (upstream.status === 204) {
      const response = new NextResponse(null, { status: 204 });
      response.headers.set("Cache-Control", "no-store");
      return response;
    }

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
