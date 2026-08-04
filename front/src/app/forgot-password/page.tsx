"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { API_BASE } from "../lib";

// 비로그인 상태 비밀번호 재설정 페이지 (recovery code 기반, 단일 페이지 내 상태 전환).
//
// 흐름:
//   1) "verify" 상태 — 이메일 + 회원가입 시 받은 6자리 recovery code 입력 → 서버가 조합 검증
//   2) 검증 OK 시 서버가 짧은 유효기간의 verificationToken을 발급 → 프론트는 이를 메모리에 보관하고
//      같은 페이지 안에서 "reset" 상태로 전환 (URL/history에 토큰 노출 없음)
//   3) "reset" 상태 — 새 비밀번호 + 확인 입력 → verificationToken과 함께 apply 요청
//   4) 성공 시 "done" 상태로 전환 후 로그인 페이지로 안내
//
// 페이지 벗어나거나 새로고침 시 verificationToken이 날아가는 게 의도된 동작 —
// 그 경우 verify 단계부터 다시 진행 (보안상 안전).

type Stage = "verify" | "reset" | "done";

// 프론트 레벨 최대 길이 상한 (붙여넣기 폭주 방지) — 실제 유효 길이는 서버가 판정
// 코드는 6자리이지만 사용자가 공백/하이픈 포함해 붙여넣을 수 있으니 여유 크게 잡음
const CODE_MAX_LENGTH = 16;

export default function ForgotPasswordPage() {
  const router = useRouter();
  const [stage, setStage] = useState<Stage>("verify");

  // verify 단계 입력
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");

  // verify 성공 후 서버가 발급한 짧은 유효기간 토큰 — reset 단계에서만 사용, 메모리에만 보관
  const [verificationToken, setVerificationToken] = useState("");

  // reset 단계 입력
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  // stage가 바뀔 때마다 에러 초기화 — 이전 단계의 잔여 메시지가 다음 화면에 남지 않도록
  useEffect(() => {
    setError("");
  }, [stage]);

  const handleVerify = async () => {
    if (!email.trim() || !code.trim()) return;
    setLoading(true);
    setError("");
    try {
      // 사용자가 공백/하이픈 포함해 붙여넣었을 수 있으니 대문자화 + 영숫자만 남김
      const normalizedCode = code.toUpperCase().replace(/[^0-9A-Z]/g, "");

      const res = await fetch(`${API_BASE}/api/v1/auth/password-reset/verify-code`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ email: email.trim(), code: normalizedCode }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        // 계정 열거 방지 — 서버가 (이메일 미존재 / 코드 불일치)를 통합 메시지로 반환할 것을 가정
        throw new Error(body?.message ?? "이메일 또는 코드가 올바르지 않아요.");
      }
      const body = await res.json().catch(() => ({}));
      // 서버 응답 형식: ResponseData 래퍼 { statusCode, data: { verificationToken } }
      const token: string | undefined = body?.data?.verificationToken;
      if (!token) {
        throw new Error("서버 응답이 올바르지 않아요.");
      }
      setVerificationToken(token);
      setStage("reset");
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "요청 처리에 실패했어요.");
    } finally {
      setLoading(false);
    }
  };

  const handleApply = async () => {
    if (!newPassword.trim()) return;
    if (newPassword !== confirmPassword) {
      setError("비밀번호가 일치하지 않아요.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/api/v1/auth/password-reset/apply`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ verificationToken, newPassword }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        // 검증 토큰이 만료/무효면 verify 단계로 되돌려 재시도 유도 (입력값도 함께 초기화)
        if (res.status === 400) {
          setError(body?.message ?? "검증이 만료되었어요. 처음부터 다시 진행해 주세요.");
          setStage("verify");
          setVerificationToken("");
          setNewPassword("");
          setConfirmPassword("");
          return;
        }
        throw new Error(body?.message ?? "비밀번호 변경에 실패했어요.");
      }
      setStage("done");
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "요청 처리에 실패했어요.");
    } finally {
      setLoading(false);
    }
  };

  // ── done 화면 ──────────────────────────────────────────────────────────────
  if (stage === "done") {
    return (
      <div className="relative flex min-h-[100dvh] flex-col overflow-hidden px-6">
        <div className="pt-8">
          <h1 className="text-lg font-bold">비밀번호가 변경됐어요</h1>
        </div>
        <div className="flex flex-col gap-5 pt-8">
          <p className="text-sm text-gray-600 leading-relaxed">
            새 비밀번호로 다시 로그인해 주세요.
            <br />
            보안을 위해 기존에 로그인되어 있던 모든 기기가 자동으로 로그아웃됩니다.
          </p>
          <button
            onClick={() => router.replace("/")}
            className="w-full py-4 rounded-2xl bg-blue-500 text-white font-semibold text-base mt-2"
          >
            로그인하러 가기
          </button>
        </div>
      </div>
    );
  }

  // ── reset 화면 (새 비밀번호 입력) ─────────────────────────────────────────
  if (stage === "reset") {
    return (
      <div className="relative flex min-h-[100dvh] flex-col overflow-hidden px-6">
        <div className="pt-8">
          <h1 className="text-lg font-bold">새 비밀번호 설정</h1>
          <p className="text-sm text-gray-500 mt-2">사용할 새 비밀번호를 입력해 주세요.</p>
        </div>

        <div className="flex flex-col gap-5 pt-8">
          <div>
            <label className="text-sm font-semibold mb-1.5 block">새 비밀번호</label>
            <input
              className="w-full p-3.5 bg-gray-100 rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-300"
              placeholder="새 비밀번호"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
            />
          </div>
          <div>
            <label className="text-sm font-semibold mb-1.5 block">새 비밀번호 확인</label>
            <input
              className="w-full p-3.5 bg-gray-100 rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-300"
              placeholder="다시 한 번 입력"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              // 엔터로 제출 — 기존 페이지들과 동일 UX
              onKeyDown={e => e.key === "Enter" && handleApply()}
            />
          </div>

          {error && <p className="text-sm text-red-500 font-semibold">{error}</p>}

          <button
            onClick={handleApply}
            disabled={!newPassword.trim() || !confirmPassword.trim() || loading}
            className="w-full py-4 rounded-2xl bg-blue-500 text-white font-semibold text-base mt-2 disabled:opacity-40"
          >
            {loading ? "변경 중..." : "비밀번호 변경"}
          </button>
        </div>
      </div>
    );
  }

  // ── verify 화면 (이메일 + 코드 입력) ──────────────────────────────────────
  return (
    <div className="relative flex min-h-[100dvh] flex-col overflow-hidden px-6">
      <div className="pt-8">
        <h1 className="text-lg font-bold">비밀번호 재설정</h1>
        <p className="text-sm text-gray-500 mt-2">
          가입 시 사용한 이메일과 발급받은 본인 확인 코드를 입력해 주세요.
        </p>
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
          <label className="text-sm font-semibold mb-1.5 block">본인 확인 코드 (6자리)</label>
          <input
            // autoComplete="one-time-code": 모바일 자동 채우기 힌트
            // maxLength: 붙여넣기 폭주 방지 (실제 유효 길이 검증은 서버)
            // tracking-widest + uppercase: 문자 간격 넓혀 가독성 향상
            className="w-full p-3.5 bg-gray-100 rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-300 tracking-widest uppercase"
            placeholder="회원가입 시 발급받은 코드"
            type="text"
            autoComplete="one-time-code"
            maxLength={CODE_MAX_LENGTH}
            value={code}
            onChange={e => setCode(e.target.value)}
            onKeyDown={e => e.key === "Enter" && handleVerify()}
          />
          <p className="text-xs text-gray-400 mt-1.5">
            회원가입 시 저장해두신 본인만의 6자리 코드예요 (대문자·숫자).
          </p>
        </div>

        {error && <p className="text-sm text-red-500 font-semibold">{error}</p>}

        <button
          onClick={handleVerify}
          disabled={!email.trim() || !code.trim() || loading}
          className="w-full py-4 rounded-2xl bg-blue-500 text-white font-semibold text-base mt-2 disabled:opacity-40"
        >
          {loading ? "확인 중..." : "다음"}
        </button>

        <button
          onClick={() => router.replace("/")}
          className="text-sm text-gray-500 text-center underline"
        >
          로그인으로 돌아가기
        </button>
      </div>
    </div>
  );
}
