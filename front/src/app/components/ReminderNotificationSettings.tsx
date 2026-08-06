"use client";

import { useState } from "react";
import { BellRinging, BellSlash } from "@phosphor-icons/react";
import {
  disableReminderNotifications,
  enableReminderNotifications,
  getReminderNotificationPermission,
  isReminderNotificationEnabled,
} from "../pushNotifications";

type PermissionState = ReturnType<typeof getReminderNotificationPermission>;

export default function ReminderNotificationSettings() {
  const [enabled, setEnabled] = useState(isReminderNotificationEnabled);
  const [permission, setPermission] = useState<PermissionState>(
    getReminderNotificationPermission,
  );
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const toggle = async () => {
    if (loading) return;

    setLoading(true);
    setMessage("");

    try {
      if (enabled) {
        await disableReminderNotifications();
        setEnabled(false);
        setMessage("리마인드 알림을 껐습니다.");
      } else {
        await enableReminderNotifications();
        setEnabled(true);
        setMessage("리마인드 알림을 켰습니다.");
      }

      setPermission(getReminderNotificationPermission());
    } catch (error) {
      setPermission(getReminderNotificationPermission());
      setMessage(error instanceof Error ? error.message : "알림 설정을 변경하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const blocked = permission === "denied";
  const unsupported = permission === "unsupported";

  return (
    <section className="mt-8">
      <div className="mb-3">
        <h2 className="font-bold">리마인드 알림</h2>
        <p className="mt-1 text-xs text-gray-500">
          1년 전 여행 기록을 매일 오전 9시에 다시 알려줍니다.
        </p>
      </div>

      <button
        type="button"
        onClick={toggle}
        disabled={loading || blocked || unsupported}
        aria-pressed={enabled}
        className={`theme-setting-option flex h-[78px] w-full items-center gap-4 rounded-2xl border px-4 py-3 text-left transition ${
          enabled
            ? "is-selected border-blue-100 bg-blue-50"
            : "border-gray-100 bg-white"
        } disabled:opacity-50`}
      >
        <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${
          enabled ? "bg-blue-500 text-white" : "bg-gray-100 text-gray-500"
        }`}>
          {enabled ? <BellRinging size={24} /> : <BellSlash size={24} />}
        </span>
        <span className="min-w-0 flex-1">
          <span className={`block text-sm font-bold ${enabled ? "text-blue-600" : ""}`}>
            {enabled ? "알림 받는 중" : "알림 꺼짐"}
          </span>
          <span className="mt-1 block text-xs leading-5 text-gray-500">
            {blocked
              ? "브라우저 설정에서 알림 권한을 다시 허용해야 합니다."
              : unsupported
                ? "현재 브라우저에서는 푸시 알림을 사용할 수 없습니다."
                : "기록 작성자에게 푸시 알림을 보냅니다."}
          </span>
        </span>
        <span className={`h-6 w-11 shrink-0 rounded-full p-0.5 transition ${
          enabled ? "bg-blue-500" : "bg-gray-300"
        }`}>
          <span className={`block h-5 w-5 rounded-full bg-white transition ${
            enabled ? "translate-x-5" : "translate-x-0"
          }`} />
        </span>
      </button>

      {message && (
        <p className="mt-3 px-1 text-xs leading-5 text-gray-500">
          {message}
        </p>
      )}
    </section>
  );
}
