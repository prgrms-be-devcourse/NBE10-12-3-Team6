export const COOKIE_AUTH_STORAGE_KEY = "cookieAuthenticated";

export function hasStoredAuthentication(): boolean {
  if (typeof window === "undefined") return false;
  return Boolean(localStorage.getItem(COOKIE_AUTH_STORAGE_KEY));
}

export function rememberCookieAuthentication() {
  localStorage.setItem(COOKIE_AUTH_STORAGE_KEY, "true");
}

export function clearStoredAuthentication() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem(COOKIE_AUTH_STORAGE_KEY);
  localStorage.removeItem("userId");
  localStorage.removeItem("userName");
}

// ── 공유 /auth/me 싱글플라이트 캐시 ───────────────────────────────────────────
// 화면(페이지) 진입/새로고침 시 인증 가드·계정 정보·로그인 상태 복원 등 여러 곳이 거의 동시에
// /auth/me를 필요로 해도, 실제 네트워크 요청은 한 번만 나가고 나머지는 그 결과(promise)를
// 공유해서 기다린 뒤 각자 비동기로 이어서 처리한다. 요청이 끝나면 캐시를 즉시 비워서,
// 다른 화면으로 이동한 뒤의 호출은 그 화면의 새 진입 시점 기준으로 다시 한 번만 나가게 한다.
export const AUTH_ME_PATH = "/api/v1/auth/me";

export interface MeData {
  id: number;
  name: string;
  email: string;
}

export interface MeResult {
  ok: boolean;
  data: MeData | null;
}

function resolveApiBase(): string {
  return typeof window !== "undefined"
    ? (process.env.NEXT_PUBLIC_API_BASE ?? `${window.location.protocol}//${window.location.hostname}:8080`)
    : (process.env.NEXT_PUBLIC_API_BASE ?? "http://192.168.0.5:8080");
}

async function fetchMe(): Promise<MeResult> {
  try {
    const res = await fetch(`${resolveApiBase()}${AUTH_ME_PATH}`, { credentials: "include" });
    if (!res.ok) return { ok: false, data: null };
    const body = await res.json().catch(() => null);
    return { ok: true, data: body?.data ?? null };
  } catch {
    return { ok: false, data: null };
  }
}

let mePromise: Promise<MeResult> | null = null;

export function getMe(): Promise<MeResult> {
  if (!mePromise) {
    mePromise = fetchMe().finally(() => {
      mePromise = null;
    });
  }
  return mePromise;
}
