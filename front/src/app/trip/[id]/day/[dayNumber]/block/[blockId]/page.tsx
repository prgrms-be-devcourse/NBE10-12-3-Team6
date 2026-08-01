"use client";

import { useCallback, useState, useEffect, useRef } from "react";
import { useRouter, useParams, useSearchParams } from "next/navigation";
import { ArrowClockwise, CrownSimple } from "@phosphor-icons/react";
import { useStore, TripDay, PlanCandidate, uid } from "../../../../../../store";
import { timeText, useAuthGuard, apiFetch, API_BASE } from "../../../../../../lib";
import TripChatRoomButton from "../../../../TripChatRoomButton";
import TripEventHeaderNotice from "../../../../TripEventHeaderNotice";
import { useTripEvent } from "../../../../TripEventProvider";
// ── Types ─────────────────────────────────────────────────────────────────────

interface VoteDetail {
  tripPlaceId: number;
  place: string;
  count: number;
  isVoted: boolean;
}

const VOTE_DETAIL_SYNC_EVENT_TYPES = new Set([
  "WISH_PLACE_ADDED",
  "VOTE_PARTICIPATION_UPDATED",
  "TIMELINE_PLACE_CONFIRMED",
  "VOTE_EXPIRED",
]);

// ── Main ──────────────────────────────────────────────────────────────────────

export default function BlockDetailPage() {
  useAuthGuard();
  const router = useRouter();
  const { id, dayNumber, blockId } = useParams<{ id: string; dayNumber: string; blockId: string }>();
  const searchParams = useSearchParams();
  const goBack = () => router.back();
  const { trips, updateTrip, upsertTrip, currentUser } = useStore();
  const { latestEvent } = useTripEvent();


  const fromVote = searchParams.get("from") === "vote";
  const timelineId = searchParams.get("timelineId");
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [pendingVote, setPendingVote] = useState<string | null>(null);
  const [showHostMenu, setShowHostMenu] = useState(false);
  const [hostMenuClosing, setHostMenuClosing] = useState(false);
  const hostMenuCloseTimerRef = useRef<number | null>(null);
  const [voteDetails, setVoteDetails] = useState<VoteDetail[] | null>(null);
  const [wishPlaces, setWishPlaces] = useState<PlanCandidate[] | null>(null);
  const [blockOrder, setBlockOrder] = useState<number | null>(null);
  const [myVotedPlaceId, setMyVotedPlaceId] = useState<string | null>(null);
  const [updateCount, setUpdateCount] = useState<number>(0);
  const [voteStatus, setVoteStatus] = useState<string | null>(null);
  const [voteConfirmed, setVoteConfirmed] = useState(false);
  const [tieConfirmedName, setTieConfirmedName] = useState<string | null>(null);
  const [confirmedPlaceId, setConfirmedPlaceId] = useState<string | null>(null);
  const [voteLoading, setVoteLoading] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [showConfirmedModal, setShowConfirmedModal] = useState(false);
  const [confirmedModalPresented, setConfirmedModalPresented] = useState(false);
  const [confirmedModalClosing, setConfirmedModalClosing] = useState(false);
  const [voteSyncPending, setVoteSyncPending] = useState(false);
  const [voteSyncLoading, setVoteSyncLoading] = useState(false);
  const [isAnonymousVote, setIsAnonymousVote] = useState(true);

  const trip = trips.find(t => t.id === id);
  const dayNum = parseInt(dayNumber);
  const dayIdx = trip?.days.findIndex(d => d.dayNumber === dayNum) ?? -1;

  useEffect(() => {
    if (trip || !id) return;
    apiFetch(`${API_BASE}/api/v1/trips/${id}`)
      .then(r => r.json())
      .then(body => {
        const tripData = body.data;
        if (!tripData) return;
        upsertTrip({
          id: String(tripData.id), name: tripData.name, region: tripData.region,
          startDate: tripData.startDate, nights: tripData.nights,
          members: (tripData.members ?? []).map((m: { memberId: number; name: string; admin: boolean }, i: number) => ({ id: m.memberId, name: m.name, isAdmin: m.admin, color: ["#f87171","#fb923c","#34d399","#60a5fa","#a78bfa"][i % 5] })),
          days: Array.from({ length: (tripData.nights ?? 0) + 1 }, (_, i) => {
            const d = new Date(tripData.startDate + "T00:00:00");
            d.setDate(d.getDate() + i);
            const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
            return { id: `day-${i + 1}`, dayNumber: i + 1, date: dateStr, blocks: [{ id: uid(), order: 1, theme: "meal" as const, startMinute: 9 * 60, endMinute: 10 * 60 }], isPlanCompleted: false, isPlanSkipped: false, selectedCandidateByBlock: {}, votedUserIDsByBlockAndCandidate: {}, records: [] };
          }),
          candidates: [], inviteCode: tripData.joinCode ?? "", inviteJoinIndex: 0,
        });
      }).catch(() => {});
  }, [id, trip]);

  useEffect(() => {
    if (!fromVote || !blockId) return;
    const saved = localStorage.getItem(`block-order-${blockId}`);
    if (saved) { setBlockOrder(Number(saved)); localStorage.removeItem(`block-order-${blockId}`); }
  }, [blockId]);

  const fetchVoteDetails = useCallback(async (showNewConfirmation = false) => {
    const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/votes/${blockId}/count`);
    if (!response.ok) throw new Error("투표 현황을 불러오지 못했습니다.");
    const body = await response.json();
    const results: VoteDetail[] = body.data?.voteResults ?? [];
    setVoteDetails(results);
    setUpdateCount(body.data?.updateCount ?? 0);
    const status = body.data?.voteStatus ?? null;
    const confirmed = status ? status === "투표 확정" : body.data?.isConfirmed ?? false;
    setVoteStatus(status);
    if (showNewConfirmation) {
      setVoteConfirmed(previous => {
        if (!previous && confirmed) setShowConfirmedModal(true);
        return confirmed;
      });
    } else {
      setVoteConfirmed(confirmed);
    }
    if (body.data?.confirmedPlaceId != null) setConfirmedPlaceId(String(body.data.confirmedPlaceId));
    const voted = results.find(v => v.isVoted);
    setMyVotedPlaceId(voted ? String(voted.tripPlaceId) : null);
    const wishList = (body.data?.wishPlaceFindResponses ?? []).map((w: { tripPlaceId: number; name: string; address: string; category: string; createdBy: string }) => ({
      id: String(w.tripPlaceId),
      authorId: 0,
      authorName: w.createdBy,
      placeName: w.name,
      address: w.address,
      category: w.category,
    }));
    setWishPlaces(wishList);
  }, [id, blockId]);

  useEffect(() => {
    if (!fromVote || !id || !blockId) return;
    fetchVoteDetails()
      .then(() => setVoteSyncPending(false))
      .catch(error => console.error("[투표 현황 조회 실패]", error));
  }, [fromVote, id, blockId, fetchVoteDetails]);

  useEffect(() => {
    if (
      !fromVote ||
      !latestEvent ||
      !VOTE_DETAIL_SYNC_EVENT_TYPES.has(latestEvent.eventType)
    ) {
      return;
    }

    const changedVoteId = latestEvent.voteId == null
      ? null
      : Number(latestEvent.voteId);
    const affectsCurrentVote =
      latestEvent.eventType === "WISH_PLACE_ADDED" ||
      changedVoteId == null ||
      changedVoteId === Number(blockId);
    if (!affectsCurrentVote) return;

    const pendingTimer = window.setTimeout(() => {
      setVoteSyncPending(true);
    }, 0);
    return () => window.clearTimeout(pendingTimer);
  }, [fromVote, latestEvent]);

  useEffect(() => {
    if (!showConfirmedModal) {
      setConfirmedModalPresented(false);
      return;
    }

    setConfirmedModalClosing(false);
    setConfirmedModalPresented(false);
    const frame = requestAnimationFrame(() => setConfirmedModalPresented(true));
    return () => cancelAnimationFrame(frame);
  }, [showConfirmedModal]);

  useEffect(() => {
    return () => {
      if (hostMenuCloseTimerRef.current !== null) {
        window.clearTimeout(hostMenuCloseTimerRef.current);
      }
    };
  }, []);

  if (!trip && !fromVote) return null;

  if (!fromVote && dayIdx < 0) return null;

  const day = trip?.days[dayIdx];
  const block = day?.blocks.find(b => b.id === blockId);
  if (!fromVote && !block) return null;

  const candidatesLoading = fromVote && wishPlaces === null;
  const candidates = fromVote ? (wishPlaces ?? []) : (trip?.candidates ?? []);

  const selectedId = day?.selectedCandidateByBlock[blockId];
  const selected = candidates.find(c => c.id === selectedId);

  const setDay = (updated: TripDay) => {
    if (!trip) return;
    updateTrip({ ...trip, days: trip.days.map((d, i) => i === dayIdx ? updated : d) });
  };

  const voteCount = (candidateId: string): number => {
    if (fromVote) return voteDetails?.find(v => String(v.tripPlaceId) === candidateId)?.count ?? 0;
    return day?.votedUserIDsByBlockAndCandidate[blockId]?.[candidateId]?.length ?? 0;
  };

  const isVoted = (candidateId: string): boolean => {
    if (fromVote) {
      return (voteDetails?.find(v => String(v.tripPlaceId) === candidateId)?.isVoted ?? false) || myVotedPlaceId === candidateId;
    }
    return day?.votedUserIDsByBlockAndCandidate[blockId]?.[candidateId]?.includes(currentUser.id) ?? false;
  };

  const refetchVoteDetails = () => {
    fetchVoteDetails(true).catch(error => {
      console.error("[투표 현황 조회 실패]", error);
    });
  };

  const syncVoteDetails = async () => {
    if (!voteSyncPending || voteSyncLoading) return;

    setVoteSyncLoading(true);
    try {
      await fetchVoteDetails(true);
      setVoteSyncPending(false);
    } catch (error) {
      console.error("[투표 현황 동기화 실패]", error);
    } finally {
      setVoteSyncLoading(false);
    }
  };

  const vote = async (candidateId: string) => {
    if (fromVote) {
      setVoteLoading(true);
      try {
        await apiFetch(`${API_BASE}/api/v1/trips/${id}/votes/${blockId}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ tripPlaceId: Number(candidateId) }),
        });
        setMyVotedPlaceId(candidateId);
        refetchVoteDetails();
      } finally {
        setVoteLoading(false);
      }
      return;
    }
    if (!day) return;
    const blockVotes = { ...(day.votedUserIDsByBlockAndCandidate[blockId] ?? {}) };
    for (const cid of Object.keys(blockVotes)) {
      blockVotes[cid] = (blockVotes[cid] ?? []).filter(uid => uid !== currentUser.id);
    }
    blockVotes[candidateId] = [...(blockVotes[candidateId] ?? []), currentUser.id];
    setDay({ ...day, votedUserIDsByBlockAndCandidate: { ...day.votedUserIDsByBlockAndCandidate, [blockId]: blockVotes } });
  };

  const randomVote = () => {
    if (candidates.length === 0) return;
    const picked = candidates[Math.floor(Math.random() * candidates.length)];
    setPendingVote(null);
    vote(picked.id);
  };

  const randomConfirm = () => {
    if (candidates.length === 0 || !day) return;
    const picked = candidates[Math.floor(Math.random() * candidates.length)];
    setDay({ ...day, selectedCandidateByBlock: { ...day.selectedCandidateByBlock, [blockId]: picked.id } });
    setShowHostMenu(false);
  };

  const tripStarted = (() => {
    if (!trip) return false;
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const startDate = new Date(trip.startDate); startDate.setHours(0, 0, 0, 0);
    return today >= startDate;
  })();

  const tripEnded = (() => {
    if (!trip) return false;
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const endDate = new Date(trip.startDate + "T00:00:00");
    endDate.setDate(endDate.getDate() + trip.nights);
    endDate.setHours(0, 0, 0, 0);
    return today > endDate;
  })();

  const voteClosed = voteStatus !== null && voteStatus !== "투표 진행중";
  const anonymousVoteLocked = voteStatus === "투표 확정" || voteStatus === "투표 기한 만료";

  const isHost = trip?.members.find(m => m.id === currentUser.id)?.isAdmin ?? false;
  const confirmedByVote = voteConfirmed && confirmedPlaceId
    ? candidates.find(c => c.id === confirmedPlaceId)
    : null;
  const displaySelected = selected ?? confirmedByVote ?? null;
  const showCenteredEmpty = voteDetails !== null && !displaySelected && !candidatesLoading && candidates.length === 0;

  const closeHostMenu = (afterClose?: () => void) => {
    if (!showHostMenu || hostMenuClosing) return;

    setHostMenuClosing(true);
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    hostMenuCloseTimerRef.current = window.setTimeout(() => {
      setShowHostMenu(false);
      setHostMenuClosing(false);
      hostMenuCloseTimerRef.current = null;
      afterClose?.();
    }, prefersReducedMotion ? 0 : 200);
  };

  const toggleHostMenu = () => {
    if (tripStarted || hostMenuClosing) return;
    if (showHostMenu) {
      closeHostMenu();
      return;
    }

    setHostMenuClosing(false);
    setShowHostMenu(true);
  };

  const toggleAnonymousVote = () => {
    if (anonymousVoteLocked) return;

    const nextIsAnonymousVote = !isAnonymousVote;
    setIsAnonymousVote(nextIsAnonymousVote);

    // 백엔드 연결 지점:
    // nextIsAnonymousVote를 투표 설정 API에 그대로 저장하고 조회 응답으로 초기화합니다.
  };

  const decideByVote = async () => {
    if (fromVote) {
      setShowHostMenu(false);
      try {
        const res = await apiFetch(`${API_BASE}/api/v1/trips/${id}/votes/${blockId}/confirm`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
        });
        const body = await res.json();
        if (body.data?.isTie) {
          const picked = candidates.find(c => c.id === String(body.data.confirmedPlaceId));
          setTieConfirmedName(picked?.placeName ?? null);
        }
        refetchVoteDetails();
      } catch (e) {
        console.error(e);
      }
      return;
    }
    if (candidates.length === 0 || !day) return;
    const maxVote = Math.max(...candidates.map(c => voteCount(c.id)));
    const winners = candidates.filter(c => voteCount(c.id) === maxVote);
    const winner = winners[Math.floor(Math.random() * winners.length)];
    setDay({ ...day, selectedCandidateByBlock: { ...day.selectedCandidateByBlock, [blockId]: winner.id } });
    setShowHostMenu(false);
  };

  const closeConfirmedModal = () => {
    if (confirmedModalClosing) return;
    setConfirmedModalPresented(false);
    setConfirmedModalClosing(true);
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.setTimeout(() => {
      setShowConfirmedModal(false);
      setConfirmedModalClosing(false);
    }, prefersReducedMotion ? 0 : 220);
  };

  return (
    <div className="trip-page-transition flex h-[100dvh] flex-col">
      {showConfirmModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setShowConfirmModal(false)}>
          <div className="bg-white rounded-2xl shadow-xl p-6 mx-6 flex flex-col gap-4" onClick={e => e.stopPropagation()}>
            <p className="font-bold text-base">투표 확정</p>
            <p className="text-sm text-gray-600">이대로 투표를 확정하시겠습니까?</p>
            <div className="flex gap-2">
              <button
                onClick={() => setShowConfirmModal(false)}
                className="flex-1 py-2.5 rounded-xl text-sm font-semibold bg-gray-100 text-gray-600"
              >
                취소
              </button>
              <button
                onClick={() => { setShowConfirmModal(false); decideByVote(); }}
                className="flex-1 py-2.5 rounded-xl text-sm font-semibold"
                style={{ background: "#dcfce7", color: "#16a34a" }}
              >
                확정
              </button>
            </div>
          </div>
        </div>
      )}
      <div className="app-safe-header flex items-center gap-3 px-4 pb-2">
        <button onClick={goBack} aria-label="뒤로가기" className="trip-header-icon-button w-10 h-10 rounded-full flex items-center justify-center">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <TripEventHeaderNotice className="h-10 flex-1">
          <h1 className="font-semibold text-base text-center">
            {dayNum}일차 {(block?.order ?? blockOrder) ? `${block?.order ?? blockOrder}번째 ` : ""}후보 투표
          </h1>
        </TripEventHeaderNotice>
        <div className="flex shrink-0 items-center gap-3">
          {fromVote ? (
            <button
              type="button"
              onClick={syncVoteDetails}
              disabled={!voteSyncPending || voteSyncLoading}
              aria-label={voteSyncPending ? "변경된 투표 현황 동기화" : "동기화할 변경 사항 없음"}
              title={voteSyncPending ? "변경된 투표 현황 동기화" : "동기화할 변경 사항 없음"}
              className={`timeline-sync-icon-button w-10 h-10 shrink-0 rounded-full flex items-center justify-center ${
                voteSyncPending ? "is-pending" : "is-idle"
              }`}
            >
              <ArrowClockwise
                size={19}
                weight="bold"
              />
            </button>
          ) : (
            <div className="w-10 shrink-0" />
          )}
          <TripChatRoomButton />
        </div>
      </div>

      <div className="flex-1 overflow-y-scroll px-4 pt-2 pb-4 flex flex-col gap-5">
        {/* Header */}
        <div>
          {block && <p className="text-xs text-gray-400 font-bold">{timeText(block.startMinute)} ~ {timeText(block.endMinute)}</p>}
          <div className="mt-1 flex items-center justify-between gap-3">
            <p className="min-w-0 truncate text-2xl font-bold">{dayNum}일차 {(block?.order ?? blockOrder) ? `${block?.order ?? blockOrder}번째 ` : ""}후보 투표</p>
            {isHost && (
              <div className="relative shrink-0">
                <button
                  type="button"
                  onClick={toggleHostMenu}
                  aria-expanded={showHostMenu && !hostMenuClosing}
                  aria-haspopup="true"
                  aria-label="방장 투표 설정 열기"
                  title="방장 투표 설정"
                  className={`host-badge ${showHostMenu ? "is-open" : ""} flex h-10 w-10 items-center justify-center rounded-full border`}
                >
                  <CrownSimple size={19} weight="bold" />
                </button>
                {showHostMenu && (
                  <>
                    <div className="fixed inset-0 z-40" onClick={() => closeHostMenu()} />
                    <div className={`host-menu ${hostMenuClosing ? "is-closing" : ""} absolute right-0 top-12 z-50 flex w-52 flex-col gap-2 rounded-2xl border p-2 shadow-xl`}>
                      <div
                        className="flex items-center justify-between gap-3 rounded-xl border px-3 py-2.5"
                        style={{
                          backgroundColor: "var(--surface-muted)",
                          borderColor: "var(--border)",
                        }}
                      >
                        <div className="min-w-0">
                          <p className="text-sm font-semibold">익명 투표</p>
                          <p className="mt-0.5 text-[11px] text-gray-400">현재 투표 설정</p>
                        </div>
                        <button
                          type="button"
                          role="switch"
                          aria-checked={isAnonymousVote}
                          aria-label="익명 투표 사용"
                          onClick={toggleAnonymousVote}
                          disabled={anonymousVoteLocked}
                          className="relative h-6 w-10 shrink-0 rounded-full transition-colors disabled:cursor-not-allowed"
                          style={{
                            backgroundColor: anonymousVoteLocked
                              ? "#2f333b"
                              : isAnonymousVote
                                ? "#3b82f6"
                                : "#64748b",
                          }}
                        >
                          <span
                            className="absolute left-0 top-[3px] h-[18px] w-[18px] rounded-full transition-transform"
                            style={{
                              backgroundColor: anonymousVoteLocked ? "#6b7280" : "#ffffff",
                              transform: `translateX(${isAnonymousVote ? 19 : 3}px)`,
                              boxShadow: "0 1px 3px rgba(15, 23, 42, 0.25)",
                            }}
                          />
                        </button>
                      </div>
                      <button
                        disabled={candidates.length === 0 || tripStarted || voteClosed || candidates.every(c => voteCount(c.id) === 0)}
                        onClick={() => closeHostMenu(() => setShowConfirmModal(true))}
                        className="confirm-vote-button w-full px-3 py-2.5 rounded-xl text-sm font-semibold text-left disabled:opacity-40"
                      >
                        📊 투표 확정
                      </button>
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Selected */}
        {displaySelected && (
            <div className="confirmed-candidate-card p-4 rounded-2xl">
              <p className={`text-sm font-semibold mb-2 ${tripEnded ? "text-gray-700" : "confirmed-candidate-title"}`}>{tripEnded ? `${displaySelected.placeName} 어떠셨어요? 🥹` : "이 구간에 확정된 후보"}</p>
              {!tripEnded && (
                <div className="flex items-start gap-3">
                  <span className="text-xl mt-0.5 text-green-500">✓</span>
                  <div>
                    <p className="font-bold">{displaySelected.placeName}</p>
                    <p className="text-xs text-gray-500 mt-0.5">{displaySelected.address}</p>
                    <p className="text-xs text-gray-400 mt-0.5">등록자 {displaySelected.authorName}</p>
                  </div>
                </div>
              )}
            </div>
        )}

        {showCenteredEmpty ? (
          <div className="flex-1 min-h-[45vh] flex items-center justify-center text-center px-6">
            <p className="text-base font-semibold text-gray-400">아직 확정된 후보가 없습니다.</p>
          </div>
        ) : (
          <>
            {/* All candidates */}
            <div>
              <div className="flex items-center justify-between mb-3">
                <p className="font-semibold">전체 후보 목록</p>
                <span className="text-xs text-gray-400 font-bold">{candidates.length}개</span>
              </div>

          {/* 동적 카테고리 필터 */}
          {candidates.length > 0 && (() => {
            const namedCategories = Array.from(new Set(candidates.map(c => c.category).filter(Boolean))) as string[];
            const hasUncategorized = candidates.some(c => !c.category);
            const categories = [...namedCategories, ...(hasUncategorized ? ["기타"] : [])];
            if (categories.length === 0) return null;
            return (
              <div className="flex gap-2 overflow-x-auto pb-2 mb-3">
                <button
                  onClick={() => setActiveCategory(null)}
                  className={`vote-category-chip shrink-0 px-3 py-1.5 rounded-full text-xs font-semibold border transition-all ${activeCategory === null ? "is-active" : ""}`}
                >
                  전체
                </button>
                {categories.map(cat => (
                  <button
                    key={cat}
                    onClick={() => setActiveCategory(activeCategory === cat ? null : cat)}
                    className={`vote-category-chip shrink-0 px-3 py-1.5 rounded-full text-xs font-semibold border transition-all ${activeCategory === cat ? "is-active" : ""}`}
                  >
                    {cat}
                  </button>
                ))}
              </div>
            );
          })()}

          {candidatesLoading ? null : candidates.length === 0 ? (
            <div className="p-4 bg-gray-50 rounded-2xl">
              <p className="text-sm text-gray-400">아직 후보가 없습니다. 여행 모임 상세 화면에서 후보를 먼저 올려주세요.</p>
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              {candidates.filter(c => {
                if (!activeCategory) return true;
                if (activeCategory === "기타") return !c.category;
                return c.category === activeCategory;
              }).map(c => {
                const isSelected = c.id === selectedId;
                const voted = isVoted(c.id);
                const cardState = isSelected ? "is-selected" : pendingVote === c.id ? "is-pending" : voted ? "is-voted" : "";
                return (
                  <div
                    key={`${activeCategory ?? "all"}-${c.id}`}
                    onClick={() => { if (!voteClosed) setPendingVote(c.id); }}
                    className={`home-trip-card vote-candidate-card ${cardState} p-4 rounded-2xl border transition-transform ${voteClosed ? "cursor-default" : "cursor-pointer active:scale-[0.98]"}`}
                    style={{ animationDelay: `${candidates.filter(c2 => { if (!activeCategory) return true; if (activeCategory === "기타") return !c2.category; return c2.category === activeCategory; }).indexOf(c) * 40}ms` }}
                  >
                    <div className="flex items-start gap-2 mb-2">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <p className="font-semibold text-sm">{c.placeName}</p>
                          {isSelected && <span className="text-green-500">✓</span>}
                        </div>
                        <p className="text-xs text-gray-400 mt-0.5">{c.address}</p>
                      </div>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-gray-400">등록자 {c.authorName}</span>
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-gray-600">{voteCount(c.id)}표</span>
                        {voted && (
                          <span className="my-vote-badge text-xs font-semibold px-2 py-0.5 rounded-full">
                            내 투표
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
            </div>
          </>
        )}

      </div>

      {/* 하단 고정 버튼 */}
      {voteClosed ? (
        <div className="px-4 py-4 border-t border-gray-100 bg-white">
          <div className="w-full py-4 rounded-2xl text-center font-semibold text-gray-400 bg-gray-100">
            {fromVote
              ? voteStatus === "투표 확정"
                ? "투표가 확정되었습니다"
                : "투표 기한이 만료되었습니다"
              : voteConfirmed
                ? "투표가 종료되었습니다"
                : "여행이 시작되어 투표가 마감되었습니다"}
          </div>
        </div>
      ) : (
        <div className="px-4 py-4 border-t border-gray-100 flex gap-2 bg-white">
          <button
            onClick={randomVote}
            disabled={candidates.length === 0 || updateCount >= 2}
            className="vote-random-button flex-1 py-4 rounded-2xl font-semibold disabled:opacity-40"
          >
            랜덤 투표
          </button>
          <button
            onClick={() => { if (pendingVote && !voteLoading) { vote(pendingVote); setPendingVote(null); } }}
            disabled={!pendingVote || voteLoading || updateCount >= 2}
            className="vote-submit-button flex-1 py-4 rounded-2xl font-semibold disabled:opacity-40"
          >
            {voteLoading ? "투표 중..." : "투표하기"}
          </button>
        </div>
      )}

      {showConfirmedModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div
            className={`modal-backdrop absolute inset-0 bg-black/40 ${confirmedModalPresented ? "is-open" : ""} ${confirmedModalClosing ? "is-closing" : ""}`}
            onClick={closeConfirmedModal}
          />
          <div className={`modal-card relative w-72 bg-white rounded-3xl p-6 flex flex-col items-center gap-4 shadow-xl ${confirmedModalPresented ? "is-open" : ""} ${confirmedModalClosing ? "is-closing" : ""}`}>
            <span className="text-5xl">🎉</span>
            <p className="text-lg font-bold text-center">투표가 확정되었습니다!</p>
            {tieConfirmedName && (
              <p className="text-sm text-center text-gray-500 leading-relaxed">
                동점이어서 랜덤으로{" "}
                <span className="font-semibold text-gray-800">{tieConfirmedName}</span>
                이(가) 선택되었습니다.
              </p>
            )}
            <p className="text-sm text-gray-500 text-center leading-relaxed">
              장소가 확정되었어요.
              <br />
              일정 화면에서 확인해보세요.
            </p>
            <button
              onClick={closeConfirmedModal}
              className="w-full py-3.5 rounded-2xl font-semibold text-white"
              style={{ background: "#3b82f6" }}
            >
              확인
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
