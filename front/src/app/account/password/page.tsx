"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuthGuard } from "../../lib";
import AccountHome from "../AccountHome";

export default function PasswordChangePage() {
  useAuthGuard();

  const router = useRouter();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newPasswordConfirm, setNewPasswordConfirm] = useState("");
  const [isLeaving, setIsLeaving] = useState(false);

  const passwordMismatch = Boolean(
    newPasswordConfirm && newPassword !== newPasswordConfirm,
  );
  const canSubmit = Boolean(
    currentPassword &&
      newPassword &&
      newPasswordConfirm &&
      !passwordMismatch,
  );

  useEffect(() => {
    router.prefetch("/account");
  }, [router]);

  const leaveToAccount = () => {
    if (isLeaving) return;

    setIsLeaving(true);
    const prefersReducedMotion = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    ).matches;
    window.setTimeout(
      () => router.replace("/account"),
      prefersReducedMotion ? 0 : 420,
    );
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canSubmit) return;

    // TODO: 비밀번호 변경 백엔드 API가 완성되면 이 위치에서 요청합니다.
    leaveToAccount();
  };

  return (
    <div className="account-password-stack">
      <div className="account-password-background" aria-hidden inert>
        <AccountHome navigationMode={isLeaving ? "returning" : "hidden"} />
      </div>
      <div className={`account-page account-password-page trip-page-transition ${isLeaving ? "trip-page-exit" : ""}`}>
        <header className="app-safe-header shrink-0 flex items-center gap-3 border-b border-gray-100 px-4 pb-4">
        <button
          type="button"
          onClick={leaveToAccount}
          disabled={isLeaving}
          aria-label="계정 화면으로 돌아가기"
          className="trip-header-icon-button flex h-10 w-10 shrink-0 items-center justify-center rounded-full"
        >
          <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="m15 19-7-7 7-7" />
          </svg>
        </button>
        <h1 className="text-lg font-bold">비밀번호 변경</h1>
        </header>

        <main className="account-password-content flex-1 overflow-y-auto px-4 pt-7">
        <form onSubmit={handleSubmit} className="flex min-h-full flex-col">
          <div className="flex flex-col gap-6">
            <div>
              <label htmlFor="current-password" className="mb-2 block text-sm font-semibold">
                현재 비밀번호
              </label>
              <input
                id="current-password"
                type="password"
                autoComplete="current-password"
                value={currentPassword}
                onChange={event => setCurrentPassword(event.target.value)}
                placeholder="현재 비밀번호를 입력해주세요"
                className="w-full rounded-xl bg-gray-100 p-4 text-base outline-none focus:ring-2 focus:ring-blue-300"
              />
            </div>

            <div>
              <label htmlFor="new-password" className="mb-2 block text-sm font-semibold">
                새로운 비밀번호
              </label>
              <input
                id="new-password"
                type="password"
                autoComplete="new-password"
                value={newPassword}
                onChange={event => setNewPassword(event.target.value)}
                placeholder="새로운 비밀번호를 입력해주세요"
                className="w-full rounded-xl bg-gray-100 p-4 text-base outline-none focus:ring-2 focus:ring-blue-300"
              />
            </div>

            <div>
              <label htmlFor="new-password-confirm" className="mb-2 block text-sm font-semibold">
                새로운 비밀번호 확인
              </label>
              <input
                id="new-password-confirm"
                type="password"
                autoComplete="new-password"
                value={newPasswordConfirm}
                onChange={event => setNewPasswordConfirm(event.target.value)}
                placeholder="새로운 비밀번호를 다시 입력해주세요"
                aria-invalid={passwordMismatch}
                aria-describedby={passwordMismatch ? "password-mismatch-message" : undefined}
                className={`w-full rounded-xl bg-gray-100 p-4 text-base outline-none focus:ring-2 ${
                  passwordMismatch
                    ? "ring-2 ring-red-300 focus:ring-red-300"
                    : "focus:ring-blue-300"
                }`}
              />
              {passwordMismatch && (
                <p id="password-mismatch-message" className="mt-2 text-xs font-medium text-red-500">
                  새로운 비밀번호가 일치하지 않습니다.
                </p>
              )}
            </div>
          </div>

          <button
            type="submit"
            disabled={!canSubmit}
            className="mb-[max(1.5rem,env(safe-area-inset-bottom))] mt-auto w-full rounded-2xl bg-blue-500 py-4 font-bold text-white disabled:opacity-40"
          >
            비밀번호 변경
          </button>
        </form>
        </main>
      </div>
    </div>
  );
}
