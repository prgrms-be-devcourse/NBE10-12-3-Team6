"use client";

import { initializeApp, getApps } from "firebase/app";
import {
  deleteToken,
  getMessaging,
  getToken,
  isSupported,
  type Messaging,
} from "firebase/messaging";
import { apiFetch, API_BASE } from "./lib";

const ENABLED_STORAGE_KEY = "triplog-reminder-notifications-enabled";
const TOKEN_STORAGE_KEY = "triplog-fcm-token";

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
};

function hasFirebaseConfig() {
  return Object.values(firebaseConfig).every(Boolean) &&
    Boolean(process.env.NEXT_PUBLIC_FIREBASE_VAPID_KEY);
}

async function getServiceWorkerRegistration() {
  if (!("serviceWorker" in navigator)) return null;

  const existing = await navigator.serviceWorker.getRegistration("/sw.js");
  return existing ?? navigator.serviceWorker.register("/sw.js");
}

async function requireSuccessfulResponse(response: Response, fallbackMessage: string) {
  if (response.ok) return;

  throw new Error(fallbackMessage);
}

async function getFirebaseMessaging(): Promise<Messaging> {
  const supported = await isSupported();
  if (!supported) {
    throw new Error("이 브라우저는 푸시 알림을 지원하지 않습니다.");
  }

  if (!hasFirebaseConfig()) {
    throw new Error("Firebase Web Push 환경변수가 설정되지 않았습니다.");
  }

  const app = getApps().length > 0 ? getApps()[0] : initializeApp(firebaseConfig);
  return getMessaging(app);
}

export function isReminderNotificationEnabled() {
  if (typeof window === "undefined") return false;
  return localStorage.getItem(ENABLED_STORAGE_KEY) === "true";
}

export function getReminderNotificationPermission(): NotificationPermission | "unsupported" {
  if (typeof window === "undefined" || !("Notification" in window)) {
    return "unsupported";
  }

  return Notification.permission;
}

export async function enableReminderNotifications() {
  if (!("Notification" in window)) {
    throw new Error("이 브라우저는 알림 권한을 지원하지 않습니다.");
  }

  const permission = await Notification.requestPermission();
  if (permission !== "granted") {
    throw new Error("알림 권한이 허용되지 않았습니다.");
  }

  const messaging = await getFirebaseMessaging();
  const serviceWorkerRegistration = await getServiceWorkerRegistration();
  if (!serviceWorkerRegistration) {
    throw new Error("서비스워커를 등록할 수 없습니다.");
  }

  const token = await getToken(messaging, {
    vapidKey: process.env.NEXT_PUBLIC_FIREBASE_VAPID_KEY,
    serviceWorkerRegistration,
  });

  if (!token) {
    throw new Error("FCM 토큰을 발급받지 못했습니다.");
  }

  const previousToken = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (previousToken && previousToken !== token) {
    await apiFetch(`${API_BASE}/api/v1/push-tokens`, {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: previousToken }),
    }).catch(() => undefined);
  }

  const response = await apiFetch(`${API_BASE}/api/v1/push-tokens`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, platform: "WEB" }),
  });
  await requireSuccessfulResponse(response, "푸시 알림 기기를 등록하지 못했습니다.");

  localStorage.setItem(TOKEN_STORAGE_KEY, token);
  localStorage.setItem(ENABLED_STORAGE_KEY, "true");

  return token;
}

export async function disableReminderNotifications() {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);

  if (token) {
    await apiFetch(`${API_BASE}/api/v1/push-tokens`, {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token }),
    }).catch(() => undefined);

    const messaging = await getFirebaseMessaging().catch(() => null);
    if (messaging) {
      await deleteToken(messaging).catch(() => undefined);
    }
  }

  localStorage.removeItem(TOKEN_STORAGE_KEY);
  localStorage.removeItem(ENABLED_STORAGE_KEY);
}

export async function syncReminderNotificationsIfEnabled() {
  if (!isReminderNotificationEnabled()) return;
  if (getReminderNotificationPermission() !== "granted") return;

  await enableReminderNotifications();
}
