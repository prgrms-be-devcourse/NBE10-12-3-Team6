"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useStore } from "./store";
import { API_BASE, apiFetch } from "./lib";
import {
  clearStoredAuthentication,
  hasStoredAuthentication,
  rememberCookieAuthentication,
} from "./authStorage";

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
  // 429 락아웃 응답 처리용 — 서버가 내려준 retryAfterSeconds를 담고 매초 감소
  const [lockoutSecondsLeft, setLockoutSecondsLeft] = useState(0);
  // 401 응답의 remainingAttempts 힌트 — 실제 회원의 비번 오류일 때만 채워짐 (미존재 이메일은 null)
  const [remainingAttempts, setRemainingAttempts] = useState<number | null>(null);
  // 회원가입 성공 응답의 recoveryCode — 세팅되면 별도 안내 화면 오버레이 표시.
  // 서버는 이 코드를 딱 이 응답에서만 노출하고 이후엔 해시만 보관하므로 유저가 반드시 저장해야 함.
  const [signupRecoveryCode, setSignupRecoveryCode] = useState<string | null>(null);
  // 코드가 실제 값으로 노출됐는지 여부 — 기본은 마스킹, 눈 아이콘 누르면 노출.
  //   피드백: "로그인 비번처럼 사용자가 한 번 확인하는 동작을 거치도록" — 어깨너머 관찰 방지
  const [isRecoveryCodeRevealed, setIsRecoveryCodeRevealed] = useState(false);
  // 복사 버튼 상태 — "idle" | "copied" | "error". 복사 성공/실패 시 시각적 피드백 (2초 후 idle 복귀)
  const [copyStatus, setCopyStatus] = useState<"idle" | "copied" | "error">("idle");
  const [emailCodeSent, setEmailCodeSent] = useState(false);
  const [emailVerified, setEmailVerified] = useState(false);
  const [verificationCode, setVerificationCode] = useState("");
  const [verificationSecondsLeft, setVerificationSecondsLeft] = useState(0);
  const [sendingCode, setSendingCode] = useState(false);
  const [verifyingCode, setVerifyingCode] = useState(false);
  const [emailAuthError, setEmailAuthError] = useState("");
  const [toast, setToast] = useState<{ message: string; visible: boolean }>({ message: "", visible: false });
  const [loginAnim, setLoginAnim] = useState(false);
  const [welcomeVisible, setWelcomeVisible] = useState(false);
  const [isSignupReveal, setIsSignupReveal] = useState(false);
  const [authTransition, setAuthTransition] = useState<AuthTransition>("forward");
  const [landingVisible, setLandingVisible] = useState(false);
  const [restoringSession, setRestoringSession] = useState(true);

  const resetEmailVerification = () => {
    setEmailCodeSent(false);
    setEmailVerified(false);
    setVerificationCode("");
    setVerificationSecondsLeft(0);
    setEmailAuthError("");
  };

  const changeMode = (nextMode: Mode, transition: AuthTransition = "forward") => {
    if (nextMode === "landing") setLandingVisible(false);
    setAuthTransition(transition);
    setMode(nextMode);
    setError("");
    // 모드 전환 시 락아웃/실패 힌트도 함께 초기화 — 이전 화면 상태가 다음 폼에 남는 것 방지
    setLockoutSecondsLeft(0);
    setRemainingAttempts(null);
    setEmail(""); setPassword(""); setPasswordConfirm(""); setName("");
    resetEmailVerification();
  };

  useEffect(() => {
    let cancelled = false;

    const restoreSession = async () => {
      const params = new URLSearchParams(window.location.search);
      if (params.get("oauth") === "success" || !hasStoredAuthentication()) {
        if (!cancelled) setRestoringSession(false);
        return;
      }

      try {
        const res = await apiFetch(`${API_BASE}/api/v1/auth/me`);
        if (!res.ok) {
          if (!cancelled) setRestoringSession(false);
          return;
        }

        const body = await res.json().catch(() => ({}));
        const memberId = Number(body.data?.id);
        const memberName = typeof body.data?.name === "string" ? body.data.name : "";

        if (!Number.isFinite(memberId) || memberId <= 0) {
          clearStoredAuthentication();
          if (!cancelled) setRestoringSession(false);
          return;
        }

        if (cancelled) return;

        rememberCookieAuthentication();
        login(memberName, memberId);
        router.replace("/home");
      } catch {
        if (!cancelled) setRestoringSession(false);
      }
    };

    void restoreSession();

    return () => {
      cancelled = true;
    };
  }, [login, router]);

  useEffect(() => {
    if (verificationSecondsLeft <= 0) return;
    const id = setInterval(() => {
      setVerificationSecondsLeft(prev => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(id);
  }, [verificationSecondsLeft]);

  // 락아웃 카운트다운 — 429 응답 시 lockoutSecondsLeft를 서버 값으로 초기화한 뒤 매초 감소
  // 0에 도달하면 재로그인 버튼이 다시 활성화됨 (아래 로그인 버튼 disabled 조건 참고)
  useEffect(() => {
    if (lockoutSecondsLeft <= 0) return;
    const id = setInterval(() => {
      setLockoutSecondsLeft(prev => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(id);
  }, [lockoutSecondsLeft]);

  const isValidEmailFormat = (value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());

  const formatCountdown = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
  };

  const resendCooldownSeconds = emailCodeSent
    ? Math.max(0, verificationSecondsLeft - 270)
    : 0;

  const handleSendEmailCode = async () => {
    if (!isValidEmailFormat(email) || sendingCode || emailVerified) return;
    setSendingCode(true);
    setEmailAuthError("");
    try {
      const res = await fetch(`${API_BASE}/api/v1/auth/check_email`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ email: email.trim() }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body?.message ?? "인증 코드 발송에 실패했어요.");
      }
      setEmailCodeSent(true);
      setVerificationCode("");
      setVerificationSecondsLeft(300);
    } catch (e: unknown) {
      setEmailAuthError(e instanceof Error ? e.message : "인증 코드 발송에 실패했어요.");
    } finally {
      setSendingCode(false);
    }
  };

  const handleVerifyEmailCode = async () => {
    if (verificationCode.length !== 6 || verifyingCode || verificationSecondsLeft === 0) return;
    setVerifyingCode(true);
    setEmailAuthError("");
    try {
      const res = await fetch(`${API_BASE}/api/v1/auth/verify_email`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ email: email.trim(), code: verificationCode }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body?.message ?? "인증에 실패했어요.");
      }
      setEmailVerified(true);
      setVerificationSecondsLeft(0);
    } catch (e: unknown) {
      setEmailAuthError(e instanceof Error ? e.message : "인증에 실패했어요.");
    } finally {
      setVerifyingCode(false);
    }
  };

  useEffect(() => {
    if (mode !== "landing") return;

    setLandingVisible(false);
    const firstFrame = requestAnimationFrame(() => {
      requestAnimationFrame(() => setLandingVisible(true));
    });

    return () => cancelAnimationFrame(firstFrame);
  }, [mode]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get("oauth") !== "success") return;

    let cancelled = false;
    let welcomeTimer: ReturnType<typeof setTimeout> | undefined;
    let homeTimer: ReturnType<typeof setTimeout> | undefined;

    const completeKakaoLogin = async () => {
      try {
        const res = await fetch(`${API_BASE}/api/v1/auth/me`, {
          credentials: "include",
        });
        if (!res.ok) {
          throw new Error("카카오 로그인 정보를 확인하지 못했습니다.");
        }

        const body = await res.json();
        if (cancelled) return;

        window.history.replaceState({}, "", window.location.pathname);
        rememberCookieAuthentication();
        login(body.data?.name, body.data?.id);
        setAuthTransition("forward");
        setMode("login");
        setLoginAnim(true);
        welcomeTimer = setTimeout(() => setWelcomeVisible(true), 500);
        homeTimer = setTimeout(() => router.replace("/home"), 2200);
      } catch (e: unknown) {
        if (cancelled) return;
        window.history.replaceState({}, "", window.location.pathname);
        clearStoredAuthentication();
        setAuthTransition("forward");
        setMode("login");
        setError(e instanceof Error ? e.message : "카카오 로그인에 실패했습니다.");
      }
    };

    void completeKakaoLogin();

    return () => {
      cancelled = true;
      if (welcomeTimer) clearTimeout(welcomeTimer);
      if (homeTimer) clearTimeout(homeTimer);
    };
  }, [login, router]);

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
        // 429 락아웃 — retryAfterSeconds로 카운트다운 상태만 세팅하고 종료 (별도 UI에서 초 단위 렌더)
        if (res.status === 429 && typeof body?.retryAfterSeconds === "number") {
          setLockoutSecondsLeft(body.retryAfterSeconds);
          setRemainingAttempts(null);
          setError("");
          return;
        }
        // 401 자격증명 오류 — 실제 회원이면 remainingAttempts로 남은 시도 힌트 세팅
        //   (미존재 이메일 케이스는 remainingAttempts가 응답에 없어서 null 유지 → 힌트 미노출)
        setRemainingAttempts(typeof body?.remainingAttempts === "number" ? body.remainingAttempts : null);
        setError(body?.message ?? "이메일 또는 비밀번호가 올바르지 않아요.");
        return;
      }
      // 성공 경로 진입 — 락아웃/힌트 상태 리셋
      setLockoutSecondsLeft(0);
      setRemainingAttempts(null);
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

  const handleKakaoLogin = () => {
    window.location.assign(`${API_BASE}/oauth2/authorization/kakao`);
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
      // 서버 응답에서 raw recovery code 추출 (백엔드 MemberResponseDto.fromSignup 결과) —
      // 이후 서버는 이 코드를 다시 알려줄 수 없으므로, 안내 화면으로 유저에게 반드시 노출
      const signupBody = await res.json().catch(() => ({}));
      const recoveryCode: string | undefined = signupBody?.data?.recoveryCode;

      // 폼 값 초기화 (안내 화면 뒤에 로그인 화면으로 넘어갈 때 잔여 입력이 남지 않도록)
      setEmail("");
      setPassword("");
      setPasswordConfirm("");
      setName("");

      if (recoveryCode) {
        // 안내 화면(recovery code overlay)으로 전환 — 유저가 "확인" 누르기 전까지 로그인 화면 이동 지연
        setSignupRecoveryCode(recoveryCode);
      } else {
        // 이론상 도달 안 하지만(백엔드가 항상 코드 포함), fallback으로 기존 애니메이션 유지
        setIsSignupReveal(true);
        setLoginAnim(true);
        setTimeout(() => setWelcomeVisible(true), 500);
        setTimeout(() => {
          setLoginAnim(false);
          setWelcomeVisible(false);
          setIsSignupReveal(false);
          changeMode("login", "back");
        }, 2200);
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "회원가입에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  if (restoringSession) {
    return (
      <div
        className="flex min-h-[100dvh] items-center justify-center"
        role="status"
        aria-label="로그인 상태 확인 중"
      >
        <span className="h-8 w-8 animate-spin rounded-full border-2 border-gray-200 border-t-blue-500" />
      </div>
    );
  }

  // ── 회원가입 완료 후 recovery code 안내 (최우선 렌더) ─────────────────────────
  // signupRecoveryCode가 세팅되어 있는 동안엔 다른 어떤 mode보다 이 화면이 앞섬 —
  // 유저가 코드를 확실히 확인/저장하기 전에는 다른 화면 접근 불가
  if (signupRecoveryCode) {
    // 6자리 코드를 3자씩 나눠 표시 (가독성 향상: "A3F 9K2")
    const displayCode = signupRecoveryCode.length === 6
      ? `${signupRecoveryCode.slice(0, 3)} ${signupRecoveryCode.slice(3)}`
      : signupRecoveryCode;
    // 마스킹: 원본 길이만큼 dot 문자로 대체 (공백 위치도 유지해 형태 자체는 노출되게 = 사용자가 자릿수 감 잡음)
    // "●"(U+25CF) 사용 — 시각적으로 비번 입력창의 dot과 유사
    const maskedCode = displayCode.replace(/[^\s]/g, "●");

    // 클립보드 복사 — HTTPS/localhost 환경에서만 navigator.clipboard 사용 가능
    const handleCopy = async () => {
      try {
        await navigator.clipboard.writeText(signupRecoveryCode);
        setCopyStatus("copied");
      } catch {
        // secure context가 아니거나 브라우저가 permission 거부한 경우
        setCopyStatus("error");
      }
      // 2초 후 상태 복귀 — 사용자가 재복사 시도 가능하게
      setTimeout(() => setCopyStatus("idle"), 2000);
    };

    return (
      <div className="relative flex min-h-[100dvh] flex-col overflow-hidden px-6">
        <div className="pt-8">
          <h1 className="text-lg font-bold">회원가입이 완료됐어요</h1>
          <p className="text-sm text-gray-500 mt-2">
            비밀번호를 잊었을 때 계정을 되찾기 위한 <strong>본인 확인 코드</strong>가 발급됐어요.
          </p>
        </div>

        <div className="flex flex-col gap-6 pt-8">
          {/* 코드 박스 — 기본은 마스킹, 눈 아이콘으로 노출 토글 + 복사 버튼 */}
          <div className="bg-blue-50 border-2 border-blue-300 rounded-2xl p-6 text-center">
            <p className="text-xs text-gray-500 mb-2">본인 확인 코드</p>
            <div className="flex items-center justify-center gap-3">
              {/* select-all: 클릭 시 전체 선택되어 수동 복사도 편함. 마스킹 상태여도 실제 값은 텍스트라 select 가능 */}
              <p className="text-3xl font-bold tracking-widest text-blue-600 tabular-nums select-all">
                {isRecoveryCodeRevealed ? displayCode : maskedCode}
              </p>
              {/* 눈 아이콘 — Heroicons 스타일 SVG (기존 페이지들 SVG 규격과 통일) */}
              <button
                type="button"
                onClick={() => setIsRecoveryCodeRevealed(prev => !prev)}
                aria-label={isRecoveryCodeRevealed ? "코드 가리기" : "코드 보기"}
                className="text-blue-600 hover:text-blue-800 p-1"
              >
                {isRecoveryCodeRevealed ? (
                  // eye-slash (가리기)
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24" />
                    <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68" />
                    <path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61" />
                    <line x1="2" y1="2" x2="22" y2="22" />
                  </svg>
                ) : (
                  // eye (보기)
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
                    <circle cx="12" cy="12" r="3" />
                  </svg>
                )}
              </button>
            </div>

            {/* 복사 버튼 — 아이콘 + 상태 텍스트 조합 (idle/copied/error) */}
            <button
              type="button"
              onClick={handleCopy}
              className={`mt-3 inline-flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-full border transition-colors ${
                copyStatus === "copied"
                  ? "bg-green-50 border-green-300 text-green-700"
                  : copyStatus === "error"
                    ? "bg-red-50 border-red-300 text-red-700"
                    : "bg-white border-blue-300 text-blue-600 hover:bg-blue-50"
              }`}
              aria-live="polite"
            >
              {copyStatus === "copied" ? (
                <>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                  복사됐어요
                </>
              ) : copyStatus === "error" ? (
                <>복사 실패 (수동으로 선택해 주세요)</>
              ) : (
                <>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                  </svg>
                  복사하기
                </>
              )}
            </button>
          </div>

          {/* 경고: 캡쳐/메모 안내 + 재발급 불가 안내 */}
          <div className="bg-yellow-50 border border-yellow-200 rounded-xl p-4">
            <p className="text-sm font-semibold text-yellow-800 mb-2">⚠️ 반드시 이 화면에서 저장해 주세요</p>
            <ul className="text-xs text-yellow-700 leading-relaxed space-y-1 list-disc list-inside">
              <li>주위에 사람이 없는 곳에서 <strong>화면 캡쳐</strong>하거나 <strong>메모장에 기록</strong>해 두세요.</li>
              <li>이 코드는 지금 이후로는 다시 볼 수 없어요. 서버에도 원본은 저장되지 않아요.</li>
              <li>비밀번호를 잊었을 때 이 코드로만 계정을 되찾을 수 있어요.</li>
            </ul>
          </div>

          <button
            onClick={() => {
              // 안내 화면 종료 → 로그인 화면으로 이동
              // reveal / copyStatus 상태도 초기화 (다음 회원가입 세션에 leak 안 되게)
              setSignupRecoveryCode(null);
              setIsRecoveryCodeRevealed(false);
              setCopyStatus("idle");
              changeMode("login", "back");
            }}
            className="w-full py-4 rounded-2xl bg-blue-500 text-white font-semibold text-base mt-2"
          >
            저장했어요, 확인
          </button>
        </div>
      </div>
    );
  }

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
            type="button"
            onClick={handleKakaoLogin}
            className="relative w-full py-4 rounded-2xl bg-[#FEE500] text-[#191919] font-semibold text-base active:opacity-80"
          >
            <span className="absolute left-5 top-1/2 -translate-y-1/2 flex h-6 w-6 items-center justify-center" aria-hidden="true">
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor">
                <path d="M12 3C6.48 3 2 6.53 2 10.88c0 2.78 1.83 5.22 4.58 6.62l-1.17 4.08c-.11.38.32.68.65.46l4.93-3.3c.33.03.67.04 1.01.04 5.52 0 10-3.53 10-7.9S17.52 3 12 3Z" />
              </svg>
            </span>
            카카오로 로그인하기
          </button>
          <button
            type="button"
            onClick={() => changeMode("signup", "forward")}
            className="mt-1 text-center text-sm text-gray-500 underline active:opacity-70"
          >
            계정이 없으신가요? 회원가입
          </button>
        </div>
      </div>
    );
  }

  // ── 로그인 ────────────────────────────────────────────────────────────────────
  if (mode === "login") {
    return (
      <div className={`auth-screen-transition auth-${authTransition} relative flex min-h-[100dvh] flex-col overflow-hidden px-6`}>
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
        <div className="app-safe-header flex items-center gap-3 border-b border-gray-100 pb-4">
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
            {/* 라벨 우측에 남은 시도 힌트를 함께 배치 — 사용자가 비번 입력 지점에서 바로 확인 가능 */}
            <div className="flex items-center justify-between mb-1.5">
              <label className="text-sm font-semibold">비밀번호</label>
              {remainingAttempts !== null && remainingAttempts > 0 && lockoutSecondsLeft === 0 && (
                <span className="text-xs text-gray-500 tabular-nums">
                  남은 시도 {remainingAttempts}회 / 총 5회
                </span>
              )}
            </div>
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

          {/* 락아웃 중이면 카운트다운을 서버 메시지 대신 직접 렌더 (매초 갱신) */}
          {lockoutSecondsLeft > 0 ? (
            <p className="text-sm text-red-500 font-semibold">
              계정이 잠겼어요. <span className="tabular-nums">{lockoutSecondsLeft}</span>초 후 다시 시도해 주세요.
            </p>
          ) : error ? (
            <p className="text-sm text-red-500 font-semibold">{error}</p>
          ) : null}

          <button
            onClick={handleLogin}
            // 락아웃 중에는 카운트다운이 0에 도달할 때까지 재시도 차단
            disabled={!email.trim() || !password.trim() || loading || lockoutSecondsLeft > 0}
            className="w-full py-4 rounded-2xl bg-blue-500 text-white font-semibold text-base mt-2 disabled:opacity-40"
          >
            {loading ? "로그인 중..." : "로그인하기"}
          </button>

          {/* 비밀번호 재설정 진입점 — 회원가입 시 받은 recovery code로 비번을 새로 설정 */}
          <button
            onClick={() => router.push("/forgot-password")}
            className="text-sm text-gray-500 text-center underline"
          >
            비밀번호를 잊으셨나요?
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
    <div className={`auth-screen-transition auth-${authTransition} relative flex min-h-[100dvh] flex-col overflow-hidden px-6`}>
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

      <div className="app-safe-header flex items-center gap-3 border-b border-gray-100 pb-4">
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
          <div className="flex gap-2">
            <input
              className="flex-1 min-w-0 p-3.5 bg-gray-100 rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-300 disabled:opacity-70"
              placeholder="이메일을 입력해주세요"
              type="email"
              autoComplete="email"
              value={email}
              onChange={e => {
                setEmail(e.target.value);
                if (emailCodeSent || emailVerified) resetEmailVerification();
              }}
              disabled={emailVerified}
            />
            <button
              type="button"
              onClick={handleSendEmailCode}
              disabled={
                !isValidEmailFormat(email) ||
                emailVerified ||
                sendingCode ||
                resendCooldownSeconds > 0
              }
              className="shrink-0 w-24 rounded-xl bg-blue-500 text-white text-sm font-semibold whitespace-nowrap disabled:opacity-40"
            >
              {emailVerified
                ? "인증 완료"
                : sendingCode
                  ? "발송 중..."
                  : emailCodeSent
                    ? resendCooldownSeconds > 0
                      ? `재전송 ${resendCooldownSeconds}s`
                      : "재전송"
                    : "인증하기"}
            </button>
          </div>
          {emailCodeSent && !emailVerified && (
            <div className="mt-2">
              <div className="flex gap-2">
                <div className="flex-1 relative min-w-0">
                  <input
                    className="w-full p-3.5 pr-16 bg-gray-100 rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-300 tracking-widest"
                    placeholder="인증번호 6자리"
                    inputMode="numeric"
                    maxLength={6}
                    value={verificationCode}
                    onChange={e => setVerificationCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                    onKeyDown={e => e.key === "Enter" && handleVerifyEmailCode()}
                  />
                  <span
                    className={`absolute right-3 top-1/2 -translate-y-1/2 text-xs font-semibold tabular-nums ${
                      verificationSecondsLeft <= 30 ? "text-red-500" : "text-gray-500"
                    }`}
                  >
                    {formatCountdown(verificationSecondsLeft)}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={handleVerifyEmailCode}
                  disabled={verificationCode.length !== 6 || verifyingCode || verificationSecondsLeft === 0}
                  className="shrink-0 w-24 rounded-xl bg-gray-800 text-white text-sm font-semibold whitespace-nowrap disabled:opacity-40"
                >
                  {verifyingCode ? "확인 중..." : "확인"}
                </button>
              </div>
              {verificationSecondsLeft === 0 && !emailAuthError && (
                <p className="text-xs text-red-500 mt-1.5 font-medium">
                  인증 시간이 만료되었어요. 재전송을 눌러주세요.
                </p>
              )}
            </div>
          )}
          {emailVerified && (
            <p className="text-xs text-green-600 mt-2 font-medium flex items-center gap-1">
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
              </svg>
              이메일 인증이 완료되었어요.
            </p>
          )}
          {emailAuthError && !emailVerified && (
            <p className="text-xs text-red-500 mt-1.5 font-medium">{emailAuthError}</p>
          )}
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
          disabled={!email.trim() || !emailVerified || !password.trim() || !name.trim() || password !== passwordConfirm || loading}
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
