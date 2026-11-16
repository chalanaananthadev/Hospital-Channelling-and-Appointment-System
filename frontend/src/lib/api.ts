const API_URL = process.env.API_URL ?? "http://localhost:8080";

export function extractClientIp(request: Request): string | undefined {
  const forwarded = request.headers.get("x-forwarded-for");
  if (forwarded) {
    return forwarded.split(",")[0].trim();
  }
  return undefined;
}

export async function backendFetch(
  path: string,
  init: RequestInit = {},
  token?: string | null,
  extraHeaders?: Record<string, string>
) {
  const headers = new Headers(init.headers);
  headers.set("Content-Type", headers.get("Content-Type") ?? "application/json");
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  if (extraHeaders) {
    for (const [key, value] of Object.entries(extraHeaders)) {
      if (value) {
        headers.set(key, value);
      }
    }
  }
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers,
    cache: "no-store"
  });
  return response;
}
