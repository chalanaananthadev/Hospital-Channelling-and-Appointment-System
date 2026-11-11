import { NextResponse } from "next/server";
import { ACCESS_COOKIE, cookieOptions } from "@/lib/cookies";

export const runtime = "nodejs";

export async function POST() {
  const response = NextResponse.json({ ok: true });
  response.cookies.set(ACCESS_COOKIE, "", { ...(await cookieOptions()), maxAge: 0 });
  response.headers.set("Cache-Control", "no-store");
  return response;
}
