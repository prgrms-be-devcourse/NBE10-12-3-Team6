"use client";

import { useEffect, useRef } from "react";
import { apiFetch, API_BASE } from "../lib";
import { useStore } from "../store";

const RECONNECT_DELAY_MS = 2000;

// 로그인된 상태에서 서버에 SSE 연결을 유지해 online 상태를 마킹한다.
// 서버는 Redis에 presence(60s TTL)를 저장하고 30s 마다 하트비트 ping을 보내 TTL을 갱신한다.
// 인증 여부는 TripLogProvider가 이미 확인한 store의 isLoggedIn을 그대로 따른다 — 여기서 별도로
// /auth/me를 다시 호출하면 페이지 로드마다 같은 엔드포인트가 두 번 호출되는 중복이 생긴다.
export default function PresenceHeartbeat() {
  const { isLoggedIn } = useStore();
  const closedRef = useRef(false);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!isLoggedIn) return;
    closedRef.current = false;

    const connect = async () => {
      if (closedRef.current) return;

      const controller = new AbortController();
      controllerRef.current = controller;
      const userId = typeof window !== "undefined" ? localStorage.getItem("userId") : null;
      const userName = typeof window !== "undefined" ? localStorage.getItem("userName") : null;
      try {
        const response = await apiFetch(`${API_BASE}/api/v1/presence/sse`, {
          headers: { Accept: "text/event-stream" },
          signal: controller.signal,
        });
        if (response.status === 401 || response.status === 403) {
          console.warn("[presence] 인증 만료 — SSE 재연결 중단, 재로그인 필요");
          return;
        }
        if (!response.ok || !response.body) throw new Error("presence SSE 연결 실패");

        console.log(`[presence] SSE 연결됨 userId=${userId} name=${userName}`);
        const reader = response.body.getReader();
        while (!closedRef.current) {
          const { done } = await reader.read();
          if (done) break;
        }
        console.log(`[presence] SSE 스트림 종료 userId=${userId} name=${userName}`);
      } catch (error) {
        if (!closedRef.current && !controller.signal.aborted) {
          console.debug("[presence SSE 재연결]", error);
        }
      }

      // 네트워크 문제 등으로 스트림이 끊긴 경우에만 짧게 재시도한다.
      // 로그아웃은 항상 하드 네비게이션(window.location.replace)을 동반해 컴포넌트가 통째로
      // 언마운트되므로, 로그인 상태 변화 자체는 이 effect의 isLoggedIn 의존성으로 충분히 처리된다.
      if (!closedRef.current) {
        reconnectTimer.current = setTimeout(connect, RECONNECT_DELAY_MS);
      }
    };

    connect();

    return () => {
      closedRef.current = true;
      controllerRef.current?.abort();
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current);
    };
  }, [isLoggedIn]);

  return null;
}
