import { NextResponse } from "next/server";
import { backendFetch } from "@/lib/api";
import { getAccessToken } from "@/lib/cookies";

export const runtime = "nodejs";

export async function GET() {
  const token = await getAccessToken();
  if (!token) {
    return NextResponse.json({ message: "Authentication required" }, { status: 401 });
  }
  const upstream = await backendFetch("/api/auth/me", { method: "GET" }, token);
  const body = await upstream.json().catch(() => ({}));
  const response = NextResponse.json(body, { status: upstream.status });
  response.headers.set("Cache-Control", "no-store");
  return response;
}
