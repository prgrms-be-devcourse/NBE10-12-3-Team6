"use client";

import { useCallback, useState, useEffect, useRef, type MouseEvent, type ReactNode } from "react";
import { useRouter, useParams } from "next/navigation";
import Link from "next/link";
import { ArrowClockwise, Clock, Minus, PencilSimple, Plus, X } from "@phosphor-icons/react";
import { useStore, Trip, TripDay, ActivityBlock, PlanTheme, uid } from "../../../../store";
import { timeText, durationText, apiFetch, useAuthGuard, API_BASE } from "../../../../lib";
import AnimatedBottomSheet from "../../../../components/AnimatedBottomSheet";

const THEMES: PlanTheme[] = ["meal", "cafe", "activity", "etc"];
const MEMBER_COLORS = ["blue", "orange", "green", "purple", "pink", "teal", "indigo", "cyan"];

type TimeLineApiItem = {
  timeLineId?: number;
  timelineId?: number;
  voteId?: number | null;
  dayNumber: number;
  startTime: string;
  endTime: string;
  confirmedPlaceName?: string | null;
  category?: string | null;
};

type TimelineEventPayload = {
  message?: string;
  changedMemberId?: number;
};

const isoToMinutes = (iso: string) => {
  const [h, m] = iso.split("T")[1].split(":").map(Number);
  return h * 60 + (m || 0);
};

const makeDraftBlock = (order: number, startMinute = 9 * 60): ActivityBlock => ({
  id: uid(),
  order,
  theme: THEMES[(order - 1) % THEMES.length],
  startMinute,
  endMinute: Math.min(23 * 60 + 59, startMinute + 60),
});

const isPersistedBlock = (block: ActivityBlock) => /^\d+$/.test(block.id);
const TIMELINE_BLOCK_EXIT_MS = 180;
const SYNC_BANNER_ENTER_MS = 640;
const SYNC_BANNER_EXIT_MS = 420;
const wait = (ms: number) => new Promise(resolve => window.setTimeout(resolve, ms));

type SyncBannerPhase = "enter" | "visible" | "exit";

const toActivityBlocks = (items: TimeLineApiItem[]): ActivityBlock[] =>
  items.map((item, i) => ({
    id: String(item.timeLineId ?? item.timelineId ?? i),
    order: i + 1,
    theme: THEMES[i % THEMES.length],
    startMinute: isoToMinutes(item.startTime),
    endMinute: isoToMinutes(item.endTime),
    voteId: item.voteId != null ? String(item.voteId) : null,
    confirmedPlaceName: item.confirmedPlaceName ?? null,
    category: item.category ?? null,
  }));

const parseSseEvent = (rawEvent: string) => {
  const dataLines: string[] = [];
  let eventName = "";

  rawEvent.split(/\r?\n/).forEach(line => {
    if (line.startsWith("event:")) {
      eventName = line.replace("event:", "").trim();
    }
    if (line.startsWith("data:")) {
      dataLines.push(line.replace(/^data:\s?/, ""));
    }
  });

  return { eventName, data: dataLines.join("\n") };
};

const readTimelineEventPayload = (data: string): TimelineEventPayload => {
  if (!data) return {};
  try {
    return JSON.parse(data) as TimelineEventPayload;
  } catch {
    return { message: data };
  }
};

function TimePickerSheet({
  title,
  value,
  onClose,
  onConfirm,
}: {
  title: string;
  value: number;
  onClose: () => void;
  onConfirm: (minutes: number) => void;
}) {
  const [draft, setDraft] = useState(value);
  const hour24 = Math.floor(draft / 60);
  const minute = draft % 60;
  const isPm = hour24 >= 12;
  const hour12 = hour24 % 12 || 12;

  const clampMinutes = (minutes: number) => Math.min(23 * 60 + 59, Math.max(0, minutes));
  const setMeridiem = (nextIsPm: boolean) => {
    setDraft(current => {
      const currentHour = Math.floor(current / 60);
      const currentMinute = current % 60;
      const baseHour = currentHour % 12;
      return (nextIsPm ? baseHour + 12 : baseHour) * 60 + currentMinute;
    });
  };
  const setHour12 = (nextHour12: number) => {
    const normalized = ((nextHour12 - 1 + 12) % 12) + 1;
    const nextHour24 = isPm
      ? (normalized === 12 ? 12 : normalized + 12)
      : (normalized === 12 ? 0 : normalized);
    setDraft(nextHour24 * 60 + minute);
  };
  const setMinute = (nextMinute: number) => {
    const normalized = ((nextMinute % 60) + 60) % 60;
    setDraft(hour24 * 60 + normalized);
  };

  return (
    <AnimatedBottomSheet
      onClose={onClose}
      overlayClassName="bg-black/35"
      className="overflow-y-auto px-5 pt-4 pb-6"
    >
      {(close) => (
        <>
        <div className="flex items-center gap-3 mb-4">
          <p className="font-bold flex-1">{title}</p>
          <button
            type="button"
            onClick={close}
            className="w-9 h-9 rounded-full bg-gray-100 flex items-center justify-center text-gray-600"
            aria-label="닫기"
          >
            <X size={18} weight="bold" />
          </button>
        </div>

        <div className="grid grid-cols-2 gap-2 p-1 bg-gray-100 rounded-2xl mb-4">
          {[
            { label: "오전", value: false },
            { label: "오후", value: true },
          ].map(option => (
            <button
              key={option.label}
              type="button"
              onClick={() => setMeridiem(option.value)}
              className={`py-3 rounded-xl text-sm font-bold transition ${
                isPm === option.value ? "bg-white text-blue-600 shadow-sm" : "text-gray-500"
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>

        <div className="text-center mb-4">
          <p className="text-4xl font-bold tracking-normal">
            {hour12}:{String(minute).padStart(2, "0")}
          </p>
        </div>

        <div className="grid grid-cols-2 gap-3 mb-4">
          <div className="bg-gray-50 rounded-2xl p-3">
            <p className="text-xs font-semibold text-gray-500 mb-2">시</p>
            <div className="flex items-center justify-between gap-2">
              <button
                type="button"
                onClick={() => setHour12(hour12 - 1)}
                className="w-12 h-12 rounded-full bg-white border border-gray-200 flex items-center justify-center"
                aria-label="시 감소"
              >
                <Minus size={18} weight="bold" />
              </button>
              <span className="text-2xl font-bold w-12 text-center">{hour12}</span>
              <button
                type="button"
                onClick={() => setHour12(hour12 + 1)}
                className="w-12 h-12 rounded-full bg-white border border-gray-200 flex items-center justify-center"
                aria-label="시 증가"
              >
                <Plus size={18} weight="bold" />
              </button>
            </div>
          </div>

          <div className="bg-gray-50 rounded-2xl p-3">
            <p className="text-xs font-semibold text-gray-500 mb-2">분</p>
            <div className="flex items-center justify-between gap-2">
              <button
                type="button"
                onClick={() => setDraft(current => clampMinutes(current - 5))}
                className="w-12 h-12 rounded-full bg-white border border-gray-200 flex items-center justify-center"
                aria-label="분 감소"
              >
                <Minus size={18} weight="bold" />
              </button>
              <span className="text-2xl font-bold w-12 text-center">{String(minute).padStart(2, "0")}</span>
              <button
                type="button"
                onClick={() => setDraft(current => clampMinutes(current + 5))}
                className="w-12 h-12 rounded-full bg-white border border-gray-200 flex items-center justify-center"
                aria-label="분 증가"
              >
                <Plus size={18} weight="bold" />
              </button>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-4 gap-2 mb-5">
          {[0, 15, 30, 45].map(option => (
            <button
              key={option}
              type="button"
              onClick={() => setMinute(option)}
              className={`py-3 rounded-xl text-sm font-bold ${
                minute === option ? "bg-blue-50 text-blue-600" : "bg-gray-50 text-gray-500"
              }`}
            >
              {String(option).padStart(2, "0")}
            </button>
          ))}
        </div>

        <button
          type="button"
          onClick={() => {
            onConfirm(draft);
            close();
          }}
          className="w-full py-4 rounded-2xl bg-blue-500 text-white font-bold"
        >
          완료
        </button>
        </>
      )}
    </AnimatedBottomSheet>
  );
}

function TimeButton({
  label,
  value,
  disabled,
  onChange,
}: {
  label: string;
  value: number;
  disabled: boolean;
  onChange: (minutes: number) => void;
}) {
  const [pickerOpen, setPickerOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        disabled={disabled}
        onClick={() => setPickerOpen(true)}
        className="w-full p-2.5 bg-white rounded-xl text-sm font-semibold border border-gray-200 outline-none flex items-center justify-between disabled:bg-gray-100 disabled:opacity-70"
      >
        <span>{timeText(value)}</span>
        <Clock size={20} weight="bold" className="text-gray-500" />
      </button>
      {pickerOpen && (
        <TimePickerSheet
          title={`${label} 시간`}
          value={value}
          onClose={() => setPickerOpen(false)}
          onConfirm={onChange}
        />
      )}
    </>
  );
}

function BlockCard({
  trip,
  day,
  block,
  disabled = false,
  onRemove,
  removeDisabled = false,
  removing = false,
  animationDelayMs = 0,
  footer,
  onStartChange,
  onEndChange,
}: {
  trip: Trip;
  day: TripDay;
  block: ActivityBlock;
  disabled?: boolean;
  onRemove?: () => void;
  removeDisabled?: boolean;
  removing?: boolean;
  animationDelayMs?: number;
  footer?: ReactNode;
  onStartChange: (v: number) => void;
  onEndChange: (v: number) => void;
}) {
  const duration = Math.max(0, block.endMinute - block.startMinute);
  const selected = trip.candidates.find(c => c.id === day.selectedCandidateByBlock[block.id]);

  return (
    <div
      className={`timeline-block-card p-4 bg-gray-50 rounded-2xl flex flex-col gap-3 ${removing ? "is-removing" : ""}`}
      style={{ animationDelay: removing ? "0ms" : `${animationDelayMs}ms` }}
    >
      <div className="flex items-start justify-between">
        <div>
          <p className="font-semibold">{block.order}번째 시간 구간</p>
          <p className="text-xs mt-0.5 text-gray-400">{durationText(duration)}</p>
        </div>
        <div className="flex items-center gap-2">
          {selected && <span className="text-green-500 text-lg">✓</span>}
          {onRemove && (
            <button
              type="button"
              onClick={onRemove}
              disabled={removeDisabled}
              className="w-8 h-8 rounded-full bg-red-50 text-red-500 flex items-center justify-center disabled:opacity-40"
              aria-label={`${block.order}번째 시간 구간 삭제`}
            >
              <Minus size={17} weight="bold" />
            </button>
          )}
        </div>
      </div>

      <div className="flex items-center gap-3">
        <div className="flex-1 flex flex-col gap-1">
          <p className="text-xs text-gray-500">시작</p>
          <TimeButton
            label="시작"
            value={block.startMinute}
            disabled={disabled}
            onChange={onStartChange}
          />
        </div>
        <span className="text-gray-300 mt-4 font-bold">~</span>
        <div className="flex-1 flex flex-col gap-1">
          <p className="text-xs text-gray-500">종료</p>
          <TimeButton
            label="종료"
            value={block.endMinute}
            disabled={disabled}
            onChange={onEndChange}
          />
        </div>
      </div>

      {selected && (
        <div className="p-3 rounded-xl" style={{ background: "#dcfce7" }}>
          <p className="text-xs text-green-600 mb-1">이 구간에 확정된 후보</p>
          <p className="font-semibold text-sm">{selected.placeName}</p>
          <p className="text-xs text-gray-500">{selected.address}</p>
        </div>
      )}

      {footer}
    </div>
  );
}

function AddTimeRangeSlot({
  onClick,
  disabled,
  animationDelayMs = 0,
}: {
  onClick: () => void;
  disabled: boolean;
  animationDelayMs?: number;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label="시간 구간 추가"
      className="timeline-block-card min-h-24 rounded-2xl border-2 border-dashed border-green-200 bg-green-50/50 text-green-600 flex flex-col items-center justify-center gap-2 disabled:opacity-40"
      style={{ animationDelay: `${animationDelayMs}ms` }}
    >
      <span className="w-10 h-10 rounded-full bg-green-500 text-white flex items-center justify-center">
        <Plus size={22} weight="bold" />
      </span>
    </button>
  );
}

function PlanSummaryCard({
  trip,
  day,
  block,
  voteHref,
  onVoteClick,
  animationDelayMs = 0,
  removing = false,
}: {
  trip: Trip;
  day: TripDay;
  block: ActivityBlock;
  voteHref?: string;
  onVoteClick?: (event: MouseEvent<HTMLAnchorElement>) => void;
  animationDelayMs?: number;
  removing?: boolean;
}) {
  const selected = trip.candidates.find(c => c.id === day.selectedCandidateByBlock[block.id]);
  const confirmedName = block.confirmedPlaceName || selected?.placeName || null;
  const content = (
    <div
      className={`timeline-block-card flex gap-4 p-4 bg-white rounded-2xl shadow-sm border border-gray-100 items-center ${removing ? "is-removing" : ""}`}
      style={{ animationDelay: removing ? "0ms" : `${animationDelayMs}ms` }}
    >
      <div className="flex flex-col items-center text-sm text-gray-400 shrink-0">
        <span className="font-bold">{timeText(block.startMinute)}</span>
        <div className="w-0.5 h-8 bg-gray-200 my-1" />
        <span className="font-bold">{timeText(block.endMinute)}</span>
      </div>
      <div className="flex-1 min-w-0 flex flex-col justify-center">
        {confirmedName ? (
          <>
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 shrink-0">
                {block.category || "기타"}
              </span>
              <p className="font-semibold text-sm truncate">{confirmedName}</p>
            </div>
            {selected?.address && <p className="text-xs text-gray-400 mt-0.5 truncate">{selected.address}</p>}
          </>
        ) : (
          <>
            <p className="text-sm font-semibold text-gray-400">아직 계획이 안 세워졌어요</p>
            <p className={`text-xs font-bold mt-1 ${block.voteId ? "text-blue-500" : "text-gray-400"}`}>
              {block.voteId ? "탭해서 후보 투표하러 가기 →" : "투표 준비 중"}
            </p>
          </>
        )}
      </div>
      {confirmedName && <span className="text-green-500 shrink-0 self-center">✓</span>}
    </div>
  );

  if (!voteHref) return content;

  return (
    <Link href={voteHref} onClick={onVoteClick}>
      {content}
    </Link>
  );
}

export default function DayPlanPage() {
  useAuthGuard();
  const router = useRouter();
  const { id, dayNumber } = useParams<{ id: string; dayNumber: string }>();
  const { trips, updateTrip, upsertTrip, currentUser } = useStore();

  const trip = trips.find(t => t.id === id);
  const dayNum = parseInt(dayNumber);
  const dayIdx = trip?.days.findIndex(d => d.dayNumber === dayNum) ?? -1;

  const [validationError, setValidationError] = useState("");
  const [showSyncButton, setShowSyncButton] = useState(false);
  const [syncBannerPhase, setSyncBannerPhase] = useState<SyncBannerPhase>("enter");
  const [syncBannerKey, setSyncBannerKey] = useState(0);
  const [syncMessage, setSyncMessage] = useState("새로운 변경 사항이 있습니다.");
  const [syncLoading, setSyncLoading] = useState(false);
  const [timelineSaving, setTimelineSaving] = useState(false);
  const [isEditingTimeRanges, setIsEditingTimeRanges] = useState(false);
  const [removingBlockIds, setRemovingBlockIds] = useState<string[]>([]);
  const [isLeavingDay, setIsLeavingDay] = useState(false);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const syncAnimationTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const syncBannerVisibleRef = useRef(false);

  const openSyncBanner = useCallback((message: string) => {
    if (syncAnimationTimer.current) {
      clearTimeout(syncAnimationTimer.current);
      syncAnimationTimer.current = null;
    }

    setSyncMessage(message);

    if (syncBannerVisibleRef.current) {
      setSyncBannerPhase("visible");
      setShowSyncButton(true);
      return;
    }

    syncBannerVisibleRef.current = true;
    setSyncBannerKey(key => key + 1);
    setSyncBannerPhase("enter");
    setShowSyncButton(true);

    syncAnimationTimer.current = setTimeout(() => {
      setSyncBannerPhase("visible");
      syncAnimationTimer.current = null;
    }, SYNC_BANNER_ENTER_MS);
  }, []);

  const closeSyncBanner = useCallback(() => new Promise<void>((resolve) => {
    if (syncAnimationTimer.current) {
      clearTimeout(syncAnimationTimer.current);
      syncAnimationTimer.current = null;
    }

    syncBannerVisibleRef.current = false;
    setSyncBannerPhase("exit");
    syncAnimationTimer.current = setTimeout(() => {
      setShowSyncButton(false);
      setSyncBannerPhase("enter");
      syncAnimationTimer.current = null;
      resolve();
    }, SYNC_BANNER_EXIT_MS);
  }), []);

  useEffect(() => () => {
    if (syncAnimationTimer.current) clearTimeout(syncAnimationTimer.current);
    syncBannerVisibleRef.current = false;
  }, []);

  const applyTimelineItems = async (
    items: TimeLineApiItem[],
    { clearWhenEmpty = false, animateRemoved = false } = {}
  ) => {
    if (!trip || dayIdx < 0) return;
    if (items.length === 0 && !clearWhenEmpty) return;

    const currentDay = trip.days[dayIdx];
    const draftBlocks = currentDay.blocks.filter(block => !isPersistedBlock(block));
    const nextPersistedIds = new Set(
      items
        .map(item => item.timeLineId ?? item.timelineId)
        .filter((timelineId): timelineId is number => timelineId != null)
        .map(String)
    );
    const removedBlockIds = animateRemoved
      ? currentDay.blocks
          .filter(block => isPersistedBlock(block) && !nextPersistedIds.has(block.id))
          .map(block => block.id)
      : [];

    if (removedBlockIds.length > 0) {
      setRemovingBlockIds(ids => Array.from(new Set([...ids, ...removedBlockIds])));
      await wait(TIMELINE_BLOCK_EXIT_MS);
    }

    const blocks = items.length > 0
      ? toActivityBlocks(items)
      : (draftBlocks.length > 0 ? draftBlocks : [makeDraftBlock(1)]);

    updateTrip({
      ...trip,
      days: trip.days.map((d, i) => i === dayIdx
        ? {
            ...currentDay,
            blocks,
            isPlanCompleted: items.length > 0,
            isPlanSkipped: items.length > 0 ? false : currentDay.isPlanSkipped,
          }
        : d
      ),
    });

    if (removedBlockIds.length > 0) {
      setRemovingBlockIds(ids => ids.filter(id => !removedBlockIds.includes(id)));
    }
  };

  const fetchTimeLinesForDay = async ({ clearWhenEmpty = true, animateRemoved = false } = {}) => {
    if (!id || !trip || dayIdx < 0) return;
    const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/timelines?dayNumber=${dayNum}`);
    if (!response.ok) throw new Error("타임라인을 불러오지 못했습니다.");
    const body = await response.json();
    const items: TimeLineApiItem[] = body.data ?? [];
    await applyTimelineItems(items, { clearWhenEmpty, animateRemoved });
  };

  useEffect(() => {
    if (!id || (trip && trip.members.length > 0)) return;

    apiFetch(`${API_BASE}/api/v1/trips/${id}`)
      .then(r => r.json())
      .then(body => {
        const tripData = body.data;
        if (!tripData) return;

        const members = (tripData.members ?? []).map(
          (m: { memberId: number; name: string; admin: boolean }, i: number) => ({
            id: m.memberId,
            name: m.name,
            color: MEMBER_COLORS[i % MEMBER_COLORS.length],
            isAdmin: m.admin,
          })
        );

        const days = trip?.days ?? Array.from({ length: (tripData.nights ?? 0) + 1 }, (_, i) => {
          const d = new Date(tripData.startDate + "T00:00:00");
          d.setDate(d.getDate() + i);
          const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
          return {
            id: `day-${i + 1}`,
            dayNumber: i + 1,
            date: dateStr,
            blocks: [makeDraftBlock(1)],
            isPlanCompleted: false,
            isPlanSkipped: false,
            selectedCandidateByBlock: {},
            votedUserIDsByBlockAndCandidate: {},
            records: [],
          };
        });

        upsertTrip({
          ...(trip ?? {
            id: String(tripData.id),
            candidates: [],
            inviteJoinIndex: 0,
          }),
          name: tripData.name,
          region: tripData.region,
          startDate: tripData.startDate,
          nights: tripData.nights,
          members,
          days,
          inviteCode: tripData.joinCode ?? "",
        });
      })
      .catch(() => {});
  }, [id, trip?.id, trip?.members.length]);

  useEffect(() => {
    if (!id || !trip || dayIdx < 0) return;
    fetchTimeLinesForDay({ clearWhenEmpty: true }).catch(() => {});
  }, [id, dayNum, trip?.id]);

  useEffect(() => {
    setIsEditingTimeRanges(false);
    setValidationError("");
  }, [id, dayNum]);

  useEffect(() => {
    if (!id || !trip || dayIdx < 0) return;

    let closed = false;
    const controller = new AbortController();

    const handleEvent = (rawEvent: string) => {
      const { eventName, data } = parseSseEvent(rawEvent);
      if (eventName !== "TIMELINE_UPDATED") return;

      const payload = readTimelineEventPayload(data);
      if (payload.changedMemberId != null && Number(payload.changedMemberId) === Number(currentUser.id)) {
        return;
      }

      openSyncBanner(payload.message || "새로운 변경 사항이 있습니다.");
    };

    const connect = async () => {
      try {
        const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/timelines/subscribe`, {
          headers: { Accept: "text/event-stream" },
          signal: controller.signal,
        });

        if (!response.ok || !response.body) return;

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        while (!closed) {
          const { value, done } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const events = buffer.split(/\r?\n\r?\n/);
          buffer = events.pop() ?? "";
          events.forEach(handleEvent);
        }
      } catch (error) {
        if (!closed && !controller.signal.aborted) {
          console.error("[타임라인 SSE 연결 실패]", error);
        }
      }

      if (!closed) {
        reconnectTimer.current = setTimeout(connect, 2000);
      }
    };

    connect();

    return () => {
      closed = true;
      controller.abort();
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current);
    };
  }, [id, trip?.id, dayIdx, currentUser.id, openSyncBanner]);

  if (!trip || dayIdx < 0) return (
    <div className="flex items-center justify-center min-h-screen">
      <p className="text-gray-400 text-sm">불러오는 중...</p>
    </div>
  );

  const day = trip.days[dayIdx];
  const isAdmin = trip.members.some(member => Number(member.id) === Number(currentUser.id) && member.isAdmin);

  const tripStarted = (() => {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const startDate = new Date(trip.startDate); startDate.setHours(0, 0, 0, 0);
    return today >= startDate;
  })();
  const sortedBlocks = [...day.blocks].sort((a, b) => a.startMinute - b.startMinute);
  const canComplete = isAdmin && day.blocks.length > 0;

  const leaveDayWithTransition = (navigate: () => void) => {
    if (isLeavingDay) return;
    setIsLeavingDay(true);
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.setTimeout(navigate, prefersReducedMotion ? 0 : 240);
  };

  const markBlockRemoving = async (blockId: string) => {
    setRemovingBlockIds(ids => ids.includes(blockId) ? ids : [...ids, blockId]);
    await wait(TIMELINE_BLOCK_EXIT_MS);
  };

  const clearBlockRemoving = (blockId: string) => {
    setRemovingBlockIds(ids => ids.filter(id => id !== blockId));
  };

  const setDay = (updated: TripDay) => {
    updateTrip({ ...trip, days: trip.days.map((d, i) => i === dayIdx ? updated : d) });
  };

  const normalizeOrders = (blocks: ActivityBlock[]): ActivityBlock[] =>
    [...blocks].sort((a, b) => a.startMinute - b.startMinute).map((b, i) => ({ ...b, order: i + 1 }));

  const toDateTime = (minutes: number) => {
    const d = new Date(trip.startDate + "T00:00:00");
    d.setDate(d.getDate() + dayNum - 1);
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    const h = String(Math.floor(minutes / 60)).padStart(2, "0");
    const m = String(minutes % 60).padStart(2, "0");
    return `${dateStr}T${h}:${m}:00`;
  };

  const validateBlocks = (blocks: ActivityBlock[]) => {
    const hasInvalid = blocks.some(block => block.endMinute <= block.startMinute);
    if (hasInvalid) {
      setValidationError("종료 시간이 시작 시간보다 늦어야 합니다.");
      return false;
    }

    const sorted = [...blocks].sort((a, b) => a.startMinute - b.startMinute);
    const hasOverlap = sorted.some((block, i) => i > 0 && sorted[i - 1].endMinute > block.startMinute);
    if (hasOverlap) {
      setValidationError("시간 구간이 서로 겹칩니다. 겹치지 않게 조정해주세요.");
      return false;
    }

    setValidationError("");
    return true;
  };

  const syncTimeLines = async () => {
    setSyncLoading(true);
    try {
      await fetchTimeLinesForDay({ clearWhenEmpty: true, animateRemoved: true });
      await closeSyncBanner();
      setIsEditingTimeRanges(false);
    } catch (error) {
      console.error("[타임라인 동기화 실패]", error);
    } finally {
      setSyncLoading(false);
    }
  };

  const suggestedNextStart = () => {
    const lastEnd = day.blocks.reduce((max, block) => Math.max(max, block.endMinute), 9 * 60);
    const rounded = Math.min(23 * 60, Math.floor(lastEnd / 15) * 15);
    return rounded >= 23 * 60 ? 9 * 60 : rounded;
  };

  const saveNewTimeLine = async (block: ActivityBlock) => {
    setTimelineSaving(true);
    try {
      const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/timelines`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          dayNumber: dayNum,
          startTime: toDateTime(block.startMinute),
          endTime: toDateTime(block.endMinute),
        }),
      });
      if (!response.ok) throw new Error("시간 구간을 추가하지 못했습니다.");
      await fetchTimeLinesForDay({ clearWhenEmpty: true });
    } catch (error) {
      console.error("[타임라인 추가 실패]", error);
      setValidationError("시간 구간을 추가하지 못했습니다.");
      await fetchTimeLinesForDay({ clearWhenEmpty: true }).catch(() => {});
    } finally {
      setTimelineSaving(false);
    }
  };

  const saveUpdatedTimeLine = async (block: ActivityBlock) => {
    if (!isPersistedBlock(block)) return;

    setTimelineSaving(true);
    try {
      const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/timelines/${block.id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          startTime: toDateTime(block.startMinute),
          endTime: toDateTime(block.endMinute),
        }),
      });
      if (!response.ok) throw new Error("시간 구간을 수정하지 못했습니다.");
      await fetchTimeLinesForDay({ clearWhenEmpty: true });
    } catch (error) {
      console.error("[타임라인 수정 실패]", error);
      setValidationError("시간 구간을 수정하지 못했습니다.");
      await fetchTimeLinesForDay({ clearWhenEmpty: true }).catch(() => {});
    } finally {
      setTimelineSaving(false);
    }
  };

  const deletePersistedTimeLine = async (block: ActivityBlock) => {
    if (!isPersistedBlock(block)) return;

    setTimelineSaving(true);
    await markBlockRemoving(block.id);
    try {
      const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/timelines/${block.id}`, {
        method: "DELETE",
      });
      if (!response.ok) throw new Error("시간 구간을 삭제하지 못했습니다.");
      await fetchTimeLinesForDay({ clearWhenEmpty: true });
    } catch (error) {
      console.error("[타임라인 삭제 실패]", error);
      setValidationError("시간 구간을 삭제하지 못했습니다.");
      clearBlockRemoving(block.id);
      await fetchTimeLinesForDay({ clearWhenEmpty: true }).catch(() => {});
    } finally {
      setTimelineSaving(false);
    }
  };

  const removeLocalBlock = async (block: ActivityBlock) => {
    if (day.blocks.length <= 1) return;

    setTimelineSaving(true);
    await markBlockRemoving(block.id);

    const remaining = day.blocks.filter(item => item.id !== block.id);
    const selected = { ...day.selectedCandidateByBlock };
    delete selected[block.id];
    const voted = { ...day.votedUserIDsByBlockAndCandidate };
    delete voted[block.id];
    setDay({
      ...day,
      blocks: normalizeOrders(remaining),
      selectedCandidateByBlock: selected,
      votedUserIDsByBlockAndCandidate: voted,
    });
    clearBlockRemoving(block.id);
    setTimelineSaving(false);
  };

  const increaseBlocks = async () => {
    if (!isAdmin || timelineSaving) return;
    if (day.isPlanCompleted && !isEditingTimeRanges) return;

    const nextStart = suggestedNextStart();
    const newBlock = makeDraftBlock(day.blocks.length + 1, nextStart);
    const nextBlocks = normalizeOrders([...day.blocks, newBlock]);
    if (!validateBlocks(nextBlocks)) return;

    if (day.isPlanCompleted) {
      await saveNewTimeLine(newBlock);
      return;
    }

    setDay({ ...day, blocks: nextBlocks });
  };

  const decreaseBlocks = async () => {
    if (!isAdmin || timelineSaving || day.blocks.length <= 1) return;
    if (day.isPlanCompleted && !isEditingTimeRanges) return;

    const removed = sortedBlocks[sortedBlocks.length - 1];
    if (day.isPlanCompleted && isPersistedBlock(removed)) {
      await deletePersistedTimeLine(removed);
      return;
    }

    await removeLocalBlock(removed);
  };

  const removeBlock = async (block: ActivityBlock) => {
    if (!isAdmin || timelineSaving) return;
    if (day.isPlanCompleted && !isEditingTimeRanges) return;

    if (day.isPlanCompleted && isPersistedBlock(block)) {
      await deletePersistedTimeLine(block);
      return;
    }

    await removeLocalBlock(block);
  };

  const updateBlockTime = async (blockId: string, start: number, end: number) => {
    if (timelineSaving) return;
    if (day.isPlanCompleted && !isEditingTimeRanges) return;
    if (!day.isPlanCompleted && !isAdmin) return;

    const nextBlocks = normalizeOrders(day.blocks.map(block => (
      block.id === blockId ? { ...block, startMinute: start, endMinute: end } : block
    )));
    setDay({ ...day, blocks: nextBlocks });
    if (!validateBlocks(nextBlocks)) return;

    const updated = nextBlocks.find(block => block.id === blockId);
    if (day.isPlanCompleted && updated) {
      await saveUpdatedTimeLine(updated);
    }
  };

  const completePlan = async () => {
    if (!isAdmin) return;

    const sorted = normalizeOrders(day.blocks);
    if (!validateBlocks(sorted)) return;

    const timeLines = sorted.map(block => ({
      dayNumber: dayNum,
      startTime: toDateTime(block.startMinute),
      endTime: toDateTime(block.endMinute),
    }));

    setTimelineSaving(true);
    try {
      const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/timelines/batch`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ dayNumber: dayNum, timeLines }),
      });
      if (!response.ok) throw new Error("타임라인 저장 실패");
      await fetchTimeLinesForDay({ clearWhenEmpty: true });
      setIsEditingTimeRanges(false);
    } catch (error) {
      console.error("[타임라인 저장 실패]", error);
      setValidationError("저장에 실패했습니다. 다시 시도해주세요.");
    } finally {
      setTimelineSaving(false);
    }
  };

  return (
    <div className={`timeline-day-page flex flex-col h-screen ${isLeavingDay ? "trip-page-exit" : ""}`}>
      <div className="flex items-center gap-3 px-4 pt-12 pb-2">
        <button onClick={() => leaveDayWithTransition(() => router.back())} aria-label="뒤로가기" className="trip-header-icon-button w-10 h-10 rounded-full flex items-center justify-center">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 className="font-semibold text-base flex-1 text-center">{dayNum}일차</h1>
        <div className="w-8" />
      </div>

      {showSyncButton && (
        <div
          key={syncBannerKey}
          className={`sync-banner-shell px-4 is-${syncBannerPhase}`}
        >
          <div className="sync-banner-card rounded-2xl bg-blue-50 px-4 py-3 flex items-center gap-3">
            <p className="flex-1 min-w-0 text-sm font-semibold text-blue-700 leading-snug">{syncMessage}</p>
            <button
              onClick={syncTimeLines}
              disabled={syncLoading}
              className="shrink-0 px-3 py-2 rounded-xl bg-white text-blue-600 font-bold text-xs flex items-center gap-1.5 disabled:opacity-60"
            >
              <ArrowClockwise size={15} weight="bold" className={syncLoading ? "animate-spin" : ""} />
              동기화
            </button>
          </div>
        </div>
      )}

      <div className="flex-1 overflow-y-scroll px-4 pt-2 pb-4 flex flex-col gap-5">
        <div>
          <p className="text-2xl font-bold">{dayNum}일차 계획</p>
          <p className="text-xs text-gray-500 mt-1">시간 구간을 정한 뒤, 여행 전체 후보 중 하나를 선택합니다.</p>
        </div>

        <div
          key={`${day.isPlanCompleted ? "completed" : "draft"}-${isEditingTimeRanges ? "editing" : "view"}-${day.isPlanSkipped ? "skipped" : "active"}`}
          className="timeline-section-transition"
        >
        {day.isPlanSkipped ? (
          <div className="p-4 bg-gray-50 rounded-2xl">
            <p className="font-semibold mb-1">이 일차는 계획을 건너뛰었습니다.</p>
            <p className="text-sm text-gray-500">여행 타임라인에서도 건너뛴 일차로 표시됩니다.</p>
          </div>
        ) : day.isPlanCompleted ? (
          <div className="flex flex-col gap-4">
            <div className="flex items-center">
              <p className="font-semibold flex-1">{isEditingTimeRanges ? "시간 구간" : "확정된 계획"}</p>
              <div className="flex items-center gap-2">
                {isEditingTimeRanges && (
                  <span className="text-[11px] font-semibold text-gray-500 hidden min-[360px]:inline">
                    다시 눌러 편집화면 나가기
                  </span>
                )}
                {!tripStarted && <button
                  type="button"
                  aria-label={isEditingTimeRanges ? "시간 구간 수정 닫기" : "시간 구간 수정"}
                  title={isEditingTimeRanges ? "시간 구간 수정 닫기" : "시간 구간 수정"}
                  onClick={() => {
                    setValidationError("");
                    setIsEditingTimeRanges(v => !v);
                  }}
                  disabled={timelineSaving}
                  className={`w-9 h-9 rounded-full flex items-center justify-center transition disabled:opacity-40 ${
                    isEditingTimeRanges ? "bg-blue-500 text-white" : "bg-gray-100 text-gray-700"
                  }`}
                >
                  <PencilSimple size={20} weight="bold" />
                </button>}
              </div>
            </div>

            {validationError && (
              <p className="text-sm text-red-500 font-semibold">{validationError}</p>
            )}

            {isEditingTimeRanges && (
              <p className="text-xs font-semibold text-gray-500 bg-gray-50 rounded-xl px-3 py-2">
                수정된 정보는 즉시 저장됩니다.
              </p>
            )}

            {isEditingTimeRanges ? (
              <>
                {sortedBlocks.map((block, index) => (
                  <BlockCard
                    key={block.id}
                    trip={trip}
                    day={day}
                    block={block}
                    removing={removingBlockIds.includes(block.id)}
                    animationDelayMs={index * 45}
                    disabled={timelineSaving}
                    onRemove={isAdmin ? () => removeBlock(block) : undefined}
                    removeDisabled={timelineSaving}
                    onStartChange={start => updateBlockTime(block.id, start, block.endMinute)}
                    onEndChange={end => updateBlockTime(block.id, block.startMinute, end)}
                  />
                ))}
                {isAdmin && (
                  <AddTimeRangeSlot
                    onClick={increaseBlocks}
                    disabled={timelineSaving}
                    animationDelayMs={sortedBlocks.length * 45}
                  />
                )}
              </>
            ) : (
              sortedBlocks.map((block, index) => (
                <PlanSummaryCard
                  key={block.id}
                  trip={trip}
                  day={day}
                  block={block}
                  removing={removingBlockIds.includes(block.id)}
                  animationDelayMs={index * 45}
                  voteHref={!tripStarted && block.voteId ? `/trip/${id}/day/${dayNum}/block/${block.voteId}?from=vote&timelineId=${block.id}` : undefined}
                  onVoteClick={(event) => {
                    event.preventDefault();
                    if (block.voteId) localStorage.setItem(`block-order-${block.voteId}`, String(block.order));
                    leaveDayWithTransition(() => router.push(`/trip/${id}/day/${dayNum}/block/${block.voteId}?from=vote&timelineId=${block.id}`));
                  }}
                />
              ))
            )}
          </div>
        ) : !isAdmin ? (
          <div className="p-4 bg-gray-50 rounded-2xl">
            <p className="font-semibold mb-1">방장이 시간 구간을 설정 중입니다.</p>
            <p className="text-sm text-gray-500">아직 확정된 시간 구간이 없습니다.</p>
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="flex items-center">
              <p className="font-semibold flex-1">시간 구간</p>
              <div className="flex items-center gap-3">
                <button
                  onClick={decreaseBlocks}
                  disabled={timelineSaving || day.blocks.length <= 1}
                  className="w-9 h-9 rounded-full bg-gray-100 flex items-center justify-center font-bold text-lg disabled:opacity-40"
                >
                  −
                </button>
                <span className="font-bold w-6 text-center">{day.blocks.length}</span>
                <button
                  onClick={increaseBlocks}
                  disabled={timelineSaving}
                  className="w-9 h-9 rounded-full bg-gray-100 flex items-center justify-center font-bold text-lg disabled:opacity-40"
                >
                  +
                </button>
              </div>
            </div>

            {sortedBlocks.map((block, index) => (
              <BlockCard
                key={block.id}
                trip={trip}
                day={day}
                block={block}
                removing={removingBlockIds.includes(block.id)}
                animationDelayMs={index * 45}
                disabled={timelineSaving}
                onStartChange={start => updateBlockTime(block.id, start, block.endMinute)}
                onEndChange={end => updateBlockTime(block.id, block.startMinute, end)}
              />
            ))}
          </div>
        )}
        </div>
      </div>

      {isAdmin && !day.isPlanCompleted && !day.isPlanSkipped && (
        <div className="px-4 py-4 border-t border-gray-100 flex flex-col gap-2 bg-white">
          {validationError && (
            <p className="text-sm text-red-500 font-semibold text-center">{validationError}</p>
          )}
          <button
            onClick={completePlan}
            disabled={!canComplete || timelineSaving}
            className="w-full py-4 rounded-2xl font-semibold text-white disabled:opacity-40"
            style={{ background: "#3b82f6" }}
          >
            시간 범위 설정 완료
          </button>
        </div>
      )}
    </div>
  );
}
