import type { ApiErrorResponse } from "@/types/portal";

export class PortalApiError extends Error {
  status: number;
  fields: Record<string, string>;

  constructor(
    status: number,
    message: string,
    fields: Record<string, string> = {}
  ) {
    super(message);
    this.name = "PortalApiError";
    this.status = status;
    this.fields = fields;
  }
}

export async function portalRequest<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const normalized = path.startsWith("/") ? path.slice(1) : path;
  const headers = new Headers(options.headers);
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`/api/proxy/${normalized}`, {
    ...options,
    headers,
    cache: "no-store"
  });

  const text = await response.text();
  let parsed: unknown = null;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = null;
    }
  }

  if (!response.ok) {
    const err = (parsed ?? {}) as ApiErrorResponse;
    throw new PortalApiError(
      response.status,
      err.message || `Request failed (${response.status})`,
      err.fields ?? {}
    );
  }

  return parsed as T;
}

export function buildQuery(
  params: Record<string, string | number | boolean | null | undefined>
): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  }
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}

export function formatLkr(amount: number | string | null | undefined): string {
  const numeric = typeof amount === "string" ? Number(amount) : (amount ?? 0);
  return `LKR ${numeric.toLocaleString("en-LK", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`;
}

export function toTimeInput(value: string | null | undefined): string {
  if (!value) {
    return "";
  }
  return value.slice(0, 5);
}

export function formatTime12h(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  const [hourStr, minStr] = value.split(":");
  const hour = Number(hourStr);
  const minute = Number(minStr ?? "0");
  if (Number.isNaN(hour) || Number.isNaN(minute)) {
    return value;
  }
  const suffix = hour >= 12 ? "PM" : "AM";
  const displayHour = hour % 12 === 0 ? 12 : hour % 12;
  return `${displayHour}:${String(minute).padStart(2, "0")} ${suffix}`;
}

export function addMinutesToTime(
  startTime: string,
  extraMinutes: number
): string {
  const [hStr, mStr] = startTime.split(":");
  const total =
    Number(hStr || 0) * 60 + Number(mStr || 0) + Math.max(0, extraMinutes);
  const hours = Math.floor(total / 60) % 24;
  const minutes = total % 60;
  return formatTime12h(
    `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`
  );
}

export function todayColomboIso(): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Colombo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).formatToParts(new Date());

  const year = parts.find((p) => p.type === "year")?.value ?? "2026";
  const month = parts.find((p) => p.type === "month")?.value ?? "01";
  const day = parts.find((p) => p.type === "day")?.value ?? "01";
  return `${year}-${month}-${day}`;
}