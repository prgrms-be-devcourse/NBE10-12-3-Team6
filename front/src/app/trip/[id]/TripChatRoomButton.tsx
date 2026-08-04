"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ChatCircleDots } from "@phosphor-icons/react";
import { apiFetch, API_BASE } from "../../lib";
import AnimatedBottomSheet from "../../components/AnimatedBottomSheet";
import TripChatRoom from "./TripChatRoom";

export default function TripChatRoomButton({
  className = "",
  refreshKey,
}: {
  className?: string;
  refreshKey?: string;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const { id } = useParams<{ id: string }>();

  const loadUnreadCount = async () => {
    try {
      const res = await apiFetch(`${API_BASE}/api/v1/trips/chat/unread-counts`);
      const body = await res.json();
      setUnreadCount(body.data?.[id] ?? 0);
    } catch {
      // 배지 표시용 최선 노력 조회이므로 실패해도 무시한다
    }
  };

  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      void loadUnreadCount();
    });
    return () => cancelAnimationFrame(frame);
  }, [id, refreshKey]);

  const handleOpen = () => {
    setIsOpen(true);
    setUnreadCount(0);
  };

  const handleClose = () => {
    setIsOpen(false);
    void loadUnreadCount();
  };

  return (
    <>
      <button
        type="button"
        onClick={handleOpen}
        aria-label="여행 채팅방 열기"
        title="여행 채팅방"
        className={`trip-header-icon-button w-10 h-10 shrink-0 rounded-full flex items-center justify-center ${className}`}
      >
        <span className="relative flex items-center justify-center">
          <ChatCircleDots size={20} weight="bold" />
          {unreadCount > 0 && (
            <span className="absolute -top-1 -right-1.5 min-w-[16px] h-4 px-1 rounded-full bg-red-500 text-white text-[10px] font-bold flex items-center justify-center">
              {unreadCount > 99 ? "99+" : unreadCount}
            </span>
          )}
        </span>
      </button>

      {isOpen && (
        <AnimatedBottomSheet
          onClose={handleClose}
          className="flex flex-col overflow-hidden pb-[env(safe-area-inset-bottom)]"
          style={{ minHeight: "36%", maxHeight: "72%" }}
        >
          {() => <TripChatRoom tripGroupId={Number(id)} />}
        </AnimatedBottomSheet>
      )}
    </>
  );
}
