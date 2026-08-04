"use client";

import { useState, useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useStore } from "../store";
import { formatDate, apiFetch, useAuthGuard, API_BASE } from "../lib";
import { useTripOwnerStore } from "../stores/tripOwnerStore";
import AnimatedBottomSheet from "../components/AnimatedBottomSheet";
import HomeBottomNavigation from "../components/HomeBottomNavigation";

type ApiTrip = {
  id: number;
  name: string;
  region: string;
  nights: number;
  startDate: string;
  joinUrl?: string;
  joinCode?: string;
};

function getTripStatus(startDate: string, nights: number) {
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const start = new Date(startDate); start.setHours(0, 0, 0, 0);
  const end = new Date(startDate); end.setDate(end.getDate() + nights); end.setHours(0, 0, 0, 0);
  if (today < start) return "before";
  if (today <= end) return "during";
  return "after";
}

const STATUS_BADGE: Record<string, { label: string; className: string }> = {
  before: { label: "여행 전", className: "bg-blue-100 text-blue-600" },
  during: { label: "여행 중", className: "bg-green-100 text-green-600" },
  after:  { label: "여행 완료", className: "bg-gray-100 text-gray-500" },
};

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const MONTHS = Array.from({ length: 12 }, (_, i) => i);

function toDateValue(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function fromDateValue(value: string) {
  return value ? new Date(value + "T00:00:00") : new Date();
}

function dateButtonText(value: string, placeholder: string) {
  if (!value) return placeholder;
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "long",
  }).format(fromDateValue(value));
}

function MobileDatePicker({
  title,
  value,
  onSelect,
  onClose,
}: {
  title: string;
  value: string;
  onSelect: (value: string) => void;
  onClose: () => void;
}) {
  const selectedDate = value ? fromDateValue(value) : null;
  const [viewDate, setViewDate] = useState(() => {
    const base = selectedDate ?? new Date();
    return new Date(base.getFullYear(), base.getMonth(), 1);
  });
  const [draftValue, setDraftValue] = useState(selectedDate ? toDateValue(selectedDate) : "");
  const year = viewDate.getFullYear();
  const month = viewDate.getMonth();
  const currentYear = new Date().getFullYear();
  const yearOptions = Array.from(
    new Set([
      ...Array.from({ length: 16 }, (_, i) => currentYear - 5 + i),
      year,
    ])
  ).sort((a, b) => a - b);
  const todayValue = toDateValue(new Date());
  const firstWeekday = new Date(year, month, 1).getDay();
  const lastDate = new Date(year, month + 1, 0).getDate();
  const cells = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: lastDate }, (_, i) => new Date(year, month, i + 1)),
  ];

  const moveMonth = (offset: number) => {
    setViewDate(current => new Date(current.getFullYear(), current.getMonth() + offset, 1));
  };

  return (
    <AnimatedBottomSheet
      onClose={onClose}
      zIndexClassName="z-[60]"
      className="overflow-y-auto px-5 pb-6"
    >
      {(close) => (
        <>
        <span className="sr-only">{title}</span>

        <div className="grid grid-cols-2 gap-3 mb-3">
          <label>
            <div className="relative">
              <select
                value={year}
                onChange={e => setViewDate(current => new Date(Number(e.target.value), current.getMonth(), 1))}
                className="w-full appearance-none py-3 pl-4 pr-12 rounded-2xl bg-gray-100 text-sm font-bold outline-none"
              >
                {yearOptions.map(option => (
                  <option key={option} value={option}>{option}년</option>
                ))}
              </select>
              <svg className="pointer-events-none absolute right-4 top-1/2 w-4 h-4 -translate-y-1/2 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M6 9l6 6 6-6" />
              </svg>
            </div>
          </label>
          <label>
            <div className="relative">
              <select
                value={month}
                onChange={e => setViewDate(current => new Date(current.getFullYear(), Number(e.target.value), 1))}
                className="w-full appearance-none py-3 pl-4 pr-12 rounded-2xl bg-gray-100 text-sm font-bold outline-none"
              >
                {MONTHS.map(option => (
                  <option key={option} value={option}>{option + 1}월</option>
                ))}
              </select>
              <svg className="pointer-events-none absolute right-4 top-1/2 w-4 h-4 -translate-y-1/2 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M6 9l6 6 6-6" />
              </svg>
            </div>
          </label>
        </div>

        <div className="flex items-center justify-between bg-gray-50 rounded-2xl px-3 py-2 mb-4">
          <button
            type="button"
            onClick={() => moveMonth(-1)}
            className="w-10 h-10 rounded-full bg-white border border-gray-100 text-blue-500 flex items-center justify-center"
            aria-label="이전 달"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M15 18l-6-6 6-6" />
            </svg>
          </button>
          <p className="font-bold">{year}년 {month + 1}월</p>
          <button
            type="button"
            onClick={() => moveMonth(1)}
            className="w-10 h-10 rounded-full bg-white border border-gray-100 text-blue-500 flex items-center justify-center"
            aria-label="다음 달"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M9 6l6 6-6 6" />
            </svg>
          </button>
        </div>

        <div className="grid grid-cols-7 gap-1 mb-2">
          {WEEKDAYS.map(day => (
            <div key={day} className="h-8 flex items-center justify-center text-xs font-bold text-gray-400">
              {day}
            </div>
          ))}
        </div>

        <div className="grid grid-cols-7 gap-1">
          {cells.map((date, i) => {
            if (!date) return <div key={`empty-${i}`} className="aspect-square" />;
            const dateValue = toDateValue(date);
            const isSelected = dateValue === draftValue;
            const isToday = dateValue === todayValue;
            return (
              <button
                key={dateValue}
                type="button"
                onClick={() => setDraftValue(dateValue)}
                className={`aspect-square rounded-2xl flex flex-col items-center justify-center text-sm font-bold transition ${
                  isSelected
                    ? "bg-blue-500 text-white"
                    : isToday
                      ? "bg-blue-50 text-blue-600"
                      : "text-gray-700 hover:bg-gray-100"
                }`}
              >
                <span>{date.getDate()}</span>
                {isToday && !isSelected && <span className="text-[10px] mt-0.5">오늘</span>}
              </button>
            );
          })}
        </div>

        <button
          type="button"
          onClick={() => {
            if (!draftValue) return;
            onSelect(draftValue);
            close();
          }}
          disabled={!draftValue}
          className={`w-full mt-5 py-4 rounded-2xl font-bold transition ${
            draftValue ? "bg-blue-500 text-white" : "bg-gray-100 text-gray-400"
          }`}
        >
          날짜 선택
        </button>
        </>
      )}
    </AnimatedBottomSheet>
  );
}

function DateField({
  value,
  onChange,
  label,
  placeholder = "날짜를 선택",
  className = "",
}: {
  value: string;
  onChange: (value: string) => void;
  label: string;
  placeholder?: string;
  className?: string;
}) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={`p-3 bg-gray-100 rounded-xl text-sm outline-none flex items-center justify-between gap-3 text-left ${className}`}
        aria-label={label}
      >
        <span className={value ? "font-semibold text-gray-800" : "font-normal text-gray-400"}>
          {dateButtonText(value, placeholder)}
        </span>
        <svg className="w-5 h-5 text-gray-500 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3M4 11h16M5 5h14a1 1 0 011 1v14a1 1 0 01-1 1H5a1 1 0 01-1-1V6a1 1 0 011-1z" />
        </svg>
      </button>
      {open && (
        <MobileDatePicker
          title={label}
          value={value}
          onSelect={onChange}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
}

const CHOSUNG = ["ㄱ","ㄲ","ㄴ","ㄷ","ㄸ","ㄹ","ㅁ","ㅂ","ㅃ","ㅅ","ㅆ","ㅇ","ㅈ","ㅉ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ"];

function getChosung(char: string): string {
  const code = char.charCodeAt(0);
  if (code >= 0xAC00 && code <= 0xD7A3) return CHOSUNG[Math.floor((code - 0xAC00) / (21 * 28))];
  return char;
}

function matchesRegion(city: string, query: string): boolean {
  if (!query) return true;
  if (city.includes(query)) return true;
  if ([...query].every(c => CHOSUNG.includes(c))) {
    return [...city].map(getChosung).join("").includes(query);
  }
  return false;
}

const DOMESTIC_REGIONS = [
  { group: "수도권", cities: ["서울", "인천", "수원", "경기"] },
  { group: "강원", cities: ["강릉", "속초", "춘천", "동해", "원주", "평창"] },
  { group: "충청", cities: ["대전", "청주", "천안", "세종", "공주", "충주"] },
  { group: "전라", cities: ["광주", "전주", "여수", "순천", "목포", "군산"] },
  { group: "경상", cities: ["부산", "대구", "울산", "경주", "거제", "통영", "진주", "포항", "안동"] },
  { group: "제주", cities: ["제주", "서귀포"] },
];

function RegionSheetPicker({
  value,
  onChange,
}: {
  value: string;
  onChange: (v: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [inputValue, setInputValue] = useState(value);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const inlineSuggestions = inputValue.trim()
    ? DOMESTIC_REGIONS.flatMap(({ cities }) =>
        cities.filter(c => matchesRegion(c, inputValue.trim()))
      )
    : [];

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const handleInlineSelect = (city: string) => {
    setInputValue(city);
    onChange(city);
  };

  const handleSheetOpen = () => {
    setOpen(true);
    setShowSuggestions(false);
  };

  const handleSheetSelect = (city: string, close: () => void) => {
    setInputValue(city);
    onChange(city);
    setShowSuggestions(true);
    close();
  };

  return (
    <>
      <div ref={containerRef} className="relative">
        <div className="flex gap-2">
          <div className="relative flex-1">
            <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-4.35-4.35M17 11A6 6 0 1 1 5 11a6 6 0 0 1 12 0z" />
            </svg>
            <input
              className="w-full pl-9 pr-4 py-3 bg-gray-100 rounded-xl text-sm outline-none"
              placeholder="지역 검색 또는 직접 입력"
              value={inputValue}
              onChange={e => { setInputValue(e.target.value); onChange(e.target.value); setShowSuggestions(true); }}
              onFocus={() => { if (inputValue.trim()) setShowSuggestions(true); }}
              autoComplete="off"
            />
          </div>
          <button
            type="button"
            onClick={handleSheetOpen}
            className="w-12 flex items-center justify-center bg-gray-100 rounded-xl text-gray-500 shrink-0"
            aria-label="전체 지역 목록"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 10h16M4 14h16M4 18h16" />
            </svg>
          </button>
        </div>

        <div className="mt-2 h-[6.4rem] border border-gray-200 rounded-xl overflow-hidden">
          {showSuggestions && inlineSuggestions.length > 0 ? (
            <div className="flex flex-wrap gap-2 content-start p-3 h-full overflow-hidden">
              {inlineSuggestions.map(city => (
                <button
                  key={city}
                  type="button"
                  onMouseDown={(e) => { e.preventDefault(); handleInlineSelect(city); }}
                  className={`px-3 py-1.5 rounded-xl text-sm font-semibold transition shrink-0 ${
                    inputValue === city
                      ? "bg-blue-500 text-white"
                      : "bg-white text-gray-700 shadow-sm border border-gray-100 active:bg-gray-100"
                  }`}
                >
                  {city}
                </button>
              ))}
            </div>
          ) : (
            <div className="flex items-center justify-center h-full">
              <p className="text-xs text-gray-300">지역명을 입력하면 추천 지역이 나타나요</p>
            </div>
          )}
        </div>
      </div>

      {open && (
        <AnimatedBottomSheet
          onClose={() => setOpen(false)}
          zIndexClassName="z-[60]"
          className="max-h-[85vh] flex flex-col"
        >
          {(close) => (
            <>
            <div className="overflow-y-auto px-5 pb-8 flex flex-col gap-5">
              {DOMESTIC_REGIONS.map(({ group, cities }) => (
                <div key={group}>
                  <p className="text-xs font-bold text-gray-400 mb-2.5">{group}</p>
                  <div className="flex flex-wrap gap-2">
                    {cities.map(city => (
                      <button
                        key={city}
                        type="button"
                        onClick={() => handleSheetSelect(city, close)}
                        className={`px-4 py-2 rounded-xl text-sm font-semibold transition ${
                          value === city
                            ? "bg-blue-500 text-white"
                            : "bg-gray-100 text-gray-700 active:bg-gray-200"
                        }`}
                      >
                        {city}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
            </>
          )}
        </AnimatedBottomSheet>
      )}
    </>
  );
}

function TripCard({
  trip,
  animationDelayMs = 0,
  unreadCount = 0,
}: {
  trip: ApiTrip;
  animationDelayMs?: number;
  unreadCount?: number;
}) {
  const status = getTripStatus(trip.startDate, trip.nights);
  const badge = STATUS_BADGE[status];
  return (
    <Link
      href={`/trip/${trip.id}`}
      className="home-trip-card block"
      style={{ animationDelay: `${animationDelayMs}ms` }}
    >
      <div className="p-4 bg-white rounded-2xl shadow-sm border border-gray-100">
        <div className="flex items-start justify-between mb-3">
          <div>
            <p className="font-bold text-base flex items-center gap-1.5">
              {trip.name}
              {unreadCount > 0 && (
                <span className="min-w-[18px] h-[18px] px-1 rounded-full bg-red-500 text-white text-[10px] font-bold flex items-center justify-center">
                  {unreadCount > 99 ? "99+" : unreadCount}
                </span>
              )}
            </p>
            <p className="text-sm text-gray-500 mt-0.5">{trip.region} · {trip.nights}박 {trip.nights + 1}일</p>
          </div>
          <span className={`text-xs font-bold px-2.5 py-1 rounded-full shrink-0 ml-2 ${badge.className}`}>
            {badge.label}
          </span>
        </div>
        <p className="text-xs text-gray-400">{formatDate(trip.startDate)} 시작</p>
      </div>
    </Link>
  );
}

export default function HomePage() {
  useAuthGuard();
  const router = useRouter();
  const { currentUser, loadTrips } = useStore();
  const clearOwnerId = useTripOwnerStore((state) => state.clearOwnerId);

  const [trips, setTrips] = useState<ApiTrip[]>([]);
  const [unreadCounts, setUnreadCounts] = useState<Record<number, number>>({});
  const [loading, setLoading] = useState(true);
  const [resultAnimationKey, setResultAnimationKey] = useState(0);
  const [hasSearched, setHasSearched] = useState(false);
  // 무한 스크롤 상태: 백엔드가 Slice 응답으로 hasNext/page를 내려주므로 그대로 보관.
  // page는 "지금까지 로드된 마지막 페이지 번호". loadingMore는 sentinel 재진입 방지.
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const tripTitleRef = useRef<HTMLInputElement>(null);
  const [showCreate, setShowCreate] = useState(false);
  useEffect(() => { if (showCreate) setTimeout(() => tripTitleRef.current?.focus(), 50); }, [showCreate]);
  const [tripTitle, setTripTitle] = useState("");
  const [tripRegion, setTripRegion] = useState("");
  const [tripDate, setTripDate] = useState("");
  const [tripNights, setTripNights] = useState(2);
  const [keyWord, setKeyWord] = useState("");
  const [searchDate, setSearchDate] = useState("");
  const canSearchTrips = keyWord.trim().length > 0 || Boolean(searchDate);
  const todayValue = toDateValue(new Date());
  // 백엔드 @PageableDefault(size = 10)와 일치. 프론트가 명시적으로 넘겨야 첫 응답의 size 필드가 예측 가능해짐.
  const PAGE_SIZE = 10;

  // Slice 한 페이지 fetch. reset=true면 목록을 새로 시작(첫 페이지), false면 다음 페이지를 이어붙임.
  // 검색/리셋은 reset=true, sentinel 진입은 reset=false로 호출.
  const fetchPage = async ({
    pageNumber,
    reset,
    keyword,
    startDate,
    animateResults = false,
  }: {
    pageNumber: number;
    reset: boolean;
    keyword: string;
    startDate: string;
    animateResults?: boolean;
  }) => {
    const p = new URLSearchParams();
    if (keyword.trim()) p.set("keyword", keyword.trim());
    if (startDate) p.set("startDate", startDate);
    p.set("page", String(pageNumber));
    p.set("size", String(PAGE_SIZE));
    try {
      const res = await apiFetch(`${API_BASE}/api/v1/trips?${p.toString()}`);
      const body = await res.json();
      const slice = body.data ?? {};
      const items: ApiTrip[] = Array.isArray(slice.items) ? slice.items : [];
      // reset이면 통째로 교체, 아니면 누적. 응답 순서(startDate DESC)를 그대로 유지.
      const nextTrips = reset ? items : [...trips, ...items];
      setTrips(nextTrips);
      loadTrips(nextTrips);
      setPage(typeof slice.page === "number" ? slice.page : pageNumber);
      setHasNext(Boolean(slice.hasNext));
      if (animateResults) setResultAnimationKey(key => key + 1);
    } catch {
    } finally {
      if (reset) setLoading(false);
      setLoadingMore(false);
    }
  };

  const loadFirstPage = (opts: { keyword?: string; startDate?: string; animateResults?: boolean } = {}) =>
    fetchPage({
      pageNumber: 0,
      reset: true,
      keyword: opts.keyword ?? keyWord,
      startDate: opts.startDate ?? searchDate,
      animateResults: opts.animateResults,
    });

  const loadUnreadCounts = async () => {
    try {
      const res = await apiFetch(`${API_BASE}/api/v1/trips/chat/unread-counts`);
      const body = await res.json();
      setUnreadCounts(body.data ?? {});
    } catch {
    }
  };

  const resetSearch = () => {
    setKeyWord("");
    setSearchDate("");
    setHasSearched(false);
    loadFirstPage({ animateResults: true, keyword: "", startDate: "" });
  };

  const runSearch = () => {
    if (!canSearchTrips) return;
    setHasSearched(true);
    loadFirstPage({ animateResults: true });
  };

  const handleKeywordChange = (value: string) => {
    setKeyWord(value);
    if (!value.trim() && (keyWord.trim() || searchDate || hasSearched)) {
      setSearchDate("");
      setHasSearched(false);
      loadFirstPage({ animateResults: true, keyword: "", startDate: "" });
    }
  };

  useEffect(() => {
    localStorage.removeItem("pendingInviteCode");
    clearOwnerId();
    const initialLoadFrame = requestAnimationFrame(() => {
      void loadFirstPage();
      void loadUnreadCounts();
    });

    return () => {
      cancelAnimationFrame(initialLoadFrame);
    };
  }, []);

  // 하단 sentinel이 뷰포트에 들어오면 다음 slice fetch.
  // deps에 hasNext/loadingMore/page/keyWord/searchDate가 들어가야 최신 값을 캡처.
  // rootMargin으로 실제 sentinel이 완전히 노출되기 전에 미리 요청해 스크롤 끊김 방지.
  useEffect(() => {
    if (!hasNext || loadingMore) return;
    const node = sentinelRef.current;
    if (!node) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setLoadingMore(true);
          void fetchPage({
            pageNumber: page + 1,
            reset: false,
            keyword: keyWord,
            startDate: searchDate,
          });
        }
      },
      { rootMargin: "200px" },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [hasNext, loadingMore, page, keyWord, searchDate]);

  const handleCreateTrip = async () => {
    if (!tripTitle.trim() || !tripRegion.trim() || !tripDate) return;
    try {
      const res = await apiFetch(`${API_BASE}/api/v1/trips`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: tripTitle.trim(),
          region: tripRegion.trim(),
          startDate: tripDate,
          nights: String(tripNights),
        }),
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body?.message ?? "생성 실패");
      const newTrip: ApiTrip = body.data;
      const updated = [...trips, newTrip];
      setTrips(updated);
      loadTrips(updated.map(t => ({ ...t, joinUrl: t.joinUrl ?? t.joinCode })));
      setShowCreate(false);
      setTripTitle(""); setTripRegion(""); setTripDate(""); setTripNights(2);
      router.push(`/trip/${newTrip.id}`);
    } catch (e) {
      console.error("[여행 만들기 실패]", e);
    }
  };

  return (
    <div className="home-page min-h-[100dvh]">
      <div className="app-safe-header shrink-0 flex items-center justify-between px-4 pb-2">
        <p className="text-3xl font-bold">내 여행</p>
        <button onClick={() => setShowCreate(true)} aria-label="여행 모임 만들기" className="home-create-button w-10 h-10 flex items-center justify-center rounded-full">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth={2.75} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
        </button>
      </div>

      <div className="home-content px-4 pb-0">
        <div className="shrink-0 mb-5">
          <p className="text-2xl font-bold">안녕하세요, {currentUser.name}님</p>
          <p className="text-sm text-gray-500 mt-1">여행 모임을 만들고 초대 링크로 멤버를 초대해보세요.</p>
        </div>

        <div className="shrink-0 flex flex-col gap-2 mb-4">
          <input
            className="w-full p-3 bg-gray-100 rounded-xl text-sm outline-none"
            placeholder="여행 이름, 지역, 멤버명으로 검색"
            value={keyWord}
            onChange={e => handleKeywordChange(e.target.value)}
            onKeyDown={e => { if (e.key === "Enter" && !e.nativeEvent.isComposing && canSearchTrips) { runSearch(); (e.target as HTMLInputElement).blur(); } }}
          />
          <div className="flex gap-2">
            <DateField
              className="flex-1"
              value={searchDate}
              onChange={setSearchDate}
              label="여행 시작일 검색"
              placeholder="날짜로 검색"
            />
            {searchDate && (
              <button
                onClick={resetSearch}
                className="px-3 bg-gray-100 rounded-xl text-gray-400 hover:text-gray-600 text-sm"
              >
                ✕
              </button>
            )}
            <button
              onClick={runSearch}
              disabled={!canSearchTrips}
              aria-label="검색"
              className="w-12 shrink-0 bg-blue-500 text-white rounded-xl flex items-center justify-center disabled:bg-blue-500/30 disabled:text-white/70 disabled:opacity-100 disabled:cursor-not-allowed"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth={3} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-4.35-4.35M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" />
              </svg>
            </button>
          </div>
        </div>

        <div className="home-list-scroll">
          {loading ? (
            <div className="flex justify-center py-10">
              <p className="text-sm text-gray-400">불러오는 중...</p>
            </div>
          ) : trips.length === 0 && hasSearched ? (
            <p key={resultAnimationKey} className="home-search-empty py-12 text-center text-sm text-gray-400">
              찾는 여행 모임이 없습니다
            </p>
          ) : trips.length === 0 ? (
            <div key={resultAnimationKey} className="home-search-empty flex flex-col items-center gap-4 p-7 bg-gray-50 rounded-2xl text-center">
              <span className="text-blue-400">
                <svg className="w-12 h-12" fill="none" stroke="currentColor" strokeWidth={2.75} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M13.5 10.5a4 4 0 0 0-5.66 0l-2.34 2.34a4 4 0 1 0 5.66 5.66l1.05-1.05" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 13.5a4 4 0 0 0 5.66 0l2.34-2.34A4 4 0 1 0 12.84 5.5L11.8 6.55" />
                </svg>
              </span>
              <p className="font-semibold">아직 여행 모임이 없어요</p>
              <p className="text-sm text-gray-500">여행 모임을 만들면 초대 링크가 생성됩니다.</p>
              <button onClick={() => setShowCreate(true)} className="px-6 py-3 rounded-2xl bg-blue-500 text-white font-semibold text-sm">
                여행 모임 만들기
              </button>
            </div>
          ) : (
            <div key={resultAnimationKey} className="home-search-results flex flex-col gap-3">
              {[...trips].sort((a, b) => {
                const order = { during: 0, before: 1, after: 2 };
                return order[getTripStatus(a.startDate, a.nights)] - order[getTripStatus(b.startDate, b.nights)];
              }).map((trip, index) => (
                <TripCard
                  key={trip.id}
                  trip={trip}
                  animationDelayMs={Math.min(index, 6) * 55}
                  unreadCount={unreadCounts[trip.id] ?? 0}
                />
              ))}
              {loadingMore && (
                <p className="text-center text-xs text-gray-400 py-3">불러오는 중...</p>
              )}
              {/* sentinel: 하단이 뷰포트 근처에 들어오면 IntersectionObserver가 다음 페이지 요청.
                  hasNext=false거나 loadingMore 중이면 useEffect가 옵저버를 붙이지 않음. */}
              {hasNext && <div ref={sentinelRef} aria-hidden className="h-1" />}
            </div>
          )}
        </div>
      </div>

      {showCreate && (
        <AnimatedBottomSheet
          onClose={() => setShowCreate(false)}
          className="max-h-[90vh] overflow-y-auto"
        >
          {() => (
            <>
            <div className="flex flex-col gap-5 px-4 pb-4">
              <div>
                <label className="text-sm font-semibold mb-1.5 block">여행 이름</label>
                <input ref={tripTitleRef} className="w-full p-3 bg-gray-100 rounded-xl text-sm outline-none" placeholder="여행 이름을 입력해주세요" value={tripTitle} onChange={e => setTripTitle(e.target.value)} />
              </div>
              <div>
                <label className="text-sm font-semibold mb-1.5 block">지역</label>
                <RegionSheetPicker value={tripRegion} onChange={setTripRegion} />
              </div>
              <div>
                <div className="flex items-center gap-2 mb-1.5">
                  <label className="text-sm font-semibold">시작일</label>
                  {tripDate === todayValue && (
                    <span className="text-xs font-semibold text-orange-500 bg-orange-50 px-2 py-0.5 rounded-full">
                      오늘 출발 시 계획 등록 제한
                    </span>
                  )}
                </div>
                <DateField
                  className="w-full"
                  value={tripDate}
                  onChange={setTripDate}
                  label="여행 시작일 선택"
                  placeholder="날짜를 선택"
                />
              </div>
              <div>
                <label className="text-sm font-semibold mb-1.5 block">기간</label>
                <div className="flex items-center gap-4">
                  <button onClick={() => setTripNights(n => Math.max(0, n - 1))} className="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center text-lg font-bold">−</button>
                  <span className="flex-1 text-center font-semibold">{tripNights}박 {tripNights + 1}일</span>
                  <button onClick={() => setTripNights(n => Math.min(10, n + 1))} className="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center text-lg font-bold">+</button>
                </div>
              </div>
              <button
                onClick={handleCreateTrip}
                disabled={!tripTitle.trim() || !tripRegion.trim() || !tripDate}
                className="w-full py-4 rounded-2xl bg-blue-500 text-white font-semibold disabled:opacity-40"
              >
                여행 모임 만들기
              </button>
            </div>
            </>
          )}
        </AnimatedBottomSheet>
      )}

      <HomeBottomNavigation activeTab="home" />
    </div>
  );
}
