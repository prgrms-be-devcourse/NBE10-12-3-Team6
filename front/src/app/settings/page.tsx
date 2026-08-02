"use client";

import { useEffect, useState } from "react";
import { Sparkle } from "@phosphor-icons/react";
import HomeBottomNavigation from "../components/HomeBottomNavigation";
import { useAuthGuard } from "../lib";
import {
  PhotoDataPreference,
  setPhotoDataPreference,
  usePhotoDataPreference,
} from "../photoDataPreference";

type ThemePreference = "system" | "light" | "dark";

const THEME_STORAGE_KEY = "triplog-theme-preference";

const THEME_OPTIONS: {
  value: ThemePreference;
  title: string;
  description: string;
}[] = [
  {
    value: "system",
    title: "시스템 설정",
    description: "기기 설정에 따른 라이트·다크 모드 자동 전환",
  },
  {
    value: "light",
    title: "라이트 모드 고정",
    description: "기기 설정과 관계없이 밝은 화면 고정",
  },
  {
    value: "dark",
    title: "다크 모드 고정",
    description: "기기 설정과 관계없이 어두운 화면 고정",
  },
];

const PHOTO_DATA_OPTIONS: {
  value: PhotoDataPreference;
  title: string;
  description: string;
}[] = [
  {
    value: "quality",
    title: "품질 우선",
    description: "예상 데이터 사용량 약 8~20MB",
  },
  {
    value: "balanced",
    title: "균형 모드",
    description: "예상 데이터 사용량 약 1~5MB",
  },
  {
    value: "saving",
    title: "절약 우선",
    description: "예상 데이터 사용량 약 1~5MB\n사진 확장 시에도 압축본 사용",
  },
];

function ThemeIcon({ preference }: { preference: ThemePreference }) {
  if (preference === "light") {
    return (
      <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <circle cx="12" cy="12" r="3.5" strokeWidth={2} />
        <path strokeLinecap="round" strokeWidth={2} d="M12 2.5v2M12 19.5v2M4.5 12h-2M21.5 12h-2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M18.7 5.3l-1.4 1.4M6.7 17.3l-1.4 1.4" />
      </svg>
    );
  }

  if (preference === "dark") {
    return (
      <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20.2 15.1A8.5 8.5 0 0 1 8.9 3.8 8.5 8.5 0 1 0 20.2 15.1Z" />
      </svg>
    );
  }

  return (
    <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <rect x="3" y="4.5" width="18" height="12.5" rx="2" strokeWidth={2} />
      <path strokeLinecap="round" strokeWidth={2} d="M8.5 21h7M12 17v4" />
    </svg>
  );
}

function PhotoDataIcon({ preference }: { preference: PhotoDataPreference }) {
  if (preference === "quality") {
    return <Sparkle size={24} weight="regular" />;
  }

  if (preference === "balanced") {
    return (
      <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeWidth={2} d="M4 7h8M17 7h3M4 17h3M12 17h8" />
        <circle cx="14.5" cy="7" r="2.5" strokeWidth={2} />
        <circle cx="9.5" cy="17" r="2.5" strokeWidth={2} />
      </svg>
    );
  }

  return (
    <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19.5 4.5c-7.4.2-12.2 3.2-13.8 8.2-1 3.1.8 6.1 4 6.7 4.7.9 8.2-4.1 9.8-14.9Z" />
      <path strokeLinecap="round" strokeWidth={2} d="M5 20c2-4.2 5.1-7.2 9.4-9.3" />
    </svg>
  );
}

function isThemePreference(value: string | null): value is ThemePreference {
  return value === "system" || value === "light" || value === "dark";
}

export default function SettingsPage() {
  useAuthGuard();

  const [themePreference, setThemePreference] = useState<ThemePreference>("system");
  const photoDataPreference = usePhotoDataPreference();

  useEffect(() => {
    const initialThemeFrame = requestAnimationFrame(() => {
      const savedPreference = localStorage.getItem(THEME_STORAGE_KEY);
      if (isThemePreference(savedPreference)) {
        setThemePreference(savedPreference);
      }
    });

    return () => cancelAnimationFrame(initialThemeFrame);
  }, []);

  useEffect(() => {
    const colorSchemeMedia = window.matchMedia("(prefers-color-scheme: dark)");
    const applySelectedTheme = () => {
      const resolvedTheme = themePreference === "system"
        ? colorSchemeMedia.matches
          ? "dark"
          : "light"
        : themePreference;
      document.documentElement.dataset.theme = resolvedTheme;
      document.documentElement.dataset.themePreference = themePreference;
    };

    applySelectedTheme();
    if (themePreference === "system") {
      colorSchemeMedia.addEventListener("change", applySelectedTheme);
    }

    return () => {
      colorSchemeMedia.removeEventListener("change", applySelectedTheme);
    };
  }, [themePreference]);

  const selectTheme = (preference: ThemePreference) => {
    localStorage.setItem(THEME_STORAGE_KEY, preference);
    setThemePreference(preference);
    window.dispatchEvent(new Event("triplog-theme-change"));
  };

  return (
    <div className="settings-page min-h-[100dvh]">
      <header className="app-safe-header shrink-0 px-4 pb-5">
        <h1 className="text-3xl font-bold">설정</h1>
      </header>

      <main className="settings-list-scroll px-4">
        <section>
          <div className="mb-3">
            <h2 className="font-bold">화면 모드</h2>
            <p className="mt-1 text-xs text-gray-500">
              이 기기에서 사용할 테마 선택
            </p>
          </div>

          <div className="flex flex-col gap-3">
            {THEME_OPTIONS.map(option => {
              const selected = themePreference === option.value;

              return (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => selectTheme(option.value)}
                  aria-pressed={selected}
                  className={`theme-setting-option flex items-center gap-4 rounded-2xl border p-4 text-left transition ${
                    selected
                      ? "is-selected border-blue-100 bg-blue-50"
                      : "border-gray-100 bg-white"
                  }`}
                >
                  <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${
                    selected
                      ? "bg-blue-500 text-white"
                      : "bg-gray-100 text-gray-500"
                  }`}>
                    <ThemeIcon preference={option.value} />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className={`block text-sm font-bold ${
                      selected ? "text-blue-600" : ""
                    }`}>
                      {option.title}
                    </span>
                    <span className="mt-1 block text-xs leading-5 text-gray-500">
                      {option.description}
                    </span>
                  </span>
                  <span className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border ${
                    selected
                      ? "border-blue-500 bg-blue-500 text-white"
                      : "border-gray-300"
                  }`}>
                    {selected && (
                      <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="m5 12 4 4 10-10" />
                      </svg>
                    )}
                  </span>
                </button>
              );
            })}
          </div>
        </section>

        <section className="mt-8">
          <div className="mb-3">
            <h2 className="font-bold">데이터 절약 설정</h2>
            <p className="mt-1 text-xs text-gray-500">
              사진을 불러올 때 사용하는 데이터 사용량 조절
              <span className="mt-0.5 block">(원본 사진 1장당 약 5MB / 총 10개 기준)</span>
            </p>
          </div>

          <div className="flex flex-col gap-3">
            {PHOTO_DATA_OPTIONS.map(option => {
              const selected = photoDataPreference === option.value;
              const savingSelected = selected && option.value === "saving";

              return (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => setPhotoDataPreference(option.value)}
                  aria-pressed={selected}
                  className={`theme-setting-option flex h-[78px] items-center gap-4 rounded-2xl border px-4 py-3 text-left transition ${
                    savingSelected
                      ? "is-selected border-yellow-200 bg-yellow-50"
                      : selected
                      ? "is-selected border-blue-100 bg-blue-50"
                      : "border-gray-100 bg-white"
                  }`}
                >
                  <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${
                    savingSelected
                      ? "bg-yellow-400 text-yellow-950"
                      : selected
                      ? "bg-blue-500 text-white"
                      : "bg-gray-100 text-gray-500"
                  }`}>
                    <PhotoDataIcon preference={option.value} />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className={`block text-sm font-bold ${
                      savingSelected
                        ? "text-yellow-700"
                        : selected
                          ? "text-blue-600"
                          : ""
                    }`}>
                      {option.title}
                    </span>
                    <span className={`block whitespace-pre-line text-xs text-gray-500 ${
                      option.value === "saving" ? "leading-4" : "mt-1 leading-5"
                    }`}>
                      {option.description}
                    </span>
                  </span>
                  <span className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border ${
                    savingSelected
                      ? "border-yellow-500 bg-yellow-400 text-yellow-950"
                      : selected
                      ? "border-blue-500 bg-blue-500 text-white"
                      : "border-gray-300"
                  }`}>
                    {selected && (
                      <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="m5 12 4 4 10-10" />
                      </svg>
                    )}
                  </span>
                </button>
              );
            })}
          </div>
          <p className="mt-3 px-1 text-xs leading-5 text-gray-500">
            (사진을 터치하여 확장되었을 때 사용되는 예상치는 포함되지 않는 수치)
          </p>
        </section>

      </main>

      <HomeBottomNavigation activeTab="settings" />
    </div>
  );
}
