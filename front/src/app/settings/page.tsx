"use client";

import { useEffect, useState } from "react";
import HomeBottomNavigation from "../components/HomeBottomNavigation";
import { useAuthGuard } from "../lib";

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
    description: "기기의 라이트·다크 모드 설정을 자동으로 따라갑니다.",
  },
  {
    value: "light",
    title: "라이트 모드 고정",
    description: "기기 설정과 관계없이 밝은 화면으로 고정합니다.",
  },
  {
    value: "dark",
    title: "다크 모드 고정",
    description: "기기 설정과 관계없이 어두운 화면으로 고정합니다.",
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

function isThemePreference(value: string | null): value is ThemePreference {
  return value === "system" || value === "light" || value === "dark";
}

export default function SettingsPage() {
  useAuthGuard();

  const [themePreference, setThemePreference] = useState<ThemePreference>("system");

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
    <div className="settings-page min-h-screen">
      <header className="shrink-0 px-4 pt-14 pb-5">
        <h1 className="text-3xl font-bold">설정</h1>
      </header>

      <main className="settings-list-scroll px-4">
        <section>
          <div className="mb-3">
            <h2 className="font-bold">화면 모드</h2>
            <p className="mt-1 text-xs text-gray-500">
              이 기기에서 사용할 테마를 선택합니다.
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

      </main>

      <HomeBottomNavigation activeTab="settings" />
    </div>
  );
}
