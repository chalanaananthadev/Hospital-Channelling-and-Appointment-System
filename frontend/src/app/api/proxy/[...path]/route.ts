import { NextRequest, NextResponse } from "next/server";
import { backendFetch, extractClientIp } from "@/lib/api";
import { getAccessToken } from "@/lib/cookies";

export const runtime = "nodejs";

async function forward(request: NextRequest, path: string[]) {
  const token = await getAccessToken();
  const search = request.nextUrl.search;
  const target = `/api/${path.join("/")}${search}`;
  const init: RequestInit = { method: request.method };
  if (request.method !== "GET" && request.method !== "HEAD") {
    init.body = await request.text();
  }
  const clientIp = extractClientIp(request);
  const extraHeaders: Record<string, string> = {};
  if (clientIp) {
    extraHeaders["X-Forwarded-For"] = clientIp;
  }

  try {
    const upstream = await backendFetch(target, init, token, extraHeaders);
    const payload = await upstream.text();
    const response = new NextResponse(payload, { status: upstream.status });
    response.headers.set("Content-Type", upstream.headers.get("Content-Type") ?? "application/json");
    const cacheControl = path[0] === "public" ? "public, max-age=120" : "no-store";
    response.headers.set("Cache-Control", cacheControl);
    return response;
  } catch {
    return NextResponse.json(
      { message: "Service temporarily unavailable" },
      { status: 502 }
    );
  }
}

export async function GET(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  return forward(request, path);
}

export async function POST(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  return forward(request, path);
}

export async function PUT(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  return forward(request, path);
}

export async function PATCH(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  return forward(request, path);
}

export async function DELETE(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  return forward(request, path);
}
