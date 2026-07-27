"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter, useParams, useSearchParams } from "next/navigation";
import { useStore, uid } from "../../../store";
import { formatDate, apiFetch, API_BASE } from "../../../lib";

interface Post {
  postId: number;
  timeLineId: number | null;
  contentUrl: string;
  createdAt?: string;
  startTime?: string;
  endTime?: string;
  confirmedPlaceName?: string;
}

interface DateGroup {
  date: string;
  posts: Post[];
}

type Segment =
  | { type: "timeline"; timeLineId: number; posts: Post[] }
  | { type: "free"; slotKey: string; posts: Post[] };

function resolveUrl(contentUrl: string): string {
  if (contentUrl.startsWith("http")) return contentUrl;
  return `${API_BASE}${contentUrl}`;
}

function toSegments(posts: Post[]): Segment[] {
  const timelineSegs = new Map<number, Segment & { type: "timeline" }>();
  const freeSegs = new Map<string, Segment & { type: "free" }>();
  const order: string[] = [];

  for (const post of posts) {
    if (post.timeLineId !== null) {
      const orderKey = `tl-${post.timeLineId}`;
      if (!timelineSegs.has(post.timeLineId)) {
        timelineSegs.set(post.timeLineId, { type: "timeline", timeLineId: post.timeLineId, posts: [post] });
        order.push(orderKey);
      } else {
        timelineSegs.get(post.timeLineId)!.posts.push(post);
      }
    } else {
      const slotKey = post.startTime && post.endTime
        ? `${post.startTime}-${post.endTime}`
        : "unknown";
      if (!freeSegs.has(slotKey)) {
        freeSegs.set(slotKey, { type: "free", slotKey, posts: [post] });
        order.push(`free-${slotKey}`);
      } else {
        freeSegs.get(slotKey)!.posts.push(post);
      }
    }
  }

  return order.map(k => k.startsWith("tl-")
    ? timelineSegs.get(Number(k.slice(3)))!
    : freeSegs.get(k.slice(5))!
  );
}

// ── Main ──────────────────────────────────────────────────────────────────────

export default function TimelinePage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const { trips, upsertTrip } = useStore();
  const [groups, setGroups] = useState<DateGroup[]>([]);
  const [lightbox, setLightbox] = useState<string | null>(null);
  const [activeDots, setActiveDots] = useState<Record<string, number>>({});
  const scrollRefs = useRef<Record<string, HTMLDivElement | null>>({});

  const goBack = () => {
    if (searchParams.get("from") === "timeline") router.push(`/trip/${id}?tab=timeline`);
    else router.back();
  };
  const trip = trips.find(t => t.id === id);


  useEffect(() => {
    if (trip || !id) return;
    apiFetch(`${API_BASE}/api/v1/trips/${id}`)
      .then(r => r.json())
      .then(body => {
        const tripData = body.data;
        if (!tripData) return;
        upsertTrip({
          id: String(tripData.id), name: tripData.name, region: tripData.region,
          startDate: tripData.startDate, nights: tripData.nights, members: [],
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
    if (!id) return;
    apiFetch(`${API_BASE}/api/v1/trips/${id}/posts`)
      .then(r => r.text())
      .then(text => {
        if (!text) return;
        const body = JSON.parse(text);
        const raw = Array.isArray(body) ? body : (body.data ?? []);
        setGroups(raw);
      })
      .catch(e => console.error(e));
  }, [id]);

  if (!trip) return (
    <div className="flex items-center justify-center min-h-screen">
      <p className="text-gray-400 text-sm">불러오는 중...</p>
    </div>
  );

  return (
    <>
      {lightbox && (
        <div className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center" onClick={() => setLightbox(null)}>
          <img src={lightbox} alt="" className="max-w-full max-h-full object-contain" />
        </div>
      )}
      <div className="flex h-screen flex-col overflow-hidden">
        <div className="shrink-0 flex items-center gap-3 px-4 pt-12 pb-2">
          <button
            onClick={goBack}
            aria-label="뒤로가기"
            className="trip-header-icon-button w-10 h-10 rounded-full flex items-center justify-center"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="font-semibold text-base flex-1 text-center">여행 타임라인</h1>
          <div className="w-8" />
        </div>

        <div className="shrink-0 px-4 pb-3">
          <p className="text-2xl font-bold">{trip.name} 타임라인</p>
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto px-4 pb-10 flex flex-col gap-4">
          {trip.days.map(day => {
            const group = groups.find(g => g.date === day.date);
            const dayPosts = group?.posts ?? [];
            const activeIdx = activeDots[day.id] ?? 0;

            return (
              <div key={day.id} className="shrink-0 bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
                <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between">
                  <div>
                    <p className="font-semibold">{day.dayNumber}일차</p>
                    <p className="text-xs text-gray-400">{formatDate(day.date)}</p>
                  </div>
                  {dayPosts.length > 1 && (
                    <span className="text-xs text-gray-400">{activeIdx + 1}/{dayPosts.length}</span>
                  )}
                </div>

                <div className="p-4">
                  {dayPosts.length === 0 ? (
                    <p className="text-sm text-gray-500">아직 사진 기록이 없습니다.</p>
                  ) : (
                    <div className="relative">
                      <div
                        ref={(el) => { scrollRefs.current[day.id] = el; }}
                        className="flex gap-3 overflow-x-auto snap-x snap-mandatory select-none"
                        style={{ scrollbarWidth: "none", cursor: "grab" }}
                        onScroll={(e) => {
                          const el = e.currentTarget;
                          const step = el.clientWidth + 12;
                          if (step <= 0) return;
                          const idx = Math.max(0, Math.min(Math.round(el.scrollLeft / step), el.children.length - 1));
                          setActiveDots(prev => (prev[day.id] === idx ? prev : { ...prev, [day.id]: idx }));
                        }}
                        onPointerDown={(e) => {
                          if (e.pointerType === "touch") return;
                          e.preventDefault();
                          const el = e.currentTarget;
                          el.setPointerCapture(e.pointerId);
                          el.style.scrollSnapType = "none";
                          el.style.cursor = "grabbing";
                          const startX = e.clientX;
                          const startScrollLeft = el.scrollLeft;
                          let hasDragged = false;
                          const onMove = (ev: PointerEvent) => {
                            if (Math.abs(ev.clientX - startX) > 5) hasDragged = true;
                            el.scrollLeft = startScrollLeft - (ev.clientX - startX);
                          };
                          const onUp = (ev: PointerEvent) => {
                            el.releasePointerCapture(e.pointerId);
                            el.removeEventListener("pointermove", onMove);
                            el.removeEventListener("pointerup", onUp);
                            el.removeEventListener("pointercancel", onUp);
                            el.style.cursor = "grab";
                            const step = el.clientWidth + 12;
                            const dx = ev.clientX - startX;
                            const baseIdx = Math.round(startScrollLeft / step);
                            let targetIdx = baseIdx;
                            if (dx < -step * 0.15) targetIdx = baseIdx + 1;
                            else if (dx > step * 0.15) targetIdx = baseIdx - 1;
                            targetIdx = Math.max(0, Math.min(targetIdx, el.children.length - 1));
                            el.scrollTo({ left: targetIdx * step, behavior: "smooth" });
                            setActiveDots(prev => ({ ...prev, [day.id]: targetIdx }));
                            setTimeout(() => { el.style.scrollSnapType = ""; }, 400);
                            if (hasDragged) {
                              const blockClick = (ec: MouseEvent) => { ec.stopPropagation(); window.removeEventListener("click", blockClick, true); };
                              window.addEventListener("click", blockClick, true);
                            }
                          };
                          el.addEventListener("pointermove", onMove);
                          el.addEventListener("pointerup", onUp);
                          el.addEventListener("pointercancel", onUp);
                        }}
                      >
                        {toSegments(dayPosts).flatMap(seg => {
                          const fp = seg.posts[0];
                          const timeRange = fp?.startTime ? `${fp.startTime.slice(11, 16)} ~ ${fp.endTime!.slice(11, 16)}` : "";
                          const label = timeRange
                            ? `${timeRange} · ${fp?.confirmedPlaceName ?? "자유 시간"}`
                            : (fp?.confirmedPlaceName ?? "자유 시간");
                          const labelColor = seg.type === "timeline" ? "text-blue-400" : "text-gray-400";
                          const borderColor = seg.type === "timeline" ? "border-blue-100 bg-blue-50" : "border-gray-200 bg-gray-50";

                          return seg.posts.map(post => {
                            const src = resolveUrl(post.contentUrl);
                            return (
                              <div key={post.postId} className={`rounded-2xl border p-3 flex flex-col gap-2 snap-start basis-full shrink-0 ${borderColor}`}>
                                <p className={`text-xs font-semibold ${labelColor}`}>{label}</p>
                                <div className="flex items-center justify-center rounded-xl overflow-hidden" style={{ height: "360px" }}>
                                  <img src={src} alt="" draggable={false} className="max-w-full max-h-full object-contain cursor-pointer" onClick={() => setLightbox(src)} />
                                </div>
                              </div>
                            );
                          });
                        })}
                      </div>

                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </>
  );
}
