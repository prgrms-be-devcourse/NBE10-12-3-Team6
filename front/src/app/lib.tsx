"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { User, PlanTheme } from "./store";
import { clearStoredAuthentication, hasStoredAuthentication } from "./authStorage";

// ── Colors ────────────────────────────────────────────────────────────────────

const COLOR_STYLES: Record<string, { bg: string; text: string }> = {
  blue: { bg: "#dbeafe", text: "#2563eb" },
  orange: { bg: "#ffedd5", text: "#ea580c" },
  green: { bg: "#dcfce7", text: "#16a34a" },
  purple: { bg: "#f3e8ff", text: "#9333ea" },
  pink: { bg: "#fce7f3", text: "#db2777" },
  teal: { bg: "#ccfbf1", text: "#0d9488" },
  indigo: { bg: "#e0e7ff", text: "#4338ca" },
  cyan: { bg: "#cffafe", text: "#0891b2" },
  brown: { bg: "#fef3c7", text: "#92400e" },
  red: { bg: "#fee2e2", text: "#dc2626" },
  yellow: { bg: "#fef9c3", text: "#ca8a04" },
  gray: { bg: "#f3f4f6", text: "#4b5563" },
  mint: { bg: "#f0fff4", text: "#276749" },
};

export function colorStyle(color: string) {
  return COLOR_STYLES[color] ?? COLOR_STYLES.gray;
}

// ── Shared components ─────────────────────────────────────────────────────────

export function Avatar({ user, size = 36 }: { user: User; size?: number }) {
  const c = colorStyle(user.color);
  const iconSize = Math.max(18, Math.round(size * 0.5));
  return (
    <div
      className="rounded-full flex items-center justify-center shrink-0"
      style={{ width: size, height: size, background: c.bg, color: c.text }}
    >
      <svg
        aria-hidden="true"
        width={iconSize}
        height={iconSize}
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M15.75 7.75a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.75 19.25a7.25 7.25 0 0 1 14.5 0"
        />
      </svg>
    </div>
  );
}

// ── Theme ─────────────────────────────────────────────────────────────────────

export const THEME: Record<
  PlanTheme,
  { label: string; icon: string; bg: string; text: string }
> = {
  meal: { label: "식사", icon: "🍴", bg: "#ffedd5", text: "#ea580c" },
  cafe: { label: "카페", icon: "☕", bg: "#fef3c7", text: "#92400e" },
  activity: { label: "활동", icon: "🚶", bg: "#dbeafe", text: "#2563eb" },
  etc: { label: "기타", icon: "•••", bg: "#f3f4f6", text: "#4b5563" },
};

export function ThemeBadge({ theme }: { theme: PlanTheme }) {
  const t = THEME[theme];
  return (
    <span
      className="text-xs font-bold px-3 py-1.5 rounded-full"
      style={{ background: t.bg, color: t.text }}
    >
      {t.icon} {t.label}
    </span>
  );
}

// ── API base ──────────────────────────────────────────────────────────────────

export const API_BASE =
typeof window !== "undefined"
  ? (process.env.NEXT_PUBLIC_API_BASE ?? `${window.location.protocol}//${window.location.hostname}:8080`)
  : (process.env.NEXT_PUBLIC_API_BASE ?? "http://192.168.0.5:8080");

export const WS_BASE = API_BASE.replace(/^http/, "ws");

// ── Auth guard ────────────────────────────────────────────────────────────────

export function useAuthGuard() {
  const router = useRouter();
  useEffect(() => {
    if (!hasStoredAuthentication()) {
      router.replace("/");
    }
  }, []);
}

// ── API fetch helper ──────────────────────────────────────────────────────────

export async function apiFetch(
  input: string,
  init: RequestInit = {},
): Promise<Response> {
  const accessToken =
    typeof window !== "undefined" ? localStorage.getItem("accessToken") : null;
  const refreshToken =
    typeof window !== "undefined" ? localStorage.getItem("refreshToken") : null;
  const headers: Record<string, string> = {
    ...(init.headers as Record<string, string>),
    ...(accessToken
      ? { Authorization: `Bearer ${refreshToken} ${accessToken}` }
      : {}),
  };
  const res = await fetch(input, { ...init, headers, credentials: "include" });
  // 401: 인증 실패(토큰 만료/구 토큰/미인증) → 로그인 페이지.
  // 이전에는 백엔드가 미인증도 403으로 내려줘서 이 분기가 죽어있었는데,
  // SecurityConfig.authenticationEntryPoint를 401로 바꾸면서 실제로 동작하게 됨.
  // 403/404: 로그인은 됐지만 접근 권한 없음(비회원/비소유자) 또는 리소스 없음 → 홈으로.
  // "너 누군진 알겠는데 여긴 못 들어감"이므로 로그인 페이지가 아닌 홈으로 튕겨야 함.
  if (res.status === 401) {
    clearStoredAuthentication();
    window.location.replace("/");
  } else if (res.status === 403 || res.status === 404) {
    window.location.replace("/home");
  }
  return res;
}

// ── Utilities ─────────────────────────────────────────────────────────────────

export function timeText(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

export function durationText(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h > 0 && m > 0) return `${h}시간 ${m}분`;
  if (h > 0) return `${h}시간`;
  return `${m}분`;
}

export function formatDate(dateStr: string): string {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
  }).format(new Date(dateStr + "T00:00:00"));
}

// ── Layout helpers ────────────────────────────────────────────────────────────

export function PageHeader({
  title,
  onBack,
  right,
}: {
  title: string;
  onBack?: () => void;
  right?: React.ReactNode;
}) {
  return (
    <div className="app-safe-header flex items-center gap-3 px-4 pb-2">
      {onBack ? (
        <button onClick={onBack} className="text-blue-500 p-1 -ml-1">
          <svg
            className="w-6 h-6"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M15 19l-7-7 7-7"
            />
          </svg>
        </button>
      ) : (
        <div className="w-8" />
      )}
      <h1 className="font-semibold text-base flex-1 text-center">{title}</h1>
      <div className="w-8 flex justify-end">{right}</div>
    </div>
  );
}

export function BigActionCard({
  icon,
  title,
  subtitle,
  colorKey,
}: {
  icon: string;
  title: string;
  subtitle: string;
  colorKey: string;
}) {
  const c = colorStyle(colorKey);
  return (
    <div className="flex items-center gap-4 p-4 bg-white rounded-2xl shadow-sm border border-gray-100">
      <div
        className="w-11 h-11 rounded-full flex items-center justify-center text-xl shrink-0"
        style={{ background: c.bg, color: c.text }}
      >
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className="font-semibold">{title}</p>
        <p className="text-xs text-gray-500 mt-0.5 leading-relaxed">
          {subtitle}
        </p>
      </div>
      <svg
        className="w-4 h-4 text-gray-400 shrink-0"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M9 5l7 7-7 7"
        />
      </svg>
    </div>
  );
}
