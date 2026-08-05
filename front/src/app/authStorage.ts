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
