"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useStore } from "./store";
import { API_BASE } from "./lib";

type Mode = "landing" | "login" | "signup";
type AuthTransition = "forward" | "back" | "swap";

function Toast({ message, visible }: { message: string; visible: boolean }) {
  return (
    <div className="fixed inset-0 flex items-center justify-center z-50 pointer-events-none">
      <div
        style={{
          width: 110,
          height: 110,
          background: "#1d1d1f",
          borderRadius: "50%",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          boxShadow: "0 8px 32px rgba(0,0,0,0.18)",
          opacity: visible ? 1 : 0,
          transform: visible ? "scale(1)" : "scale(0.72)",
          transition: visible
            ? "opacity 0.28s ease-out, transform 0.32s cubic-bezier(0.34, 1.56, 0.64, 1)"
            : "opacity 0.24s ease-in, transform 0.24s ease-in",
        }}
      >
        <span
          style={{
            width: 74,
            height: 74,
            background: "#34c759",
            borderRadius: "50%",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            flexShrink: 0,
          }}
        >
          <svg width="34" height="34" viewBox="0 0 24 24" fill="none">
            <path d="M4 12L9.5 17.5L20 7" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </span>
      </div>
    </div>
  );
}

export default function LoginPage() {
  const router = useRouter();
  const { login } = useStore();
  const [mode, setMode] = useState<Mode>("landing");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [toast, setToast] = useState<{ message: string; visible: boolean }>({ message: "", visible: false });
  const [loginAnim, setLoginAnim] = useState(false);
  const [welcomeVisible, setWelcomeVisible] = useState(false);
  const [isSignupReveal, setIsSignupReveal] = useState(false);
  const [authTransition, setAuthTransition] = useState<AuthTransition>("forward");
  const [landingVisible, setLandingVisible] = useState(false);

  const changeMode = (nextMode: Mode, transition: AuthTransition = "forward") => {
    if (nextMode === "landing") setLandingVisible(false);
    setAuthTransition(transition);
    setMode(nextMode);
    setError("");
    setEmail(""); setPassword(""); setPasswordConfirm(""); setName("");
  };

  useEffect(() => {
    if (mode !== "landing") return;

    setLandingVisible(false);
    const firstFrame = requestAnimationFrame(() => {
      requestAnimationFrame(() => setLandingVisible(true));
    });

    return () => cancelAnimationFrame(firstFrame);
  }, [mode]);

  const showToast = (message: string, onDone?: () => void) => {
    setToast({ message, visible: true });
    setTimeout(() => {
      setToast(prev => ({ ...prev, visible: false }));
      setTimeout(() => {
        setToast({ message: "", visible: false });
        onDone?.();
      }, 300);
    }, 1500);
  };

  const handleLogin = async () => {
    if (!email.trim() || !password.trim()) return;
    setLoading(true);
    setError("");
    try {
      const joinCode = localStorage.getItem("pendingInviteCode") ?? undefined;
      const res = await fetch(`${API_BASE}/api/v1/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ email: email.trim(), password, joinCode }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body?.message ?? "이메일 또는 비밀번호가 올바르지 않아요.");
      }
      const authHeader = res.headers.get("authorization");
      if (authHeader) {
        const parts = authHeader.split(" ");
        if (parts.length === 3) {
          localStorage.setItem("refreshToken", parts[1]);
          localStorage.setItem("accessToken", parts[2]);
        }
      }
      const body = await res.json().catch(() => ({}));
      login(body.data?.name, body.data?.id);
      setLoginAnim(true);
      setTimeout(() => setWelcomeVisible(true), 500);
      setTimeout(() => router.replace("/home"), 2200);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "로그인에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleSignup = async () => {
    if (!email.trim() || !password.trim() || !name.trim()) return;
    if (password !== passwordConfirm) {
      setError("비밀번호가 일치하지 않아요.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/api/v1/auth/signup`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ email: email.trim(), password, name: name.trim() }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body?.message ?? "회원가입에 실패했습니다.");
      }
      setEmail("");
      setPassword("");
      setPasswordConfirm("");
      setName("");
      setIsSignupReveal(true);
      setLoginAnim(true);
      setTimeout(() => setWelcomeVisible(true), 500);
      setTimeout(() => {
        setLoginAnim(false);
        setWelcomeVisible(false);
        setIsSignupReveal(false);
        changeMode("login", "back");
      }, 2200);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "회원가입에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  // ── 랜딩 ──────────────────────────────────────────────────────────────────────
  if (mode === "landing") {
    return (
      <div className={`auth-landing-screen ${landingVisible ? "is-open" : ""} flex flex-col px-6`} style={{ height: "100dvh", paddingBottom: "max(2rem, env(safe-area-inset-bottom))" }}>
      <Toast message={toast.message} visible={toast.visible} />
        <div className="auth-landing-brand flex-1 flex flex-col items-center justify-center gap-4">
          <span className="auth-landing-icon" aria-hidden="true">
            <svg viewBox="0 0 64 64" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
              <path d="M23 18v-3.5C23 9.8 26.8 6 31.5 6h1C37.2 6 41 9.8 41 14.5V18" strokeWidth="4" />
              <path d="M17 25c0-5 4-9 9-9h12c5 0 9 4 9 9v24c0 5-4 9-9 9H26c-5 0-9-4-9-9V25Z" strokeWidth="4" />
              <path d="M17 32h-3c-2.2 0-4 1.8-4 4v10c0 2.2 1.8 4 4 4h3" strokeWidth="4" />
              <path d="M47 32h3c2.2 0 4 1.8 4 4v10c0 2.2-1.8 4-4 4h-3" strokeWidth="4" />
              <path d="M24 38h16v9c0 3.3-2.7 6-6 6h-4c-3.3 0-6-2.7-6-6v-9Z" strokeWidth="4" />
              <path d="M25 28h14" strokeWidth="4" />
            </svg>
          </span>
          <h1 className="text-4xl font-bold tracking-tight">TripLog</h1>
          <p className="text-gray-500 text-center leading-relaxed">
            친구들과 여행을 계획하고,<br />여행 중 순간을 기록해보세요.
          </p>
        </div>
        <div className="auth-landing-actions flex flex-col gap-3">
          <button
            onClick={() => changeMode("login", "forward")}
            className="w-full py-4 rounded-2xl bg-blue-500 text-white font-semibold text-base active:opacity-80"
          >
            로그인하기
          </button>
          <button
            onClick={() => changeMode("signup", "forward")}
            className="w-full py-4 rounded-2xl bg-gray-100 text-gray-800 font-semibold text-base active:opacity-80"
          >
            회원가입
          </button>
        </div>
      </div>
    );
  }

  // ── 로그인 ────────────────────────────────────────────────────────────────────
  if (mode === "login") {
    return (
      <div className={`auth-screen-transition auth-${authTransition} relative flex flex-col min-h-screen px-6 overflow-hidden`}>
        <Toast message={toast.message} visible={toast.visible} />

        {loginAnim && (
          <>
            <style>{`
              @keyframes circularReveal {
                from { clip-path: circle(0% at 50% 60%); }
                to   { clip-path: circle(160% at 50% 60%); }
              }
              @keyframes waveHand {
                0%   { transform: rotate(0deg); }
                15%  { transform: rotate(-22deg); }
                35%  { transform: rotate(18deg); }
                55%  { transform: rotate(-12deg); }
                70%  { transform: rotate(7deg); }
                85%  { transform: rotate(-3deg); }
                100% { transform: rotate(0deg); }
              }
            `}</style>
            <div
              style={{
                position: "absolute",
                inset: 0,
                zIndex: 200,
                background: "linear-gradient(160deg, #60a5fa 0%, #3b82f6 40%, #4338ca 100%)",
                animation: "circularReveal 0.75s cubic-bezier(0.4, 0, 0.2, 1) forwards",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <p
                style={{
                  color: "white",
                  fontSize: "1.75rem",
                  fontWeight: 700,
                  letterSpacing: "-0.02em",
                  display: "flex",
                  alignItems: "center",
                  gap: "0.4rem",
                  opacity: welcomeVisible ? 1 : 0,
                  transform: welcomeVisible ? "translateY(0)" : "translateY(8px)",
                  transition: "opacity 0.5s ease, transform 0.5s ease",
                }}
              >
                환영합니다
                <span
                  style={{
                    display: "inline-block",
                    animation: welcomeVisible ? "waveHand 1.1s ease-in-out 0.1s both" : "none",
                    transformOrigin: "70% 80%",
                  }}
                >
                  👋
                </span>
              </p>
            </div>
          </>
        )}
        <div className="flex items-center gap-3 pt-14 pb-4 border-b border-gray-100">
          <button
            onClick={() => changeMode("landing", "back")}
            aria-label="뒤로가기"
            className="trip-header-icon-button w-10 h-10 rounded-full flex items-center justify-center"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-lg font-bold">로그인</h1>
        </div>

        <div className="flex flex-col gap-5 pt-8">
          <div>
            <label className="text-sm font-semibold mb-1.5 block">이메일</label>
            <input
              className="w-full p-3.5 bg-gray-100 rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-300"
              placeholder="이메일을 입력해주세요"
              type="email"
              autoComplete="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
            />
          </div>
          <div>
            <label className="text-sm font-semibold mb-1.5 block">비밀번호</label>
            <input
              className="w-full p-3.5 bg-gray-100 rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-300"
              placeholder="비밀번호를 입력해주세요"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              onKeyDown={e => e.key === "Enter" && handleLogin()}
            />
          </div>

          {error && <p className="text-sm text-red-500 font-semibold">{error}</p>}

          <button
            onClick={handleLogin}
            disabled={!email.trim() || !password.trim() || loading}
            className="w-full py-4 rounded-2xl bg-blue-500 text-white font-semibold text-base mt-2 disabled:opacity-40"
          >
            {loading ? "로그인 중..." : "로그인하기"}
          </button>

          <button
            onClick={() => changeMode("signup", "swap")}
            className="text-sm text-gray-500 text-center underline"
          >
            계정이 없으신가요? 회원가입
          </button>
        </div>
      </div>
    );
  }

  // ── 회원가입 ──────────────────────────────────────────────────────────────────
  return (
    <div className={`auth-screen-transition auth-${authTransition} relative flex flex-col min-h-screen px-6 overflow-hidden`}>
      <Toast message={toast.message} visible={toast.visible} />

      {loginAnim && (
        <>
          <style>{`
            @keyframes circularReveal {
              from { clip-path: circle(0% at 50% 60%); }
              to   { clip-path: circle(160% at 50% 60%); }
            }
            @keyframes popConfetti {
              0%   { transform: scale(1) rotate(0deg); }
              20%  { transform: scale(1.5) rotate(-15deg); }
              45%  { transform: scale(0.88) rotate(10deg); }
              70%  { transform: scale(1.2) rotate(-5deg); }
              100% { transform: scale(1) rotate(0deg); }
            }
          `}</style>
          <div
            style={{
              position: "absolute",
              inset: 0,
              zIndex: 200,
              background: "linear-gradient(160deg, #60a5fa 0%, #3b82f6 40%, #4338ca 100%)",
              animation: "circularReveal 0.75s cubic-bezier(0.4, 0, 0.2, 1) forwards",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <p
              style={{
                color: "white",
                fontSize: "1.75rem",
                fontWeight: 700,
                letterSpacing: "-0.02em",
                display: "flex",
                alignItems: "center",
                gap: "0.4rem",
                opacity: welcomeVisible ? 1 : 0,
                transform: welcomeVisible ? "translateY(0)" : "translateY(8px)",
                transition: "opacity 0.5s ease, transform 0.5s ease",
              }}
            >
              {isSignupReveal ? "가입을 축하해요" : "환영합니다"}
              <span
                style={{
                  display: "inline-block",
                  animation: welcomeVisible
                    ? isSignupReveal
                      ? "popConfetti 0.8s ease-in-out 0.1s both"
                      : "none"
                    : "none",
                }}
              >
                {isSignupReveal ? "🎉" : "👋"}
              </span>
            </p>
          </div>
        </>
      )}

      <div className="flex items-center gap-3 pt-14 pb-4 border-b border-gray-100">
        <button
          onClick={() => changeMode("landing", "back")}
          aria-label="뒤로가기"
          className="trip-header-icon-button w-10 h-10 rounded-full flex items-center justify-center"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 className="text-lg font-bold">회원가입</h1>
      </div>

      <div className="flex flex-col gap-5 pt-8">
        <div>
          <label className="text-sm font-semibold mb-1.5 block">이메일</label>
          <input
            className="w-full p-3.5 bg-gray-100 rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-300"
            placeholder="이메일을 입력해주세요"
            type="email"
            autoComplete="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
          />
        </div>
        <div>
          <label className="text-sm font-semibold mb-1.5 block">비밀번호</label>
          <input
            className="w-full p-3.5 bg-gray-100 rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-300"
            placeholder="비밀번호를 입력해주세요"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={e => setPassword(e.target.value)}
          />
        </div>
        <div>
          <label className="text-sm font-semibold mb-1.5 block">비밀번호 확인</label>
          <input
            className={`w-full p-3.5 bg-gray-100 rounded-xl text-sm outline-none focus:ring-2 ${
              passwordConfirm && password !== passwordConfirm
                ? "focus:ring-red-300 ring-2 ring-red-300"
                : "focus:ring-blue-300"
            }`}
            placeholder="비밀번호를 다시 입력해주세요"
            type="password"
            autoComplete="new-password"
            value={passwordConfirm}
            onChange={e => setPasswordConfirm(e.target.value)}
          />
          {passwordConfirm && password !== passwordConfirm && (
            <p className="text-xs text-red-500 mt-1.5 font-medium">비밀번호가 일치하지 않아요.</p>
          )}
        </div>
        <div>
          <label className="text-sm font-semibold mb-1.5 block">이름</label>
          <input
            className="w-full p-3.5 bg-gray-100 rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-300"
            placeholder="이름을 입력해주세요"
            value={name}
            onChange={e => setName(e.target.value)}
          />
        </div>

        {error && <p className="text-sm text-red-500 font-semibold">{error}</p>}

        <button
          onClick={handleSignup}
          disabled={!email.trim() || !password.trim() || !name.trim() || password !== passwordConfirm || loading}
          className="w-full py-4 rounded-2xl bg-blue-500 text-white font-semibold text-base mt-2 disabled:opacity-40"
        >
          {loading ? "가입 중..." : "가입 완료"}
        </button>

        <button
          onClick={() => changeMode("login", "back")}
          className="text-sm text-gray-500 text-center underline"
        >
          이미 계정이 있으신가요? 로그인
        </button>
      </div>
    </div>
  );
}
