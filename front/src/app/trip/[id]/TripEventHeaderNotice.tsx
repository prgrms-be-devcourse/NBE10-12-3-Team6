"use client";

import { useEffect, useState, type ReactNode } from "react";
import { useTripEvent } from "./TripEventProvider";

type NoticePhase =
  | "title-visible"
  | "title-hidden"
  | "message-visible"
  | "message-exiting"
  | "title-returning";

function ActiveHeaderNotice({
  message,
  children,
}: {
  message: string;
  children: ReactNode;
}) {
  const [phase, setPhase] = useState<NoticePhase>("title-visible");
  const titleHidden = phase !== "title-visible" && phase !== "title-returning";
  const messageVisible = phase === "message-visible";
  const messageExiting = phase === "message-exiting" || phase === "title-returning";

  useEffect(() => {
    const hideTitleFrame = window.requestAnimationFrame(() => {
      setPhase("title-hidden");
    });
    const showMessageTimer = window.setTimeout(() => {
      setPhase("message-visible");
    }, 360);
    const hideMessageTimer = window.setTimeout(() => {
      setPhase("message-exiting");
    }, 4400);
    const showTitleTimer = window.setTimeout(() => {
      setPhase("title-returning");
    }, 4700);

    return () => {
      window.cancelAnimationFrame(hideTitleFrame);
      window.clearTimeout(showMessageTimer);
      window.clearTimeout(hideMessageTimer);
      window.clearTimeout(showTitleTimer);
    };
  }, []);

  return (
    <>
      <div
        className={`trip-event-header-title flex h-full items-center justify-center overflow-hidden ${
          titleHidden ? "is-hidden" : ""
        }`}
      >
        {children}
      </div>
      <p
        role="status"
        aria-live="polite"
        className={`trip-event-header-message absolute inset-0 flex items-center justify-center px-1 text-center text-[13px] leading-4 font-semibold text-blue-600 ${
          messageVisible ? "is-visible" : ""
        } ${messageExiting ? "is-exiting" : ""}`}
      >
        <span className="trip-event-header-message-text">{message}</span>
      </p>
    </>
  );
}

export default function TripEventHeaderNotice({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  const { activeNotice } = useTripEvent();

  return (
    <div className={`trip-event-header-notice min-w-0 ${className}`}>
      <div className="relative h-full w-full">
        {activeNotice ? (
          <ActiveHeaderNotice
            key={activeNotice.noticeId}
            message={activeNotice.message}
          >
            {children}
          </ActiveHeaderNotice>
        ) : (
          <div className="trip-event-header-title flex h-full items-center justify-center overflow-hidden">
            {children}
          </div>
        )}
      </div>
    </div>
  );
}
