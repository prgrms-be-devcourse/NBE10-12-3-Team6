"use client";

import { useEffect } from "react";
import { useRouter, useParams } from "next/navigation";
import { useStore } from "../../store";
import { apiFetch, API_BASE } from "../../lib";

async function joinTripByInviteCode(code: string) {
  await apiFetch(`${API_BASE}/api/v1/trips/member/${code}`, {
    method: "POST",
  });
}

export default function InviteLandingPage() {
  const router = useRouter();
  const { code } = useParams<{ code: string }>();
  const { isLoggedIn } = useStore();

  useEffect(() => {
    if (!code) return;
    //혹시라도 로그인이 실패 할 수 있어서 우선 코드로 입장되면 로컬 스토리지에 저장
    localStorage.setItem("pendingInviteCode", code);
    if (isLoggedIn) {
      joinTripByInviteCode(code).finally(() => router.replace("/home"));
    } else {
      router.replace("/");
    }
  }, [code, router, isLoggedIn]);

  return (
    <div className="flex items-center justify-center min-h-screen">
      <p className="text-gray-400 text-sm">초대 링크 확인 중...</p>
    </div>
  );
}
