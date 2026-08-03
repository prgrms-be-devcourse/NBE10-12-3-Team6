"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { ChatCircleDots } from "@phosphor-icons/react";
import AnimatedBottomSheet from "../../components/AnimatedBottomSheet";
import TripChatRoom from "./TripChatRoom";

export default function TripChatRoomButton({
  className = "",
}: {
  className?: string;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const { id } = useParams<{ id: string }>();

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
          {() => <TripChatRoom tripGroupId={Number(id)} />}
        </AnimatedBottomSheet>
      )}
    </>
  );
}
