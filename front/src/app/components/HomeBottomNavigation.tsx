"use client";

import { useRouter } from "next/navigation";
import FixedBottomPortal from "./FixedBottomPortal";

type HomeTab = "home" | "profile" | "settings";

type HomeBottomNavigationProps = {
  activeTab: HomeTab;
  onHome?: () => void;
  onProfile?: () => void;
};

export default function HomeBottomNavigation({
  activeTab,
  onHome,
  onProfile,
}: HomeBottomNavigationProps) {
  const router = useRouter();

  const navigation = (
    <div
      className="pointer-events-none fixed bottom-0 left-0 right-0 z-40 flex justify-center px-6"
      style={{ paddingBottom: "max(1rem, env(safe-area-inset-bottom))" }}
    >
      <div className="flex items-center justify-center gap-3">
        <nav
          className={`trip-floating-tab-bar home-floating-tab-bar pointer-events-auto is-${activeTab}`}
          aria-label="홈 메뉴"
        >
          <span className="trip-floating-tab-indicator" aria-hidden="true" />

          <button
            type="button"
            onClick={onHome ?? (() => router.push("/home"))}
            className="trip-floating-tab-button"
            aria-label="여행 모임 목록"
            aria-current={activeTab === "home" ? "page" : undefined}
          >
            <span className={`trip-floating-tab-icon ${activeTab === "home" ? "is-active" : ""}`}>
              <svg className="h-full w-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3.75 11.25 12 4.5l8.25 6.75M5.75 10.75V20h4.5v-5.25h3.5V20h4.5v-9.25" />
              </svg>
            </span>
            <span className="sr-only">여행 모임 목록</span>
          </button>

          <button
            type="button"
            onClick={() => router.push("/settings")}
            className="trip-floating-tab-button"
            aria-label="설정"
            aria-current={activeTab === "settings" ? "page" : undefined}
          >
            <span className={`trip-floating-tab-icon ${activeTab === "settings" ? "is-active" : ""}`}>
              <svg className="h-full w-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.6 3.75h4.8l.55 2.16c.48.2.94.46 1.36.78l2.1-.63 2.4 4.16-1.55 1.53c.04.25.06.5.06.75s-.02.5-.06.75l1.55 1.53-2.4 4.16-2.1-.63c-.42.32-.88.58-1.36.78l-.55 2.16H9.6l-.55-2.16a7.3 7.3 0 0 1-1.36-.78l-2.1.63-2.4-4.16 1.55-1.53a4.8 4.8 0 0 1 0-1.5L3.19 10.22l2.4-4.16 2.1.63c.42-.32.88-.58 1.36-.78L9.6 3.75Z" />
                <circle cx="12" cy="12.5" r="2.6" strokeWidth={2} />
              </svg>
            </span>
            <span className="sr-only">설정</span>
          </button>
        </nav>

        <button
          type="button"
          onClick={onProfile ?? (() => router.push("/account"))}
          className="home-profile-floating-button pointer-events-auto"
          aria-label="내 정보"
          aria-current={activeTab === "profile" ? "page" : undefined}
        >
          <span className={`home-profile-floating-icon ${activeTab === "profile" ? "is-active" : ""}`}>
            <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.75 7.75a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.75 19.25a7.25 7.25 0 0 1 14.5 0" />
            </svg>
          </span>
          <span className="sr-only">내 정보</span>
        </button>
      </div>
    </div>
  );

  return <FixedBottomPortal>{navigation}</FixedBottomPortal>;
}
