"use client";

import { useSyncExternalStore } from "react";

export type PhotoDataPreference = "quality" | "balanced" | "saving";

type PhotoUrls = {
  contentUrl?: string | null;
  normalContentUrl?: string | null;
  dataSaverContentUrl?: string | null;
};

export const PHOTO_DATA_STORAGE_KEY = "triplog-photo-data-preference";
const PHOTO_DATA_CHANGE_EVENT = "triplog-photo-data-change";
const DEFAULT_PHOTO_DATA_PREFERENCE: PhotoDataPreference = "balanced";

export function isPhotoDataPreference(value: string | null): value is PhotoDataPreference {
  return value === "quality" || value === "balanced" || value === "saving";
}

export function getPhotoDataPreference(): PhotoDataPreference {
  if (typeof window === "undefined") return DEFAULT_PHOTO_DATA_PREFERENCE;

  try {
    const savedPreference = localStorage.getItem(PHOTO_DATA_STORAGE_KEY);
    return isPhotoDataPreference(savedPreference)
      ? savedPreference
      : DEFAULT_PHOTO_DATA_PREFERENCE;
  } catch {
    return DEFAULT_PHOTO_DATA_PREFERENCE;
  }
}

export function setPhotoDataPreference(preference: PhotoDataPreference) {
  localStorage.setItem(PHOTO_DATA_STORAGE_KEY, preference);
  window.dispatchEvent(new Event(PHOTO_DATA_CHANGE_EVENT));
}

function subscribePhotoDataPreference(onStoreChange: () => void) {
  const handleStorage = (event: StorageEvent) => {
    if (event.key === null || event.key === PHOTO_DATA_STORAGE_KEY) {
      onStoreChange();
    }
  };

  window.addEventListener("storage", handleStorage);
  window.addEventListener(PHOTO_DATA_CHANGE_EVENT, onStoreChange);
  return () => {
    window.removeEventListener("storage", handleStorage);
    window.removeEventListener(PHOTO_DATA_CHANGE_EVENT, onStoreChange);
  };
}

export function usePhotoDataPreference(): PhotoDataPreference {
  return useSyncExternalStore(
    subscribePhotoDataPreference,
    getPhotoDataPreference,
    () => DEFAULT_PHOTO_DATA_PREFERENCE
  );
}

export function selectPhotoUrls(
  photo: PhotoUrls,
  preference: PhotoDataPreference
): { previewUrl: string | null; detailUrl: string | null } {
  const originalUrl = photo.contentUrl
    ?? photo.normalContentUrl
    ?? photo.dataSaverContentUrl
    ?? null;
  const normalUrl = photo.normalContentUrl ?? originalUrl;
  const dataSaverUrl = photo.dataSaverContentUrl ?? normalUrl;

  if (preference === "quality") {
    return { previewUrl: normalUrl, detailUrl: originalUrl };
  }
  if (preference === "balanced") {
    return { previewUrl: dataSaverUrl, detailUrl: originalUrl };
  }
  return { previewUrl: dataSaverUrl, detailUrl: dataSaverUrl };
}
