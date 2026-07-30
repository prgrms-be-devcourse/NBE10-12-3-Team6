"use client";

import { useState } from "react";
import { ChatCircleDots } from "@phosphor-icons/react";
import AnimatedBottomSheet from "../../components/AnimatedBottomSheet";

export default function TripChatRoomButton({
  className = "",
}: {
  className?: string;
}) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setIsOpen(true)}
        aria-label="여행 채팅방 열기"
        title="여행 채팅방"
        className={`trip-header-icon-button w-10 h-10 shrink-0 rounded-full flex items-center justify-center ${className}`}
      >
        <ChatCircleDots size={20} weight="bold" />
      </button>

      {isOpen && (
        <AnimatedBottomSheet
          onClose={() => setIsOpen(false)}
          className="flex h-[72%] flex-col overflow-hidden pb-[env(safe-area-inset-bottom)]"
        >
          {(close) => (
            <>
              <div className="flex shrink-0 justify-end px-5 pt-5 pb-3">
                <button
                  type="button"
                  onClick={close}
                  className="text-sm font-semibold text-blue-500"
                >
                  닫기
                </button>
              </div>
              <div
                className="min-h-0 flex-1 overflow-y-auto"
                aria-label="채팅방 준비 영역"
              />
            </>
          )}
        </AnimatedBottomSheet>
      )}
    </>
  );
}
