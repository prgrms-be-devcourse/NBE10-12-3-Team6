"use client";

import { useCallback, useState, useEffect, useMemo, useRef, type MouseEvent } from "react";
import { useRouter, useParams } from "next/navigation";
import Link from "next/link";
import { ArrowClockwise, Clock, CrownSimple } from "@phosphor-icons/react";
import { useStore, Trip, TripDay, PlanCandidate, uid } from "../../store";
import { Avatar, durationText, formatDate, apiFetch, useAuthGuard, API_BASE } from "../../lib";
import { useTripOwnerStore } from "../../stores/tripOwnerStore";
import AnimatedBottomSheet from "../../components/AnimatedBottomSheet";
import FixedBottomPortal from "../../components/FixedBottomPortal";
import TripChatRoomButton from "./TripChatRoomButton";
import TripEventHeaderNotice from "./TripEventHeaderNotice";
import { useTripEvent } from "./TripEventProvider";

// ── InviteModal ───────────────────────────────────────────────────────────────

type PastMate = {
  id: number;
  name: string;
  travelCount: number;
  latestTravelDate: string;
  // 가장 최근 함께한 여행방 이름. 백엔드가 상관 서브쿼리로 채움.
  // 극단 케이스(방이 없는 경우) 대비 optional로 선언.
  latestGroupName?: string | null;
};

function formatPastMateDate(dateStr: string): string {
  const d = new Date(dateStr + "T00:00:00");
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}`;
}

// "여행방이름 (2026.06)" 형태로 조합. 방 이름이 없으면 날짜만.
function formatPastMateLabel(mate: PastMate): string {
  const date = formatPastMateDate(mate.latestTravelDate);
  return mate.latestGroupName ? `${mate.latestGroupName} (${date})` : date;
}

function PastMatesInvitePanel({
  trip,
  isAdmin,
  onInvited,
}: {
  trip: Trip;
  isAdmin: boolean;
  onInvited: () => void;
}) {
  const { currentUser } = useStore();
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [items, setItems] = useState<PastMate[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [inviting, setInviting] = useState(false);
  const [onlineIds, setOnlineIds] = useState<Set<number>>(new Set());
  const sentinelRef = useRef<HTMLDivElement>(null);

  const existingMemberIds = useMemo(
    () => new Set(trip.members.map(m => Number(m.id))),
    [trip.members]
  );

  useEffect(() => {
    const t = setTimeout(() => setDebouncedKeyword(keyword.trim()), 250);
    return () => clearTimeout(t);
  }, [keyword]);

  const loadPage = useCallback(async (targetPage: number, kw: string) => {
    const p = new URLSearchParams();
    p.set("page", String(targetPage));
    p.set("size", "15");
    if (kw) p.set("search", kw);
    const res = await apiFetch(`${API_BASE}/api/v1/trips/past-members?${p.toString()}`);
    if (!res.ok) throw new Error("지난 메이트 조회 실패");
    const body = await res.json();
    const data = body.data ?? {};
    const nextItems: PastMate[] = data.items ?? [];
    setItems(prev => (targetPage === 0 ? nextItems : [...prev, ...nextItems]));
    setHasNext(Boolean(data.hasNext));
    setPage(targetPage);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        await loadPage(0, debouncedKeyword);
      } catch {
      } finally {
        if (!cancelled) setInitialLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [debouncedKeyword, loadPage]);

  useEffect(() => {
    const el = sentinelRef.current;
    if (!el) return;
    const observer = new IntersectionObserver(async ([entry]) => {
      if (entry.isIntersecting && hasNext && !loading && !initialLoading) {
        setLoading(true);
        try {
          await loadPage(page + 1, debouncedKeyword);
        } catch {
        } finally {
          setLoading(false);
        }
      }
    }, { threshold: 0.1 });
    observer.observe(el);
    return () => observer.disconnect();
  }, [page, hasNext, loading, initialLoading, debouncedKeyword, loadPage]);

  const refreshOnline = useCallback(async (ids: number[], mates: PastMate[]) => {
    if (ids.length === 0) return;
    try {
      const p = new URLSearchParams();
      p.set("userIds", ids.join(","));
      console.log("[presence] status 요청 ids=", ids);
      const res = await apiFetch(`${API_BASE}/api/v1/presence/status?${p.toString()}`);
      if (!res.ok) {
        console.warn("[presence] status 응답 실패 status=", res.status);
        return;
      }
      const body = await res.json();
      const map: Record<string, boolean> = body.data ?? {};
      const nameById = new Map(mates.map(m => [m.id, m.name] as const));
      const onlineList = Object.entries(map)
        .filter(([, v]) => v)
        .map(([k]) => ({ id: Number(k), name: nameById.get(Number(k)) ?? "?" }));
      const offlineList = Object.entries(map)
        .filter(([, v]) => !v)
        .map(([k]) => ({ id: Number(k), name: nameById.get(Number(k)) ?? "?" }));
      console.log("[presence] status 응답", { online: onlineList, offline: offlineList, raw: map });
      setOnlineIds(prev => {
        const next = new Set(prev);
        for (const [k, v] of Object.entries(map)) {
          const id = Number(k);
          if (v) next.add(id);
          else next.delete(id);
        }
        return next;
      });
    } catch (error) {
      console.warn("[presence] status 조회 오류", error);
    }
  }, []);

  useEffect(() => {
    console.log(
      `[presence] currentUser id=${currentUser.id} name=${currentUser.name} · trip.members=`,
      trip.members.map(m => ({ id: m.id, name: m.name, isAdmin: m.isAdmin ?? false })),
    );
  }, [currentUser.id, currentUser.name, trip.members]);

  useEffect(() => {
    if (items.length === 0) return;
    const ids = items.map(m => m.id);
    (async () => { await refreshOnline(ids, items); })();
    const interval = setInterval(() => { refreshOnline(ids, items); }, 20_000);
    return () => clearInterval(interval);
  }, [items, refreshOnline]);

  const toggle = (mateId: number) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(mateId)) next.delete(mateId);
      else next.add(mateId);
      return next;
    });
  };

  const invite = async () => {
    if (!isAdmin || selectedIds.size === 0 || inviting) return;
    setInviting(true);
    try {
      const res = await apiFetch(`${API_BASE}/api/v1/trips/${trip.id}/members/invite`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ memberIds: Array.from(selectedIds) }),
      });
      if (!res.ok) throw new Error("초대 실패");
      setSelectedIds(new Set());
      onInvited();
    } catch (e) {
      console.error("[지난 메이트 초대 실패]", e);
    } finally {
      setInviting(false);
    }
  };

  return (
    <div className="flex flex-col gap-3 min-h-0">
      <input
        className="w-full p-3 bg-gray-100 rounded-xl text-sm outline-none"
        placeholder="이름으로 지난 메이트 검색"
        value={keyword}
        onChange={e => setKeyword(e.target.value)}
      />
      {!isAdmin && (
        <p className="text-xs text-gray-500 bg-orange-50 rounded-xl px-3 py-2">
          방장만 지난 메이트를 초대할 수 있어요. 조회는 가능합니다.
        </p>
      )}
      <div className="flex flex-col gap-2 max-h-[45vh] overflow-y-auto">
        {initialLoading ? (
          <p className="text-sm text-gray-400 text-center py-6">불러오는 중...</p>
        ) : items.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-6">
            {debouncedKeyword ? "검색 결과가 없습니다." : "함께 여행한 메이트가 없습니다."}
          </p>
        ) : (
          items.map(mate => {
            const alreadyMember = existingMemberIds.has(mate.id);
            const selected = selectedIds.has(mate.id);
            return (
              <button
                key={mate.id}
                type="button"
                onClick={() => !alreadyMember && toggle(mate.id)}
                disabled={alreadyMember}
                className={`w-full p-3 rounded-xl flex items-center gap-3 text-left transition ${
                  selected
                    ? "bg-blue-50 border border-blue-500"
                    : alreadyMember
                      ? "bg-gray-50 opacity-50 cursor-not-allowed border border-transparent"
                      : "bg-gray-50 border border-transparent"
                }`}
              >
                <div className={`w-5 h-5 rounded-md border-2 flex items-center justify-center shrink-0 ${
                  selected ? "bg-blue-500 border-blue-500" : "border-gray-300"
                }`}>
                  {selected && (
                    <svg className="w-3 h-3 text-white" fill="none" stroke="currentColor" strokeWidth={3} viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5">
                    <p className="text-sm font-semibold truncate">{mate.name}</p>
                    {onlineIds.has(mate.id) && (
                      <span className="inline-flex items-center gap-1 shrink-0" aria-label="접속중">
                        <span className="w-1.5 h-1.5 rounded-full bg-green-500" />
                        <span className="text-[11px] font-semibold text-green-600">접속중</span>
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-gray-400 mt-0.5">
                    최근 함께한 여행 {formatPastMateLabel(mate)}
                  </p>
                </div>
                {alreadyMember && (
                  <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 shrink-0">
                    이미 참여
                  </span>
                )}
              </button>
            );
          })
        )}
        {hasNext && (
          <div ref={sentinelRef} className="py-3 text-center text-xs text-gray-400">
            {loading ? "더 불러오는 중..." : ""}
          </div>
        )}
      </div>
      <button
        type="button"
        onClick={invite}
        disabled={!isAdmin || selectedIds.size === 0 || inviting}
        className="w-full py-3.5 rounded-2xl text-sm font-semibold bg-blue-500 text-white disabled:opacity-40"
      >
        {inviting
          ? "초대 중..."
          : `여행방에 초대하기${selectedIds.size > 0 ? ` (${selectedIds.size}명)` : ""}`}
      </button>
    </div>
  );
}

function InviteSheet({
  trip,
  onClose,
  onMembersInvited,
}: {
  trip: Trip;
  onClose: () => void;
  onMembersInvited: () => void;
}) {
  const { currentUser } = useStore();
  const [copied, setCopied] = useState(false);
  const [origin, setOrigin] = useState("");
  const [subTab, setSubTab] = useState<"link" | "past">("link");
  useEffect(() => { setOrigin(window.location.origin); }, []);
  const code = trip.inviteCode;
  const inviteLink = `${origin}/invite/${code}`;
  const isAdmin = trip.members.some(
    m => Number(m.id) === Number(currentUser.id) && m.isAdmin,
  );

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
    <AnimatedBottomSheet onClose={onClose} className="p-6 flex flex-col gap-4 max-h-[90vh]">
      {(close) => (
        <>
        <div className="flex items-center justify-between">
          <p className="text-lg font-bold">멤버 초대</p>
          <button onClick={close} className="text-blue-500 font-medium">닫기</button>
        </div>

        <div className="flex gap-1 p-1 bg-gray-100 rounded-xl">
          <button
            type="button"
            onClick={() => setSubTab("link")}
            className={`flex-1 py-2 rounded-lg text-sm font-semibold transition ${
              subTab === "link" ? "bg-white text-gray-900 shadow-sm" : "text-gray-500"
            }`}
          >
            링크 초대
          </button>
          <button
            type="button"
            onClick={() => setSubTab("past")}
            className={`flex-1 py-2 rounded-lg text-sm font-semibold transition ${
              subTab === "past" ? "bg-white text-gray-900 shadow-sm" : "text-gray-500"
            }`}
          >
            지난 메이트 초대
          </button>
        </div>

        {subTab === "link" ? (
          <>
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
        ) : (
          <PastMatesInvitePanel trip={trip} isAdmin={isAdmin} onInvited={onMembersInvited} />
        )}
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
          category: selected.category_group_name || "",
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
        <div className="flex flex-col gap-4 px-4 pb-4">
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
  const { currentUser } = useStore();
  const [showAdd, setShowAdd] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null);
  const canRegister = tripStatus === "before";
  const deleteTarget = trip.candidates.find(c => c.id === deleteTargetId) ?? null;

  const handleDeleteCandidate = async (tripPlaceId: string) => {
    setDeleteError("");
    try {
      const res = await apiFetch(`${API_BASE}/api/v1/trips/${trip.id}/wish-places/${tripPlaceId}`, {
        method: "DELETE",
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setDeleteError(body?.message ?? "후보 장소를 삭제하지 못했습니다.");
        return;
      }
      onUpdate({ ...trip, candidates: trip.candidates.filter(c => c.id !== tripPlaceId) });
    } catch (e) {
      console.error("[후보 삭제 실패]", e);
      setDeleteError("후보 장소를 삭제하지 못했습니다.");
    }
  };

  const confirmDeleteCandidate = () => {
    if (!deleteTargetId) return;
    const targetId = deleteTargetId;
    setDeleteTargetId(null);
    handleDeleteCandidate(targetId);
  };

  return (
    <div className="candidate-pool-card candidate-section-panel pt-3 pb-0 flex flex-col gap-4 rounded-2xl">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-lg font-bold">후보 장소</p>
          <p className="mt-1 text-sm text-gray-500">일차와 상관없이 여행 전체에서 사용할 후보를 올립니다.</p>
        </div>
        <span className="ml-3 shrink-0 pt-1 text-sm font-bold text-gray-500">{trip.candidates.length}개</span>
      </div>

      {canRegister ? (
        <button
          onClick={() => setShowAdd(true)}
          className="candidate-add-button min-h-12 w-full rounded-2xl px-4 py-3.5 text-base font-semibold"
        >
          + 후보 올리기
        </button>
      ) : (
        <div className="candidate-closed-notice flex min-h-12 w-full items-center justify-center rounded-2xl px-4 py-3.5 text-center text-base font-semibold">
          여행이 시작되어 후보 등록이 마감되었습니다
        </div>
      )}

      {deleteError && <p className="text-xs text-red-500">{deleteError}</p>}

      {trip.candidates.length === 0 ? (
        <div className="candidate-empty-card flex min-h-28 items-center gap-3 rounded-2xl p-4">
          <span className="shrink-0 text-2xl">📍</span>
          <div>
            <p className="text-base font-semibold">아직 후보 장소가 없습니다.</p>
            <p className="mt-1 text-sm leading-5 text-gray-500">후보를 올린 뒤 각 일차의 시간 구간에서 투표나 랜덤으로 선택합니다.</p>
          </div>
        </div>
      ) : (
        <div className="candidate-list-scroll flex flex-col gap-3">
          {trip.candidates.map(c => (
            <div key={c.id} className="candidate-list-item flex items-center gap-3 rounded-2xl p-4">
              <span className="shrink-0 text-xl text-green-600">📍</span>
              <div className="flex-1 min-w-0">
                <p className="truncate text-base font-semibold">{c.placeName}</p>
                <p className="mt-0.5 truncate text-sm text-gray-400">{c.address}</p>
                <p className="mt-0.5 text-xs text-gray-400">등록자 {c.authorName}</p>
              </div>
              {canRegister && c.authorId === currentUser.id && (
                <button
                  onClick={() => setDeleteTargetId(c.id)}
                  aria-label="희망장소 삭제"
                  className="shrink-0 text-xs text-gray-400"
                >
                  삭제
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {deleteTarget && (
        <FixedBottomPortal>
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setDeleteTargetId(null)}>
            <div className="bg-white rounded-2xl shadow-xl p-6 mx-6 flex flex-col gap-4" onClick={e => e.stopPropagation()}>
              <p className="font-bold text-base">후보 장소 삭제</p>
              <p className="text-sm text-gray-600">&apos;{deleteTarget.placeName}&apos;을(를) 삭제하시겠습니까?</p>
              <div className="flex gap-2">
                <button
                  onClick={() => setDeleteTargetId(null)}
                  className="flex-1 py-2.5 rounded-xl text-sm font-semibold bg-gray-100 text-gray-600"
                >
                  취소
                </button>
                <button
                  onClick={confirmDeleteCandidate}
                  className="flex-1 py-2.5 rounded-xl text-sm font-semibold"
                  style={{ background: "#fee2e2", color: "#dc2626" }}
                >
                  삭제
                </button>
              </div>
            </div>
          </div>
        </FixedBottomPortal>
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
  timelineId: number;
  confirmedPlaceName: string;
  startTime: string;
  voteStatus?: string;
}
interface VoteDay {
  date: string;
  timeLines: VoteTimeline[];
}

interface DayTimelineItem {
  timelineId: number;
  startTime: string;
  endTime: string;
  confirmedPlaceName?: string | null;
  category?: string | null;
  isFreeTime?: boolean;
}

const visibleTimelineItemsForDay = (
  day: TripDay,
  items: DayTimelineItem[],
): DayTimelineItem[] => {
  const plannedItems = items.filter(item => !item.isFreeTime);
  if (plannedItems.length > 0) return plannedItems;

  return [
    {
      timelineId: -day.dayNumber,
      startTime: `${day.date}T00:00:00`,
      endTime: `${day.date}T23:59:00`,
      confirmedPlaceName: "자유시간",
      category: "기타",
      isFreeTime: true,
    },
  ];
};

interface DayFreeTimeSetting {
  dayNumber: number;
  freeTimeMinutes: number;
}

interface TripGroupSettingsPayload {
  tripGroupId: number;
  days: DayFreeTimeSetting[];
  editable: boolean;
  isAnonymousVote: boolean;
}

function DayFreeTimeRangeControl({
  dayNumber,
  minutes,
  saving,
  error,
  onChange,
}: {
  dayNumber: number;
  minutes: number;
  saving: boolean;
  error?: string;
  onChange: (change: number) => void;
}) {
  return (
    <div className="day-free-time-control border-t border-gray-100 pt-3">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-xs font-bold">자유시간 범위</p>
          <p className="mt-0.5 text-[11px] text-gray-400">
            방장 전용 · 여행 시작 후 고정
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onChange(-30)}
            disabled={minutes <= 30 || saving}
            className="settings-step-button flex h-9 w-9 items-center justify-center rounded-full bg-gray-100 text-base font-bold disabled:cursor-not-allowed disabled:opacity-35"
            aria-label={`${dayNumber}일차 자유시간 범위 30분 줄이기`}
          >
            −
          </button>
          <output className="w-20 text-center text-xs font-bold text-blue-600">
            {saving ? "저장 중" : durationText(minutes)}
          </output>
          <button
            type="button"
            onClick={() => onChange(30)}
            disabled={minutes >= 180 || saving}
            className="settings-step-button flex h-9 w-9 items-center justify-center rounded-full bg-gray-100 text-base font-bold disabled:cursor-not-allowed disabled:opacity-35"
            aria-label={`${dayNumber}일차 자유시간 범위 30분 늘리기`}
          >
            +
          </button>
        </div>
      </div>
      {error && <p className="mt-2 text-[11px] font-semibold text-red-500">{error}</p>}
    </div>
  );
}

function BulkFreeTimeRangeSheet({
  initialMinutes,
  onApply,
  onClose,
}: {
  initialMinutes: number;
  onApply: (freeTimeMinutes: number) => Promise<void>;
  onClose: () => void;
}) {
  const [minutes, setMinutes] = useState(initialMinutes);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const changeMinutes = (change: number) => {
    setMinutes(current => Math.min(180, Math.max(30, current + change)));
    setError("");
  };

  const applyToAllDays = async (close: () => void) => {
    if (saving) return;

    setSaving(true);
    setError("");
    try {
      await onApply(minutes);
      close();
    } catch (applyError) {
      setError(
        applyError instanceof Error
          ? applyError.message
          : "자유시간 범위를 일괄 적용하지 못했습니다.",
      );
      setSaving(false);
    }
  };

  return (
    <AnimatedBottomSheet
      onClose={onClose}
      className="flex flex-col px-5 pb-6"
    >
      {(close) => (
        <>
          <p className="text-center text-xs text-gray-400">모든 일차에 동일한 범위를 적용합니다.</p>

          <div className="mt-4 flex items-center justify-center gap-5 rounded-2xl bg-gray-50 px-4 py-5">
            <button
              type="button"
              onClick={() => changeMinutes(-30)}
              disabled={minutes <= 30 || saving}
              className="settings-step-button flex h-11 w-11 items-center justify-center rounded-full bg-gray-100 text-xl font-bold disabled:cursor-not-allowed disabled:opacity-35"
              aria-label="전체 자유시간 범위 30분 줄이기"
            >
              −
            </button>
            <output className="w-24 text-center text-base font-bold text-blue-600">
              {durationText(minutes)}
            </output>
            <button
              type="button"
              onClick={() => changeMinutes(30)}
              disabled={minutes >= 180 || saving}
              className="settings-step-button flex h-11 w-11 items-center justify-center rounded-full bg-gray-100 text-xl font-bold disabled:cursor-not-allowed disabled:opacity-35"
              aria-label="전체 자유시간 범위 30분 늘리기"
            >
              +
            </button>
          </div>

          {error && (
            <p className="mt-3 text-center text-xs font-semibold text-red-500">{error}</p>
          )}

          <button
            type="button"
            onClick={() => applyToAllDays(close)}
            disabled={saving}
            className="mt-5 w-full rounded-2xl bg-blue-500 py-4 font-semibold text-white disabled:opacity-50"
          >
            {saving ? "적용 중..." : "모두 적용"}
          </button>
        </>
      )}
    </AnimatedBottomSheet>
  );
}

const VOTE_SYNC_EVENT_TYPES = new Set([
  "TIMELINE_CREATED",
  "TIMELINE_BATCH_CREATED",
  "TIMELINE_TIME_UPDATED",
  "TIMELINE_DELETED",
  "VOTE_CREATED",
  "VOTE_PARTICIPATION_UPDATED",
  "TIMELINE_PLACE_CONFIRMED",
  "VOTE_EXPIRED",
]);

const TRIP_OVERVIEW_SYNC_EVENT_TYPES = new Set([
  "TRIP_MEMBER_JOINED",
]);

const CANDIDATE_SYNC_EVENT_TYPES = new Set([
  "WISH_PLACE_ADDED",
  "WISH_PLACE_DELETED",
]);

function HeaderSyncButton({
  pending,
  loading,
  onClick,
  pendingLabel,
  positionClass = "trip-room-header-action-0",
}: {
  pending: boolean;
  loading: boolean;
  onClick: () => void;
  pendingLabel: string;
  positionClass?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={!pending || loading}
      aria-label={pending ? pendingLabel : "동기화할 변경 사항 없음"}
      title={pending ? pendingLabel : "동기화할 변경 사항 없음"}
      className={`timeline-sync-icon-button trip-room-header-control absolute ${positionClass} z-10 w-10 h-10 rounded-full flex items-center justify-center ${
        pending ? "is-pending" : "is-idle"
      }`}
    >
      <ArrowClockwise
        size={19}
        weight="bold"
      />
    </button>
  );
}

export default function TripDetailPage() {
  useAuthGuard();
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { trips, updateTrip, upsertTrip, currentUser } = useStore();
  const { latestEvent } = useTripEvent();
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
  const [tripSyncPending, setTripSyncPending] = useState(false);
  const [candidateSyncPending, setCandidateSyncPending] = useState(false);
  const [voteSyncPending, setVoteSyncPending] = useState(false);
  const [tripSyncLoading, setTripSyncLoading] = useState(false);
  const [candidateSyncLoading, setCandidateSyncLoading] = useState(false);
  const [voteSyncLoading, setVoteSyncLoading] = useState(false);
  const [allDayTimelines, setAllDayTimelines] = useState<Record<number, DayTimelineItem[]>>({});
  const [freeTimeMinutesByDay, setFreeTimeMinutesByDay] = useState<Record<number, number>>({});
  const [freeTimeSettingsEditable, setFreeTimeSettingsEditable] = useState(false);
  const [savingFreeTimeDay, setSavingFreeTimeDay] = useState<number | null>(null);
  const [freeTimeErrorByDay, setFreeTimeErrorByDay] = useState<Record<number, string>>({});
  const [showBulkFreeTimeSheet, setShowBulkFreeTimeSheet] = useState(false);
  const [bulkFreeTimeInitialMinutes, setBulkFreeTimeInitialMinutes] = useState(60);
  const [showVoteDefaultMenu, setShowVoteDefaultMenu] = useState(false);
  const [voteDefaultMenuClosing, setVoteDefaultMenuClosing] = useState(false);
  const [isAnonymousVoteDefault, setIsAnonymousVoteDefault] = useState(true);
  const voteDefaultMenuCloseTimerRef = useRef<number | null>(null);
  const voteDefaultMenuRef = useRef<HTMLDivElement>(null);
  const trip = trips.find(t => t.id === id);

  useEffect(() => {
    return () => {
      if (voteDefaultMenuCloseTimerRef.current != null) {
        window.clearTimeout(voteDefaultMenuCloseTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (!showVoteDefaultMenu) return;
    const handleOutsidePointerDown = (event: Event) => {
      if (!voteDefaultMenuRef.current?.contains(event.target as Node)) {
        closeVoteDefaultMenu();
      }
    };
    document.addEventListener("mousedown", handleOutsidePointerDown);
    document.addEventListener("touchstart", handleOutsidePointerDown);
    return () => {
      document.removeEventListener("mousedown", handleOutsidePointerDown);
      document.removeEventListener("touchstart", handleOutsidePointerDown);
    };
  }, [showVoteDefaultMenu]);

  const closeVoteDefaultMenu = () => {
    if (!showVoteDefaultMenu || voteDefaultMenuClosing) return;

    setVoteDefaultMenuClosing(true);
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    voteDefaultMenuCloseTimerRef.current = window.setTimeout(() => {
      setShowVoteDefaultMenu(false);
      setVoteDefaultMenuClosing(false);
      voteDefaultMenuCloseTimerRef.current = null;
    }, prefersReducedMotion ? 0 : 200);
  };

  const toggleVoteDefaultMenu = () => {
    if (tripStatus !== "before") return;
    if (voteDefaultMenuClosing) return;
    if (showVoteDefaultMenu) {
      closeVoteDefaultMenu();
      return;
    }

    setVoteDefaultMenuClosing(false);
    setShowVoteDefaultMenu(true);
  };

  const toggleAnonymousVoteDefault = async () => {
    if (tripStatus !== "before") return;
    const previous = isAnonymousVoteDefault;
    const next = !previous;
    setIsAnonymousVoteDefault(next);

    try {
      const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/settings`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ isAnonymousVote: next }),
      });
      const body = await response.json();
      if (!response.ok) {
        throw new Error(body?.message ?? "익명 투표 설정을 저장하지 못했습니다.");
      }
      const settings = body.data as TripGroupSettingsPayload;
      setIsAnonymousVoteDefault(settings.isAnonymousVote);
    } catch (error) {
      console.error("[익명 투표 설정 변경 실패]", error);
      setIsAnonymousVoteDefault(previous);
    }
  };

  const fetchVoteData = useCallback(async () => {
    const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/votes`);
    if (!response.ok) throw new Error("투표 목록을 불러오지 못했습니다.");
    const body = await response.json();
    setVoteData(body.data ?? []);
  }, [id]);

  const syncVoteData = async () => {
    if (!voteSyncPending || voteSyncLoading) return;

    setVoteSyncLoading(true);
    try {
      await fetchVoteData();
      setVoteSyncPending(false);
    } catch (error) {
      console.error("[투표 목록 동기화 실패]", error);
    } finally {
      setVoteSyncLoading(false);
    }
  };

  const tripStatus = (() => {
    if (!trip) return "before";
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const startDate = new Date(trip.startDate); startDate.setHours(0, 0, 0, 0);
    const endDate = new Date(trip.startDate); endDate.setDate(endDate.getDate() + trip.nights); endDate.setHours(23, 59, 59, 999);
    return today < startDate ? "before" : today > endDate ? "after" : "during";
  })();

  const fetchTripOverview = useCallback(async () => {
    if (!id) return;

    const [tripBody, countBody] = await Promise.all([
      apiFetch(`${API_BASE}/api/v1/trips/${id}`).then(r => r.json()),
      apiFetch(`${API_BASE}/api/v1/trips/${id}/timelines/count`).then(r => r.json()),
    ]);
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
      name: tripData.name,
      region: tripData.region,
      startDate: tripData.startDate,
      nights: tripData.nights,
      inviteCode,
      members,
      days: updatedDays,
    });
  }, [id, trip, setOwnerId, upsertTrip]);

  useEffect(() => {
    fetchTripOverview()
      .then(() => setTripSyncPending(false))
      .catch(() => {});
  }, [id]);

  useEffect(() => {
    if (!id) return;

    const controller = new AbortController();
    apiFetch(`${API_BASE}/api/v1/trips/${id}/settings`, {
      signal: controller.signal,
    })
      .then(async response => {
        const body = await response.json();
        if (!response.ok) {
          throw new Error(body?.message ?? "자유시간 설정을 불러오지 못했습니다.");
        }
        return body.data as TripGroupSettingsPayload;
      })
      .then(settings => {
        setFreeTimeMinutesByDay(
          Object.fromEntries(
            settings.days.map(day => [day.dayNumber, day.freeTimeMinutes]),
          ),
        );
        setFreeTimeSettingsEditable(settings.editable);
        setIsAnonymousVoteDefault(settings.isAnonymousVote);
      })
      .catch(error => {
        if (!controller.signal.aborted) {
          console.error("[자유시간 설정 조회 실패]", error);
        }
      });

    return () => controller.abort();
  }, [id]);

  const changeDayFreeTimeMinutes = async (
    dayNumber: number,
    change: number,
  ) => {
    if (!freeTimeSettingsEditable || savingFreeTimeDay != null) return;

    const currentMinutes = freeTimeMinutesByDay[dayNumber] ?? 60;
    const nextMinutes = Math.min(180, Math.max(30, currentMinutes + change));
    if (nextMinutes === currentMinutes) return;

    setSavingFreeTimeDay(dayNumber);
    setFreeTimeErrorByDay(current => ({
      ...current,
      [dayNumber]: "",
    }));

    try {
      const response = await apiFetch(
        `${API_BASE}/api/v1/trips/${id}/settings/free-time/${dayNumber}`,
        {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ freeTimeMinutes: nextMinutes }),
        },
      );
      const body = await response.json();
      if (!response.ok) {
        throw new Error(body?.message ?? "자유시간 범위를 저장하지 못했습니다.");
      }

      const settings = body.data as TripGroupSettingsPayload;
      setFreeTimeMinutesByDay(
        Object.fromEntries(
          settings.days.map(day => [day.dayNumber, day.freeTimeMinutes]),
        ),
      );
    } catch (error) {
      setFreeTimeErrorByDay(current => ({
        ...current,
        [dayNumber]: error instanceof Error
          ? error.message
          : "자유시간 범위를 저장하지 못했습니다.",
      }));
    } finally {
      setSavingFreeTimeDay(null);
    }
  };

  const openBulkFreeTimeSheet = () => {
    if (!trip || !freeTimeSettingsEditable || tripStatus !== "before") return;

    const daySettings = trip.days
      .map(day => freeTimeMinutesByDay[day.dayNumber])
      .filter((minutes): minutes is number => minutes != null);
    const firstMinutes = daySettings[0] ?? 60;
    const allDaysUseSameMinutes = daySettings.every(minutes => minutes === firstMinutes);

    setBulkFreeTimeInitialMinutes(allDaysUseSameMinutes ? firstMinutes : 60);
    setShowBulkFreeTimeSheet(true);
  };

  const applyFreeTimeMinutesToAllDays = async (freeTimeMinutes: number) => {
    if (!freeTimeSettingsEditable || tripStatus !== "before") {
      throw new Error("여행 시작 전 방장만 일괄 설정할 수 있습니다.");
    }

    const response = await apiFetch(
      `${API_BASE}/api/v1/trips/${id}/settings/free-time`,
      {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ freeTimeMinutes }),
      },
    );
    const body = await response.json();
    if (!response.ok) {
      throw new Error(body?.message ?? "자유시간 범위를 일괄 적용하지 못했습니다.");
    }

    const settings = body.data as TripGroupSettingsPayload;
    setFreeTimeMinutesByDay(
      Object.fromEntries(
        settings.days.map(day => [day.dayNumber, day.freeTimeMinutes]),
      ),
    );
    setFreeTimeErrorByDay({});
  };

  const fetchAllDayTimelineData = useCallback(async () => {
    if (!trip || tripStatus === "before" || trip.days.length === 0) return;
    const results = await Promise.all(
      trip.days.map(day =>
        apiFetch(`${API_BASE}/api/v1/trips/${id}/timelines?dayNumber=${day.dayNumber}`)
          .then(r => r.json())
          .then(body => ({ dayNumber: day.dayNumber, items: (body.data ?? []) as DayTimelineItem[] }))
          .catch(() => ({ dayNumber: day.dayNumber, items: [] }))
      )
    );
    const map: Record<number, DayTimelineItem[]> = {};
    results.forEach(({ dayNumber, items }) => { map[dayNumber] = items; });
    setAllDayTimelines(map);
  }, [id, trip, tripStatus]);

  useEffect(() => {
    fetchAllDayTimelineData().catch(() => {});
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
    fetchVoteData()
      .then(() => setVoteSyncPending(false))
      .catch(() => setVoteData([]));
  }, [tab, id, fetchVoteData]);

  const fetchCandidates = useCallback(async () => {
    if (!id || !trip) return;
    const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/wish-places`);
    if (!response.ok) throw new Error("후보 장소를 불러오지 못했습니다.");
    const body = await response.json();
    const wishes: { tripPlaceId: number; name: string; address: string; category: string; createdBy: string; createdByMemberId: number }[] = body.data ?? [];
    const candidates = wishes.map(w => ({
      id: String(w.tripPlaceId),
      authorId: w.createdByMemberId,
      authorName: w.createdBy,
      placeName: w.name,
      address: w.address,
      category: w.category,
    }));
    updateTrip({ ...trip, candidates });
  }, [id, trip, updateTrip]);

  useEffect(() => {
    if (tab !== "candidates") return;
    fetchCandidates()
      .then(() => setCandidateSyncPending(false))
      .catch(() => {});
  }, [tab, id, trip?.id]);

  useEffect(() => {
    if (!latestEvent) return;

    const pendingTimer = window.setTimeout(() => {
      if (TRIP_OVERVIEW_SYNC_EVENT_TYPES.has(latestEvent.eventType)) {
        setTripSyncPending(true);
      }
      if (CANDIDATE_SYNC_EVENT_TYPES.has(latestEvent.eventType)) {
        setCandidateSyncPending(true);
      }
      if (VOTE_SYNC_EVENT_TYPES.has(latestEvent.eventType)) {
        setVoteSyncPending(true);
      }
    }, 0);
    return () => window.clearTimeout(pendingTimer);
  }, [latestEvent]);

  const syncTripOverview = async () => {
    if (!tripSyncPending || tripSyncLoading) return;

    setTripSyncLoading(true);
    try {
      await fetchTripOverview();
      setTripSyncPending(false);
    } catch (error) {
      console.error("[여행방 정보 동기화 실패]", error);
    } finally {
      setTripSyncLoading(false);
    }
  };

  const syncCandidates = async () => {
    if (!candidateSyncPending || candidateSyncLoading) return;

    setCandidateSyncLoading(true);
    try {
      await fetchCandidates();
      setCandidateSyncPending(false);
    } catch (error) {
      console.error("[후보 장소 동기화 실패]", error);
    } finally {
      setCandidateSyncLoading(false);
    }
  };

  if (!trip) {
    return (
      <div className="flex min-h-[100dvh] items-center justify-center">
        <p className="text-gray-400 text-sm">불러오는 중...</p>
      </div>
    );
  }

  return (
    <div className={`min-h-[100dvh] ${tab === "trip" || tab === "candidates" || tab === "vote" ? "trip-detail-page" : "pb-24"} ${isReturningHome || isNavigatingAway ? "trip-page-exit" : ""}`}>
      {/* Header */}
      <div className="trip-room-header relative shrink-0 pb-2">
        <button
          onClick={goTripList}
          aria-label="여행방 목록"
          className="trip-header-icon-button trip-room-header-control trip-room-header-left-control absolute z-10 w-10 h-10 rounded-full flex items-center justify-center"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth={2.2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 10.75 12 4l8.25 6.75" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M5.75 9.75V20h12.5V9.75" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 20v-5.25h4.5V20" />
          </svg>
        </button>
        <TripEventHeaderNotice
          className={`trip-detail-event-header trip-room-header-control absolute inset-x-0 h-10 ${
            tab === "trip"
              ? "has-triple-actions"
              : tab === "candidates" || tab === "vote"
                ? "has-double-actions"
                : ""
          }`}
        >
          <div className="min-w-0 text-center">
            <p className="truncate font-semibold text-base">{trip.name}</p>
            <p className="truncate text-xs text-gray-400">{trip.region} · {trip.nights}박 {trip.nights + 1}일</p>
          </div>
        </TripEventHeaderNotice>
        {tab === "trip" ? (
          <>
            <HeaderSyncButton
              pending={tripSyncPending}
              loading={tripSyncLoading}
              onClick={syncTripOverview}
              pendingLabel="새로 참여한 여행 멤버 동기화"
              positionClass="trip-room-header-action-2"
            />
            <TripChatRoomButton className="trip-room-header-control trip-room-header-action-1 absolute z-10" />
            <button
              onClick={() => setShowInvite(true)}
              aria-label="초대 링크"
              className="trip-header-icon-button trip-room-header-control trip-room-header-action-0 absolute z-10 w-10 h-10 rounded-full flex items-center justify-center"
            >
              <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
              </svg>
            </button>
          </>
        ) : tab === "candidates" ? (
          <>
            <HeaderSyncButton
              pending={candidateSyncPending}
              loading={candidateSyncLoading}
              onClick={syncCandidates}
              pendingLabel="변경된 후보 장소 동기화"
              positionClass="trip-room-header-action-1"
            />
            <TripChatRoomButton className="trip-room-header-control trip-room-header-action-0 absolute z-10" />
          </>
        ) : tab === "vote" ? (
          <>
            <HeaderSyncButton
              pending={voteSyncPending}
              loading={voteSyncLoading}
              onClick={syncVoteData}
              pendingLabel="변경된 투표 목록 동기화"
              positionClass="trip-room-header-action-1"
            />
            <TripChatRoomButton className="trip-room-header-control trip-room-header-action-0 absolute z-10" />
          </>
        ) : tab === "timeline" && tripStatus === "during" ? (
          <Link
            href={`/trip/${id}/timeline?from=timeline`}
            onClick={navigateWithPageExit(`/trip/${id}/timeline?from=timeline`)}
            className="trip-room-header-text-action trip-room-header-action-0 absolute z-10 text-xs font-semibold text-blue-500"
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
        className={`app-safe-inline trip-page-transition pt-2 flex flex-col gap-5 ${tab === "trip" || tab === "candidates" || tab === "vote" ? "trip-tab-content" : ""}`}
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
              <div className="flex items-center justify-between">
                <p className="font-semibold">일차별 계획</p>
                {freeTimeSettingsEditable && tripStatus === "before" && (
                  <button
                    type="button"
                    onClick={openBulkFreeTimeSheet}
                    disabled={savingFreeTimeDay != null}
                    aria-label="자유시간 범위 일괄 설정"
                    title="자유시간 범위 일괄 설정"
                    className="trip-header-icon-button flex h-9 w-9 items-center justify-center rounded-full disabled:cursor-not-allowed disabled:opacity-40"
                  >
                  <Clock size={19} weight="bold" />
                  </button>
                )}
              </div>
              <div className="trip-day-list-scroll flex flex-col gap-3">
                {trip.days.map(day => {
                  if (tripStatus !== "before") {
                    const items = visibleTimelineItemsForDay(
                      day,
                      allDayTimelines[day.dayNumber] ?? [],
                    );
                    return (
                      <div key={day.id} className="shrink-0 p-4 bg-gray-50 rounded-2xl flex flex-col gap-3">
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
                            {items.map(item => {
                              return (
                                <div key={item.timelineId} className="flex items-center gap-3 bg-white rounded-xl px-3 py-2.5">
                                  <span className="text-xs text-gray-400 font-bold shrink-0">{item.startTime.slice(11, 16)} ~ {item.endTime.slice(11, 16)}</span>
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
                    <div key={day.id} className="shrink-0 overflow-hidden rounded-2xl bg-gray-50">
                      <Link
                        href={`/trip/${trip.id}/day/${day.dayNumber}`}
                        onClick={navigateWithPageExit(`/trip/${trip.id}/day/${day.dayNumber}`)}
                        className="block p-4"
                      >
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
                      </Link>
                      {freeTimeSettingsEditable && freeTimeMinutesByDay[day.dayNumber] != null && (
                        <div className="px-4 pb-4">
                          <DayFreeTimeRangeControl
                            dayNumber={day.dayNumber}
                            minutes={freeTimeMinutesByDay[day.dayNumber]}
                            saving={savingFreeTimeDay === day.dayNumber}
                            error={freeTimeErrorByDay[day.dayNumber]}
                            onChange={change => changeDayFreeTimeMinutes(day.dayNumber, change)}
                          />
                        </div>
                      )}
                    </div>
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

          const createVote = async (timelineId: number) => {
            await apiFetch(`${API_BASE}/api/v1/trips/${id}/votes`, {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ timelineId }),
            });
            const r = await apiFetch(`${API_BASE}/api/v1/trips/${id}/votes`);
            const body = await r.json();
            setVoteData(body.data ?? []);
          };

          const isHost = trip.members.some(
            member => Number(member.id) === Number(currentUser.id) && member.isAdmin,
          );

          return (
            <div className="vote-section-panel flex min-h-0 flex-1 flex-col gap-4 overflow-hidden">
              <div className="flex items-center justify-between gap-3">
                <p className="font-semibold">일차별 투표</p>
                {isHost && (
                  <div ref={voteDefaultMenuRef} className="relative shrink-0">
                    <button
                      type="button"
                      onClick={toggleVoteDefaultMenu}
                      disabled={tripStatus !== "before"}
                      aria-expanded={showVoteDefaultMenu && !voteDefaultMenuClosing}
                      aria-haspopup="true"
                      aria-label="방장 투표 설정 열기"
                      title={tripStatus === "before" ? "방장 투표 설정" : "여행 시작 전에만 변경할 수 있어요"}
                      className={`host-badge ${showVoteDefaultMenu ? "is-open" : ""} flex h-10 w-10 items-center justify-center rounded-full border disabled:cursor-not-allowed disabled:opacity-50`}
                    >
                      <CrownSimple size={19} weight="bold" />
                    </button>
                    {showVoteDefaultMenu && (
                      <div className={`host-menu ${voteDefaultMenuClosing ? "is-closing" : ""} absolute right-0 top-12 z-50 w-52 rounded-2xl border p-2 shadow-xl`}>
                          <div
                            className="flex items-center justify-between gap-3 rounded-xl border px-3 py-2.5"
                            style={{
                              backgroundColor: "var(--surface-muted)",
                              borderColor: "var(--border)",
                            }}
                          >
                            <div className="min-w-0">
                              <p className="text-sm font-semibold">익명 투표</p>
                              <p className="mt-0.5 text-[11px] text-gray-400">투표 기본값</p>
                            </div>
                            <button
                              type="button"
                              role="switch"
                              aria-checked={isAnonymousVoteDefault}
                              aria-label="익명 투표 기본값 사용"
                              onClick={toggleAnonymousVoteDefault}
                              className="relative h-6 w-10 shrink-0 rounded-full transition-colors"
                              style={{
                                backgroundColor: isAnonymousVoteDefault ? "#3b82f6" : "#64748b",
                              }}
                            >
                              <span
                                className="absolute left-0 top-[3px] h-[18px] w-[18px] rounded-full transition-transform"
                                style={{
                                  backgroundColor: "#ffffff",
                                  transform: `translateX(${isAnonymousVoteDefault ? 19 : 3}px)`,
                                  boxShadow: "0 1px 3px rgba(15, 23, 42, 0.25)",
                                }}
                              />
                            </button>
                          </div>
                        </div>
                    )}
                  </div>
                )}
              </div>
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
                              <div key={tl.timelineId} className="p-4 bg-gray-50 rounded-2xl flex items-center justify-between">
                                <div className="flex-1 min-w-0">
                                  <p className="text-xs text-gray-400">{toTimeStr(tl.startTime)} 시작</p>
                                  <p className="text-sm font-semibold mt-0.5 text-gray-400">투표 없음</p>
                                </div>
                                <button
                                  onClick={() => createVote(tl.timelineId)}
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
                              href={`/trip/${trip.id}/day/${dayNumber}/block/${tl.voteId}?from=vote&timelineId=${tl.timelineId}`}
                              onClick={navigateWithPageExit(
                                `/trip/${trip.id}/day/${dayNumber}/block/${tl.voteId}?from=vote&timelineId=${tl.timelineId}`,
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
      <FixedBottomPortal>
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
      </FixedBottomPortal>

      {showInvite && (
        <InviteSheet
          trip={trip}
          onClose={() => setShowInvite(false)}
          onMembersInvited={() => {
            fetchTripOverview().catch(() => {});
          }}
        />
      )}
      {showBulkFreeTimeSheet && (
        <BulkFreeTimeRangeSheet
          initialMinutes={bulkFreeTimeInitialMinutes}
          onApply={applyFreeTimeMinutesToAllDays}
          onClose={() => setShowBulkFreeTimeSheet(false)}
        />
      )}
    </div>
  );
}
