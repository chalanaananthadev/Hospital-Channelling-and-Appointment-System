import { cookies } from "next/headers";

export const ACCESS_COOKIE = "mc_token";

export async function cookieOptions() {
  const secure = process.env.COOKIE_SECURE === "true" || process.env.NODE_ENV === "production";
  return {
    httpOnly: true,
    secure,
    sameSite: "strict" as const,
    path: "/",
    maxAge: 60 * 60 * 8
  };
}

export async function setAccessCookie(token: string) {
  const store = await cookies();
  store.set(ACCESS_COOKIE, token, await cookieOptions());
}

export async function clearAccessCookie() {
  const store = await cookies();
  store.set(ACCESS_COOKIE, "", { ...(await cookieOptions()), maxAge: 0 });
}

export async function getAccessToken() {
  const store = await cookies();
  return store.get(ACCESS_COOKIE)?.value ?? null;
}
