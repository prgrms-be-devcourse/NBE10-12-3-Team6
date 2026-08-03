"use client";

import { useEffect, useRef } from "react";
import { apiFetch, API_BASE } from "../lib";

const RECONNECT_DELAY_MS = 2000;

// 로그인된 상태에서 서버에 SSE 연결을 유지해 online 상태를 마킹한다.
// 서버는 Redis에 presence(60s TTL)를 저장하고 30s 마다 하트비트 ping을 보내 TTL을 갱신한다.
// accessToken이 없으면 2초 뒤 재시도해서 로그인 완료 시 자동으로 붙는다.
export default function PresenceHeartbeat() {
  const closedRef = useRef(false);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    closedRef.current = false;

    const isAuthed = () =>
      typeof window !== "undefined" && !!localStorage.getItem("accessToken");

    const scheduleReconnect = () => {
      if (closedRef.current) return;
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current);
      reconnectTimer.current = setTimeout(connect, RECONNECT_DELAY_MS);
    };

    const connect = async () => {
      if (closedRef.current) return;
      if (!isAuthed()) {
        scheduleReconnect();
        return;
      }

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

      scheduleReconnect();
    };

    // 로그아웃 시 현재 SSE 스트림을 즉시 abort — 이후 재접속 루프는 accessToken 부재를 감지해
    // 자연스럽게 대기 상태로 들어감. 다시 로그인하면 다음 폴링(최대 2s)에 자동 연결.
    const handleLogout = () => {
      console.log("[presence] 로그아웃 감지 — SSE abort");
      controllerRef.current?.abort();
    };
    window.addEventListener("triplog-logout", handleLogout);

    connect();

    return () => {
      closedRef.current = true;
      controllerRef.current?.abort();
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current);
      window.removeEventListener("triplog-logout", handleLogout);
    };
  }, []);

  return null;
}
