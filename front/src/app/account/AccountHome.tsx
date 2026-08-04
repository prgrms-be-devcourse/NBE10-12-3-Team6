"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import HomeBottomNavigation from "../components/HomeBottomNavigation";
import { API_BASE, apiFetch, useAuthGuard } from "../lib";
import { useStore } from "../store";

type AccountPayload = {
  id: number;
  email: string;
  name: string;
};

export default function AccountHome({
  navigationMode = "visible",
}: {
  navigationMode?: "visible" | "hidden" | "returning";
}) {
  useAuthGuard();

  const router = useRouter();
  const { currentUser } = useStore();
  const [account, setAccount] = useState<AccountPayload | null>(null);
  const [confirmLogout, setConfirmLogout] = useState(false);

  useEffect(() => {
    router.prefetch("/account/password");

    const controller = new AbortController();

    apiFetch(`${API_BASE}/api/v1/auth/me`, { signal: controller.signal })
      .then(async response => {
        const body = await response.json();
        if (!response.ok) throw new Error(body?.message ?? "계정 정보를 불러오지 못했습니다.");
        return body.data as AccountPayload;
      })
      .then(setAccount)
      .catch(error => {
        if (!controller.signal.aborted) {
          console.error("[계정 정보 조회 실패]", error);
        }
      });

    return () => controller.abort();
  }, [router]);

  const handleLogout = async () => {
    // 반드시 await: 서버가 Set-Cookie로 access/refresh 쿠키를 만료(maxAge=0)시키는 응답을 받고 나서
    // 이동해야 middleware가 "아직 인증 쿠키 있음"으로 오판하고 /home으로 튕기는 것을 막을 수 있음.
    try {
      await apiFetch(`${API_BASE}/api/v1/auth/logout`, { method: "POST" });
    } catch (error) {
      console.error("[로그아웃 API 실패]", error);
    }

    localStorage.clear();
    window.dispatchEvent(new Event("triplog-logout"));
    // router.replace 대신 하드 네비게이션 사용: 방금 만료시킨 쿠키 상태가 브라우저 저장소에 확실히 반영된
    // 뒤에 새 요청을 나가게 하기 위함(SPA 라우팅은 상황에 따라 쿠키 갱신 타이밍이 미묘하게 어긋날 수 있음).
    window.location.replace("/");
  };

  const displayName = account?.name || currentUser.name || "사용자";

  return (
    <div className="account-page">
      <header className="app-safe-header shrink-0 px-4 pb-5">
        <h1 className="text-3xl font-bold">계정</h1>
      </header>

      <main className="settings-list-scroll px-4">
        <section>
          <h2 className="mb-3 font-bold">현재 계정</h2>
          <div className="flex items-center gap-4 rounded-2xl border border-gray-100 bg-white p-4">
            <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-blue-100 text-blue-600">
              <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.75 7.75a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.75 19.25a7.25 7.25 0 0 1 14.5 0" />
              </svg>
            </span>
            <div className="min-w-0">
              <p className="truncate font-bold">{displayName}</p>
              {account?.email && (
                <p className="mt-1 truncate text-sm text-gray-500">{account.email}</p>
              )}
            </div>
          </div>
        </section>

        <section className="mt-6 flex flex-col gap-3">
          <button
            type="button"
            onClick={() => router.push("/account/password")}
            className="flex w-full items-center gap-3 rounded-2xl border border-gray-100 bg-white p-4 text-left active:opacity-80"
          >
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gray-100 text-gray-500">
              <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <rect x="5" y="10" width="14" height="10" rx="2" strokeWidth={2} />
                <path strokeLinecap="round" strokeWidth={2} d="M8 10V7a4 4 0 0 1 8 0v3" />
              </svg>
            </span>
            <span className="flex-1 font-semibold">비밀번호 변경</span>
            <svg className="h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="m9 5 7 7-7 7" />
            </svg>
          </button>

          <div className={`account-logout-panel rounded-2xl border border-red-100 bg-white ${
            confirmLogout ? "is-confirming" : ""
          }`}>
            <button
              type="button"
              onClick={() => {
                if (!confirmLogout) setConfirmLogout(true);
              }}
              aria-expanded={confirmLogout}
              className="account-logout-summary flex w-full items-center gap-3 p-4 text-left text-red-500"
            >
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-red-50">
                <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 5H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h4m5-4 4-4m0 0-4-4m4 4H9" />
                </svg>
              </span>
              <span className="account-logout-labels relative h-6 min-w-0 flex-1">
                <span className="account-logout-default-label absolute inset-0 flex items-center font-semibold">
                  로그아웃
                </span>
                <span className="account-logout-confirm-label absolute inset-0 flex items-center font-bold">
                  정말 로그아웃하시겠습니까?
                </span>
              </span>
            </button>

            <div className="account-logout-actions-reveal">
              <div className="account-logout-actions-inner">
                <div className="flex gap-3 px-4 pb-4">
                  <button
                    type="button"
                    onClick={() => setConfirmLogout(false)}
                    className="flex-1 rounded-xl bg-gray-100 py-3.5 font-bold text-gray-700 active:opacity-80"
                  >
                    취소
                  </button>
                  <button
                    type="button"
                    onClick={handleLogout}
                    className="flex-1 rounded-xl bg-red-500 py-3.5 font-bold text-white active:opacity-80"
                  >
                    로그아웃
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>

      {navigationMode !== "hidden" && (
        <HomeBottomNavigation
          activeTab="profile"
          isReturning={navigationMode === "returning"}
        />
      )}
    </div>
  );
}
