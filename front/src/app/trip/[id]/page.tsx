"use client";

import { useState, useEffect, type MouseEvent } from "react";
import { useRouter, useParams } from "next/navigation";
import Link from "next/link";
import { useStore, Trip, TripDay, PlanCandidate, uid } from "../../store";
import { Avatar, formatDate, apiFetch, useAuthGuard, API_BASE } from "../../lib";
import { useTripOwnerStore } from "../../stores/tripOwnerStore";
import AnimatedBottomSheet from "../../components/AnimatedBottomSheet";

// ── InviteModal ───────────────────────────────────────────────────────────────

function InviteSheet({ trip, onClose }: { trip: Trip; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  const [origin, setOrigin] = useState("");
  useEffect(() => { setOrigin(window.location.origin); }, []);
  const code = trip.inviteCode;
  const inviteLink = `${origin}/invite/${code}`;

  const copyLink = () => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(inviteLink).catch(() => {});
    } else {
      const el = document.createElement("input");
      el.value = inviteLink;
      document.body.appendChild(el);
      el.select();
      document.execCommand("copy");
      document.body.removeChild(el);
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <AnimatedBottomSheet onClose={onClose} className="p-6 flex flex-col gap-4">
      {(close) => (
        <>
        <div className="flex items-center justify-between">
          <p className="text-lg font-bold">초대 링크</p>
          <button onClick={close} className="text-blue-500 font-medium">닫기</button>
        </div>

        <p className="text-sm text-gray-500">아래 링크를 친구에게 공유해주세요.</p>

        <div className="flex items-center justify-center py-6 rounded-2xl bg-blue-50 border border-blue-100">
          <p className="text-sm font-semibold text-blue-600 break-all text-center px-2">{inviteLink}</p>
        </div>

        <button
          onClick={copyLink}
          className="w-full py-3.5 rounded-2xl text-sm font-semibold bg-blue-100 text-blue-600"
        >
          {copied ? "복사 완료 ✓" : "링크 복사"}
        </button>
        </>
      )}
    </AnimatedBottomSheet>
  );
}

// ── AddCandidateSheet ─────────────────────────────────────────────────────────

interface KakaoPlace {
  id: string;
  place_name: string;
  address_name: string;
  road_address_name: string;
  category_group_name: string;
  place_url: string;
}

function AddCandidateSheet({
  trip, onAdd, onClose,
}: { trip: Trip; onAdd: (c: PlanCandidate) => void; onClose: () => void }) {
  const { currentUser } = useStore();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<KakaoPlace[]>([]);
  const [selected, setSelected] = useState<KakaoPlace | null>(null);
  const [loading, setLoading] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);

  const search = async () => {
    if (!query.trim()) return;
    setLoading(true);
    setSelected(null);
    setHasSearched(false);
    try {
      const res = await fetch(`/api/places?query=${encodeURIComponent(query)}`);
      const data = await res.json();
      setResults(data.documents ?? []);
      setHasSearched(true);
    } finally {
      setLoading(false);
    }
  };

  const handleAdd = async (close: () => void) => {
    if (!selected) return;
    try {
      const res = await apiFetch(`${API_BASE}/api/v1/trips/${trip.id}/wish-places`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: selected.place_name,
          category: selected.category_group_name || undefined,
          address: selected.road_address_name || selected.address_name,
          kakaoPlaceId: Number(selected.id),
          kakaoMapUrl: selected.place_url,
        }),
      });
      const body = await res.json();
      onAdd({
        id: String(body.data?.id ?? uid()),
        authorId: currentUser.id,
        authorName: currentUser.name,
        placeName: selected.place_name,
        address: selected.road_address_name || selected.address_name,
        category: selected.category_group_name || undefined,
      });
      close();
    } catch (e) {
      console.error("[후보 등록 실패]", e);
    }
  };

  return (
    <AnimatedBottomSheet
      onClose={onClose}
      className="min-h-[50vh] max-h-[85vh] overflow-y-auto"
    >
      {(close) => (
        <>
        <div className="flex items-center justify-between px-4 pt-5 pb-3 border-b border-gray-100">
          <h2 className="text-lg font-bold">후보 올리기</h2>
          <button onClick={close} className="text-blue-500 font-medium">닫기</button>
        </div>
        <div className="p-4 flex flex-col gap-4">
          {/* 장소 검색 */}
          <div>
            <label className="text-sm font-semibold mb-1.5 block">장소 검색</label>
            <div className="flex gap-2">
              <input
                className="flex-1 p-3 bg-gray-100 rounded-xl text-sm outline-none"
                placeholder="검색어 입력해주세요"
                value={query}
                onChange={e => setQuery(e.target.value)}
                onKeyDown={e => e.key === "Enter" && search()}
              />
              <button
                onClick={search}
                aria-label="검색"
                className="w-14 shrink-0 rounded-xl text-white flex items-center justify-center"
                style={{ background: "#3b82f6" }}
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth={3} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-4.35-4.35M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" />
                </svg>
              </button>
            </div>
          </div>

          {/* 검색 결과 */}
          <div className="candidate-search-panel rounded-2xl p-3 flex flex-col gap-2 min-h-[300px]">
            {loading && <p className="text-sm text-gray-400 text-center py-4">검색 중...</p>}
            {!loading && hasSearched && results.length === 0 && (
              <p className="text-sm text-gray-400 text-center py-4">검색 결과가 없습니다.</p>
            )}
            {!loading && results.length > 0 && results.map((place, i) => {
              const isSelected = selected?.place_name === place.place_name && selected?.address_name === place.address_name;
              const isDup = trip.candidates.some(c => c.placeName === place.place_name && c.address === (place.road_address_name || place.address_name));
              return (
                <div
                  key={i}
                  className={`candidate-search-result p-3 rounded-xl border flex items-center gap-2 transition-all ${isSelected ? "is-selected" : ""}`}
                >
                  <button onClick={() => setSelected(place)} className="flex-1 text-left min-w-0">
                    <p className="text-sm font-semibold truncate">{place.place_name}</p>
                    <p className="text-xs text-gray-400 mt-0.5 truncate">{place.road_address_name || place.address_name}</p>
                    {place.category_group_name && <p className="text-xs text-gray-400">{place.category_group_name}</p>}
                  </button>
                  {isSelected && (
                    isDup
                      ? <span className="text-xs text-red-400 shrink-0">이미 등록됨</span>
                      : <button
                          onClick={() => handleAdd(close)}
                          className="shrink-0 px-4 py-2.5 rounded-xl text-sm font-semibold text-white"
                          style={{ background: "#3b82f6" }}
                        >
                          등록
                        </button>
                  )}
                </div>
              );
            })}
          </div>
        </div>
        </>
      )}
    </AnimatedBottomSheet>
  );
}

// ── TripCandidatePoolCard ─────────────────────────────────────────────────────

function TripCandidatePoolCard({ trip, onUpdate, tripStatus }: { trip: Trip; onUpdate: (t: Trip) => void; tripStatus: "before" | "during" | "after" }) {
  const [showAdd, setShowAdd] = useState(false);
  const canRegister = tripStatus === "before";

  return (
    <div className="candidate-pool-card candidate-section-panel px-4 pt-4 pb-0 flex flex-col gap-3 rounded-2xl">
      <div className="flex items-start justify-between">
        <div>
          <p className="font-semibold">후보 장소</p>
          <p className="text-xs text-gray-500 mt-0.5">일차와 상관없이 여행 전체에서 사용할 후보를 올립니다.</p>
        </div>
        <span className="text-xs font-bold text-gray-500 shrink-0 ml-2">{trip.candidates.length}개</span>
      </div>

      {canRegister ? (
        <button
          onClick={() => setShowAdd(true)}
          className="candidate-add-button w-full py-3 rounded-xl font-semibold text-sm"
        >
          + 후보 올리기
        </button>
      ) : (
        <div className="candidate-closed-notice w-full py-3 rounded-xl text-center text-sm font-semibold">
          여행이 시작되어 후보 등록이 마감되었습니다
        </div>
      )}

      {trip.candidates.length === 0 ? (
        <div className="candidate-empty-card p-3 rounded-xl flex items-start gap-2">
          <span className="text-base shrink-0">📍</span>
          <div>
            <p className="text-sm font-semibold">아직 후보 장소가 없습니다.</p>
            <p className="text-xs text-gray-500 mt-0.5">후보를 올린 뒤 각 일차의 시간 구간에서 투표나 랜덤으로 선택합니다.</p>
          </div>
        </div>
      ) : (
        <div className="candidate-list-scroll flex flex-col gap-2">
          {trip.candidates.map(c => (
            <div key={c.id} className="candidate-list-item flex items-center gap-3 p-3 rounded-2xl">
              <span className="text-base shrink-0 text-green-600">📍</span>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold truncate">{c.placeName}</p>
                <p className="text-xs text-gray-400 truncate">{c.address}</p>
                <p className="text-xs text-gray-400">등록자 {c.authorName}</p>
              </div>
            </div>
          ))}
        </div>
      )}

      {showAdd && (
        <AddCandidateSheet
          trip={trip}
          onAdd={c => onUpdate({ ...trip, candidates: [...trip.candidates, c] })}
          onClose={() => setShowAdd(false)}
        />
      )}
    </div>
  );
}

// ── Status Badge ──────────────────────────────────────────────────────────────

function statusBadge(day: TripDay) {
  if (day.isPlanSkipped) return <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-gray-100 text-gray-500">계획 건너뜀</span>;
  if (day.isPlanCompleted) return <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-blue-100 text-blue-600">계획 완료</span>;
  return <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-orange-100 text-orange-600">작성 필요</span>;
}

// ── Main ──────────────────────────────────────────────────────────────────────

type Tab = "trip" | "candidates" | "vote" | "timeline";

interface VoteTimeline {
  voteId: number | null;
  timeLineId: number;
  confirmedPlaceName: string;
  startTime: string;
  voteStatus?: string;
}
interface VoteDay {
  date: string;
  timeLines: VoteTimeline[];
}

interface DayTimelineItem {
  timeLineId: number;
  startTime: string;
  endTime: string;
  confirmedPlaceName?: string | null;
  category?: string | null;
}

export default function TripDetailPage() {
  useAuthGuard();
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { trips, updateTrip, upsertTrip } = useStore();
  const setOwnerId = useTripOwnerStore((state) => state.setOwnerId);
  const [showInvite, setShowInvite] = useState(false);
  const [tab, setTab] = useState<Tab>("trip");
  const [isReturningHome, setIsReturningHome] = useState(false);
  const [isNavigatingAway, setIsNavigatingAway] = useState(false);

  useEffect(() => {
    const returnTab = sessionStorage.getItem(`return-tab-${id}`) as Tab | null;
    if (returnTab) {
      setTab(returnTab);
      sessionStorage.removeItem(`return-tab-${id}`);
      return;
    }
    const savedTab = sessionStorage.getItem(`active-tab-${id}`) as Tab | null;
    if (savedTab) setTab(savedTab);
  }, []);

  const changeTab = (t: Tab) => {
    setTab(t);
    if (t !== "timeline") sessionStorage.setItem(`active-tab-${id}`, t);
  };

  const goTripList = () => {
    if (isReturningHome || isNavigatingAway) return;
    sessionStorage.removeItem(`active-tab-${id}`);
    sessionStorage.removeItem(`return-tab-${id}`);
    setIsReturningHome(true);
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.setTimeout(() => router.push("/home"), prefersReducedMotion ? 0 : 240);
  };

  const navigateWithPageExit = (
    href: string,
    beforeNavigate?: () => void
  ) => (event: MouseEvent<HTMLAnchorElement>) => {
    if (isReturningHome || isNavigatingAway) {
      event.preventDefault();
      return;
    }

    event.preventDefault();
    beforeNavigate?.();
    setIsNavigatingAway(true);
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.setTimeout(() => router.push(href), prefersReducedMotion ? 0 : 240);
  };

  const [voteData, setVoteData] = useState<VoteDay[] | null>(null);
  const [allDayTimelines, setAllDayTimelines] = useState<Record<number, DayTimelineItem[]>>({});
  const trip = trips.find(t => t.id === id);

  const tripStatus = (() => {
    if (!trip) return "before";
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const startDate = new Date(trip.startDate); startDate.setHours(0, 0, 0, 0);
    const endDate = new Date(trip.startDate); endDate.setDate(endDate.getDate() + trip.nights); endDate.setHours(23, 59, 59, 999);
    return today < startDate ? "before" : today > endDate ? "after" : "during";
  })();

  useEffect(() => {
    if (!id) return;

    Promise.all([
      apiFetch(`${API_BASE}/api/v1/trips/${id}`).then(r => r.json()),
      apiFetch(`${API_BASE}/api/v1/trips/${id}/timelines/count`).then(r => r.json()),
    ]).then(([tripBody, countBody]) => {
      const tripData = tripBody.data;

      if (!tripData) return;
      const inviteCode = tripData.joinCode ?? "";
      const counts: { day: number; count: number }[] = countBody.data ?? [];

      const COLORS = ["blue", "orange", "green", "purple", "pink", "teal", "indigo", "cyan"];
      const members: { id: number; name: string; color: string; isAdmin?: boolean }[] =
        (tripData.members ?? []).map((m: { memberId: number; name: string; admin: boolean }, i: number) => ({
          id: m.memberId,
          name: m.name,
          color: COLORS[i % COLORS.length],
          isAdmin: m.admin,
        }));

      const baseDays = trip?.days ?? Array.from(
        { length: (tripData.nights ?? 0) + 1 },
        (_, i) => {
          const d = new Date(tripData.startDate + "T00:00:00");
          d.setDate(d.getDate() + i);
          const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
          return { id: `day-${i + 1}`, dayNumber: i + 1, date: dateStr, blocks: [{ id: uid(), order: 1, theme: "meal" as const, startMinute: 9 * 60, endMinute: 10 * 60 }], isPlanCompleted: false, isPlanSkipped: false, selectedCandidateByBlock: {}, votedUserIDsByBlockAndCandidate: {}, records: [] };
        }
      );

      const updatedDays = baseDays.map(day => {
        const entry = counts.find(c => c.day === day.dayNumber);
        if (!entry || entry.count === 0) return day;
        const blocks = Array.from({ length: entry.count }, (_, i) => ({
          id: `${day.dayNumber}-${i}`,
          order: i + 1,
          theme: "etc" as const,
          startMinute: 0,
          endMinute: 0,
        }));
        return { ...day, blocks, isPlanCompleted: true };
      });


      if (tripData.ownerId) setOwnerId(tripData.ownerId);
      upsertTrip({
        ...(trip ?? {
          id: String(tripData.id),
          name: tripData.name,
          region: tripData.region,
          startDate: tripData.startDate,
          nights: tripData.nights,
          candidates: [],
          inviteJoinIndex: 0,
        }),
        inviteCode,
        members,
        days: updatedDays,
      });
    }).catch(() => {});
  }, [id]);


  useEffect(() => {
    if (!trip || tripStatus === "before" || trip.days.length === 0) return;
    Promise.all(
      trip.days.map(day =>
        apiFetch(`${API_BASE}/api/v1/trips/${id}/timelines?dayNumber=${day.dayNumber}`)
          .then(r => r.json())
          .then(body => ({ dayNumber: day.dayNumber, items: (body.data ?? []) as DayTimelineItem[] }))
          .catch(() => ({ dayNumber: day.dayNumber, items: [] }))
      )
    ).then(results => {
      const map: Record<number, DayTimelineItem[]> = {};
      results.forEach(({ dayNumber, items }) => { map[dayNumber] = items; });
      setAllDayTimelines(map);
    });
  }, [trip?.id, tripStatus]);

  useEffect(() => {
    if (tab !== "timeline" || !trip) return;
    if (tripStatus === "during") {
      const today = new Date(); today.setHours(0, 0, 0, 0);
      const startDate = new Date(trip.startDate); startDate.setHours(0, 0, 0, 0);
      const daysSinceStart = Math.floor((today.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24));
      router.push(`/trip/${trip.id}/day/${daysSinceStart + 1}/photos?from=timeline`);
    } else if (tripStatus === "after") {
      router.push(`/trip/${trip.id}/timeline`);
    }
  }, [tab, tripStatus, trip]);

  useEffect(() => {
    if (tab !== "vote" || !id) return;
    setVoteData(null);
    apiFetch(`${API_BASE}/api/v1/trips/${id}/votes`)
      .then(r => r.json())
      .then(body => setVoteData(body.data ?? []))
      .catch(() => setVoteData([]));
  }, [tab, id]);

  useEffect(() => {
    if (tab !== "candidates" || !id || !trip) return;
    apiFetch(`${API_BASE}/api/v1/trips/${id}/wish-places`)
      .then(r => r.json())
      .then(body => {
        const wishes: { placeId: number; name: string; address: string; category: string; createdBy: string }[] = body.data ?? [];
        const candidates = wishes.map(w => ({
          id: String(w.placeId),
          authorId: 0,
          authorName: w.createdBy,
          placeName: w.name,
          address: w.address,
          category: w.category,
        }));
        updateTrip({ ...trip, candidates });
      })
      .catch(() => {});
  }, [tab, id, trip?.id]);

  if (!trip) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <p className="text-gray-400 text-sm">불러오는 중...</p>
      </div>
    );
  }

  return (
    <div className={`min-h-screen ${tab === "trip" || tab === "candidates" || tab === "vote" ? "trip-detail-page" : "pb-24"} ${isReturningHome || isNavigatingAway ? "trip-page-exit" : ""}`}>
      {/* Header */}
      <div className="relative min-h-[84px] px-4 pt-12 pb-2">
        <button
          onClick={goTripList}
          aria-label="여행방 목록"
          className="trip-header-icon-button absolute left-4 top-10 z-10 w-10 h-10 rounded-full flex items-center justify-center"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth={2.2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 10.75 12 4l8.25 6.75" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M5.75 9.75V20h12.5V9.75" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 20v-5.25h4.5V20" />
          </svg>
        </button>
        <div className="absolute left-1/2 top-11 w-40 -translate-x-1/2 text-center sm:w-56">
          <p className="truncate font-semibold text-base">{trip.name}</p>
          <p className="text-xs text-gray-400">{trip.region} · {trip.nights}박 {trip.nights + 1}일</p>
        </div>
        {tab === "trip" ? (
          <button
            onClick={() => setShowInvite(true)}
            aria-label="초대 링크"
            className="trip-header-icon-button absolute right-4 top-10 z-10 w-10 h-10 rounded-full flex items-center justify-center"
          >
            <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
            </svg>
          </button>
        ) : tab === "timeline" && tripStatus === "during" ? (
          <Link
            href={`/trip/${id}/timeline?from=timeline`}
            onClick={navigateWithPageExit(`/trip/${id}/timeline?from=timeline`)}
            className="absolute right-4 top-12 z-10 text-xs font-semibold text-blue-500"
          >
            전체보기
          </Link>
        ) : (
          null
        )}
      </div>

      {/* Tab content */}
      <div
        key={tab}
        className={`trip-page-transition px-4 pt-2 flex flex-col gap-5 ${tab === "trip" || tab === "candidates" || tab === "vote" ? "trip-tab-content" : ""}`}
      >
        {tab === "trip" && (
          <div className="trip-overview-panel flex min-h-0 flex-1 flex-col gap-5 overflow-hidden">
            {/* Members */}
            <div className="p-4 bg-gray-50 rounded-2xl">
              <div className="flex items-center justify-between mb-3">
                <p className="font-semibold">여행 멤버</p>
                <span className="text-xs text-gray-400 font-bold">{trip.members.length}명</span>
              </div>
              <div className="flex gap-4 overflow-x-auto pb-1">
                {trip.members.map(m => (
                  <div key={m.id} className="flex flex-col items-center gap-1.5 shrink-0">
                    <Avatar user={m} size={42} />
                    <span className="text-xs text-gray-600">{m.name}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Day list */}
            <div className="flex min-h-0 flex-1 flex-col gap-3">
              <p className="font-semibold">일차별 계획</p>
              <div className="trip-day-list-scroll flex flex-col gap-3">
                {trip.days.map(day => {
                  if (tripStatus !== "before") {
                    const items = allDayTimelines[day.dayNumber] ?? [];
                    return (
                      <div key={day.id} className="p-4 bg-gray-50 rounded-2xl flex flex-col gap-3">
                        <div className="flex items-start justify-between">
                          <div>
                            <p className="font-semibold">{day.dayNumber}일차</p>
                            <p className="text-xs text-gray-400 mt-0.5">{formatDate(day.date)}</p>
                          </div>
                        </div>
                        {items.length === 0 ? (
                          <p className="text-xs text-gray-400">확정된 계획이 없습니다.</p>
                        ) : (
                          <div className="flex flex-col gap-2">
                            {items.map((item, idx) => {
                              return (
                                <div key={item.timeLineId ?? idx} className="flex items-center gap-3 bg-white rounded-xl px-3 py-2.5">
                                  <span className="text-xs text-gray-400 font-bold shrink-0">{item.startTime.slice(11, 16)}~{item.endTime.slice(11, 16)}</span>
                                  {(item.category || item.confirmedPlaceName) && (
                                    <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 shrink-0">{item.category ?? "기타"}</span>
                                  )}
                                  <p className="text-sm font-semibold truncate">{item.confirmedPlaceName || "미확정"}</p>
                                </div>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    );
                  }
                  return (
                    <Link
                      key={day.id}
                      href={`/trip/${trip.id}/day/${day.dayNumber}`}
                      onClick={navigateWithPageExit(`/trip/${trip.id}/day/${day.dayNumber}`)}
                    >
                      <div className="p-4 bg-gray-50 rounded-2xl">
                        <div className="flex items-start justify-between mb-2">
                          <div>
                            <p className="font-semibold">{day.dayNumber}일차</p>
                            <p className="text-xs text-gray-400 mt-0.5">{formatDate(day.date)}</p>
                          </div>
                          {statusBadge(day)}
                        </div>
                        {!day.isPlanSkipped && day.blocks.length > 0 && (
                          <div className="flex items-center gap-3 mt-1 text-xs font-bold">
                            <span className="text-blue-500">{day.blocks.length}개 시간 구간</span>
                          </div>
                        )}
                      </div>
                    </Link>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {tab === "candidates" && (
          <TripCandidatePoolCard trip={trip} onUpdate={updateTrip} tripStatus={tripStatus} />
        )}

        {tab === "vote" && (() => {
          const toDayNumber = (date: string) => {
            const start = new Date(trip.startDate + "T00:00:00");
            const d = new Date(date + "T00:00:00");
            return Math.round((d.getTime() - start.getTime()) / 86400000) + 1;
          };
          const toTimeStr = (iso: string) => iso.slice(11, 16);

          const createVote = async (timeLineId: number) => {
            await apiFetch(`${API_BASE}/api/v1/trips/${id}/votes`, {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ timeLineId }),
            });
            const r = await apiFetch(`${API_BASE}/api/v1/trips/${id}/votes`);
            const body = await r.json();
            setVoteData(body.data ?? []);
          };

          return (
            <div className="vote-section-panel flex min-h-0 flex-1 flex-col gap-4 overflow-hidden">
              <p className="font-semibold">일차별 투표</p>
              {voteData === null ? (
                <div className="p-4 bg-gray-50 rounded-2xl">
                  <p className="text-sm text-gray-400">불러오는 중...</p>
                </div>
              ) : (
                <div className="vote-list-scroll flex flex-col gap-4">
                  {voteData.map(dayEntry => {
                    const dayNumber = toDayNumber(dayEntry.date);
                    return (
                      <div key={dayEntry.date} className="flex flex-col gap-2">
                        <p className="text-sm font-semibold text-gray-500">{dayNumber}일차 · {formatDate(dayEntry.date)}</p>
                        {dayEntry.timeLines.length === 0 ? (
                          <div className="p-4 bg-gray-50 rounded-2xl flex items-center justify-center">
                            <span className="text-xs text-gray-400">일정 확정 후 투표 가능</span>
                          </div>
                        ) : dayEntry.timeLines.map((tl, tlIdx) => {
                          if (tl.voteId === null) {
                            return (
                              <div key={tl.timeLineId} className="p-4 bg-gray-50 rounded-2xl flex items-center justify-between">
                                <div className="flex-1 min-w-0">
                                  <p className="text-xs text-gray-400">{toTimeStr(tl.startTime)} 시작</p>
                                  <p className="text-sm font-semibold mt-0.5 text-gray-400">투표 없음</p>
                                </div>
                                <button
                                  onClick={() => createVote(tl.timeLineId)}
                                  className="text-xs font-bold px-3 py-1.5 rounded-full"
                                  style={{ background: "#eff6ff", color: "#2563eb" }}
                                >
                                  투표 생성하기
                                </button>
                              </div>
                            );
                          }
                          return (
                            <Link
                              key={tl.voteId}
                              href={`/trip/${trip.id}/day/${dayNumber}/block/${tl.voteId}?from=vote&timelineId=${tl.timeLineId}`}
                              onClick={navigateWithPageExit(
                                `/trip/${trip.id}/day/${dayNumber}/block/${tl.voteId}?from=vote&timelineId=${tl.timeLineId}`,
                                () => {
                                  localStorage.setItem(`block-order-${tl.voteId}`, String(tlIdx + 1));
                                  sessionStorage.setItem(`return-tab-${id}`, "vote");
                                }
                              )}
                            >
                              <div className="p-4 bg-gray-50 rounded-2xl flex items-center justify-between">
                                <div className="flex-1 min-w-0">
                                  <p className="text-xs text-gray-400">{toTimeStr(tl.startTime)} 시작</p>
                                  <p className="text-sm font-semibold mt-0.5 truncate">
                                    {tl.confirmedPlaceName || "미확정"}
                                  </p>
                                </div>
                                <div className="flex items-center gap-2 shrink-0">
                                  {tl.voteStatus === "투표 확정"
                                    ? <span className="text-xs font-bold px-2 py-1 rounded-full" style={{ background: "#dcfce7", color: "#16a34a" }}>확정됨</span>
                                    : tl.voteStatus === "투표 기한 만료"
                                    ? <span className="text-xs font-bold px-2 py-1 rounded-full" style={{ background: "#f3f4f6", color: "#9ca3af" }}>투표 마감</span>
                                    : <span className="text-xs font-bold px-2 py-1 rounded-full" style={{ background: "#dbeafe", color: "#2563eb" }}>투표하기</span>
                                  }
                                  <svg className="w-4 h-4 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                                  </svg>
                                </div>
                              </div>
                            </Link>
                            );
                          })}
                        </div>
                      );
                    })}
                  </div>
              )}
            </div>
          );
        })()}

        {tab === "timeline" && (() => {

          if (tripStatus === "before") {
            return (
              <div className="flex flex-col gap-5">
                <div className="timeline-before-card p-5 rounded-2xl flex flex-col gap-2">
                  <p className="text-base font-bold">아직 여행 시작 전이에요</p>
                  <p className="text-sm text-gray-500">계획을 한번 더 점검해보는 건 어때요?</p>
                </div>
                <div className="flex flex-col gap-2">
                  {([
                    { key: "trip", label: "여행 모임", tone: "blue" },
                    { key: "candidates", label: "후보 장소", tone: "green" },
                    { key: "vote", label: "투표", tone: "yellow" },
                  ] as const).map(({ key, label, tone }) => (
                    <button
                      key={key}
                      onClick={() => changeTab(key)}
                      className={`timeline-before-link ${tone} w-full py-4 rounded-2xl font-semibold text-left px-5`}
                    >
                      {label} →
                    </button>
                  ))}
                </div>
              </div>
            );
          }

          return null;
        })()}
      </div>

      {/* Bottom tab bar */}
      <div
        className="pointer-events-none fixed bottom-0 left-0 right-0 z-40 flex justify-center px-6"
        style={{ paddingBottom: "max(1rem, env(safe-area-inset-bottom))" }}
      >
        <div className={`trip-floating-tab-bar pointer-events-auto is-${tab}`}>
          <span className="trip-floating-tab-indicator" aria-hidden="true" />
          {([
            { key: "trip", label: "여행 모임", icon: (
              <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            )},
            { key: "candidates", label: "후보 장소", icon: (
              <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            )},
            { key: "vote", label: "투표", icon: (
              <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
              </svg>
            )},
            { key: "timeline", label: "타임라인", icon: (
              <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            )},
          ] as const).map(({ key, label, icon }) => {
            const active = tab === key;
            return (
              <button
                key={key}
                onClick={() => changeTab(key)}
                className="trip-floating-tab-button"
                aria-label={label}
                aria-current={active ? "page" : undefined}
              >
                <span className={`trip-floating-tab-icon ${active ? "is-active" : ""}`}>
                  {icon}
                </span>
                <span className="sr-only">{label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {showInvite && (
        <InviteSheet trip={trip} onClose={() => setShowInvite(false)} />
      )}
    </div>
  );
}
