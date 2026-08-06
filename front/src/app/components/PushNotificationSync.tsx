"use client";

import { useEffect } from "react";
import { hasStoredAuthentication } from "../authStorage";
import { syncReminderNotificationsIfEnabled } from "../pushNotifications";

export default function PushNotificationSync() {
  useEffect(() => {
    if (!hasStoredAuthentication()) return;

    syncReminderNotificationsIfEnabled().catch((error) => {
      console.debug("[push] reminder notification sync skipped", error);
    });
  }, []);

  return null;
}
