"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter, useParams, useSearchParams } from "next/navigation";
import { useStore, uid } from "../../../store";
import { formatDate, apiFetch, API_BASE } from "../../../lib";
import { selectPhotoUrls, usePhotoDataPreference } from "../../../photoDataPreference";

interface Post {
  postId: number;
  authorMemberId?: number | null;
  content?: string | null;
  timelineId: number | null;
  contentUrl: string | null;
  normalContentUrl?: string | null;
  dataSaverContentUrl?: string | null;
  createdAt?: string;
  startTime?: string;
  endTime?: string;
  confirmedPlaceName?: string;
}

interface DateGroup {
  date: string;
  posts: Post[];
}

interface PostCursorResponse {
  groups: DateGroup[];
  nextCursor: string | null;
  hasNext: boolean;
}

type Segment =
  | { type: "timeline"; timelineId: number; posts: Post[] }
  | { type: "free"; slotKey: string; posts: Post[] };

function resolveUrl(contentUrl?: string | null): string | null {
  if (!contentUrl) return null;
  if (contentUrl.startsWith("http")) return contentUrl;
  return `${API_BASE}${contentUrl}`;
}

function mergeGroups(current: DateGroup[], incoming: DateGroup[]): DateGroup[] {
  const grouped = new Map<string, Post[]>();

  for (const group of [...current, ...incoming]) {
    const posts = grouped.get(group.date) ?? [];
    const postIds = new Set(posts.map(post => post.postId));
    for (const post of group.posts) {
      if (!postIds.has(post.postId)) {
        posts.push(post);
        postIds.add(post.postId);
      }
    }
    grouped.set(group.date, posts);
  }

  return Array.from(grouped.entries())
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([date, posts]) => ({ date, posts }));
}

function toSegments(posts: Post[]): Segment[] {
  const timelineSegs = new Map<number, Segment & { type: "timeline" }>();
  const freeSegs = new Map<string, Segment & { type: "free" }>();
  const order: string[] = [];

  for (const post of posts) {
    if (post.timelineId !== null) {
      const orderKey = `tl-${post.timelineId}`;
      if (!timelineSegs.has(post.timelineId)) {
        timelineSegs.set(post.timelineId, { type: "timeline", timelineId: post.timelineId, posts: [post] });
        order.push(orderKey);
      } else {
        timelineSegs.get(post.timelineId)!.posts.push(post);
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
  const { trips, upsertTrip, currentUser } = useStore();
  const photoDataPreference = usePhotoDataPreference();
  const [groups, setGroups] = useState<DateGroup[]>([]);
  const [lightbox, setLightbox] = useState<string | null>(null);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [isLoadingPosts, setIsLoadingPosts] = useState(false);
  const [postLoadError, setPostLoadError] = useState(false);
  const [activeDots, setActiveDots] = useState<Record<string, number>>({});
  const [editingPostId, setEditingPostId] = useState<number | null>(null);
  const [contentDraft, setContentDraft] = useState("");
  const [postActionId, setPostActionId] = useState<number | null>(null);
  const [postActionError, setPostActionError] = useState("");
  const scrollRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const listScrollRef = useRef<HTMLDivElement | null>(null);
  const loadMoreRef = useRef<HTMLDivElement | null>(null);
  const loadingRef = useRef(false);

  const goBack = () => {
    if (searchParams.get("from") === "timeline") {
      sessionStorage.setItem(`return-tab-${id}`, "timeline");
      router.push(`/trip/${id}?tab=timeline`);
      return;
    }

    router.back();
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
  }, [id, trip, upsertTrip]);

  const loadPostPage = useCallback(async (cursor: string | null, replace = false) => {
    if (!id || loadingRef.current) return;

    loadingRef.current = true;
    setIsLoadingPosts(true);
    setPostLoadError(false);

    try {
      const query = new URLSearchParams({ size: "10" });
      if (cursor) query.set("cursor", cursor);

      const response = await apiFetch(
        `${API_BASE}/api/v1/trips/${id}/posts?${query.toString()}`
      );
      if (!response.ok) {
        throw new Error("사진 기록을 불러오지 못했습니다.");
      }

      const body = await response.json();
      const data = (body.data ?? body) as PostCursorResponse | DateGroup[];
      const pageGroups = Array.isArray(data) ? data : (data.groups ?? []);

      setGroups(current => replace ? pageGroups : mergeGroups(current, pageGroups));
      setNextCursor(Array.isArray(data) ? null : data.nextCursor);
      setHasNext(Array.isArray(data) ? false : data.hasNext);
    } catch (error) {
      console.error(error);
      setPostLoadError(true);
    } finally {
      loadingRef.current = false;
      setIsLoadingPosts(false);
    }
  }, [id]);

  const updatePostContent = async (postId: number) => {
    if (!id || postActionId !== null) return;
    setPostActionId(postId);
    setPostActionError("");
    try {
      const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/posts/${postId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content: contentDraft.trim() || null }),
      });
      if (!response.ok) throw new Error("게시글 내용을 수정하지 못했습니다.");

      setGroups(current => current.map(group => ({
        ...group,
        posts: group.posts.map(post => post.postId === postId
          ? { ...post, content: contentDraft.trim() || null }
          : post),
      })));
      setEditingPostId(null);
    } catch (error) {
      setPostActionError(error instanceof Error ? error.message : "게시글 내용을 수정하지 못했습니다.");
    } finally {
      setPostActionId(null);
    }
  };

  const deletePost = async (postId: number) => {
    if (!id || postActionId !== null) return;
    if (!window.confirm("게시글과 서버에 저장된 사진을 모두 삭제할까요?")) return;

    setPostActionId(postId);
    setPostActionError("");
    try {
      const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/posts/${postId}`, {
        method: "DELETE",
      });
      if (!response.ok) throw new Error("게시글을 삭제하지 못했습니다.");

      setGroups(current => current
        .map(group => ({ ...group, posts: group.posts.filter(post => post.postId !== postId) }))
        .filter(group => group.posts.length > 0));
      if (editingPostId === postId) setEditingPostId(null);
    } catch (error) {
      setPostActionError(error instanceof Error ? error.message : "게시글을 삭제하지 못했습니다.");
    } finally {
      setPostActionId(null);
    }
  };

  useEffect(() => {
    const firstFrame = requestAnimationFrame(() => {
      void loadPostPage(null, true);
    });
    return () => cancelAnimationFrame(firstFrame);
  }, [loadPostPage]);

  useEffect(() => {
    const target = loadMoreRef.current;
    const root = listScrollRef.current;
    if (!target || !root || !hasNext) return;

    const observer = new IntersectionObserver(
      entries => {
        if (entries[0]?.isIntersecting) {
          void loadPostPage(nextCursor);
        }
      },
      { root, rootMargin: "240px 0px", threshold: 0 }
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [hasNext, loadPostPage, nextCursor]);

  if (!trip) return (
    <div className="flex min-h-[100dvh] items-center justify-center">
      <p className="text-gray-400 text-sm">불러오는 중...</p>
    </div>
  );

  return (
    <>
      {lightbox && (
        <div className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center" onClick={() => setLightbox(null)}>
          {/* 원격 S3 URL을 클릭할 때만 원본으로 요청하기 위해 기본 img를 사용한다. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={lightbox} alt="확대된 여행 사진" className="max-w-full max-h-full object-contain" />
        </div>
      )}
      <div className="flex h-[100dvh] flex-col overflow-hidden">
        <div className="app-safe-header flex shrink-0 items-center gap-3 px-4 pb-2">
          <button
            onClick={goBack}
            aria-label="뒤로가기"
            className="trip-header-icon-button w-10 h-10 rounded-full flex items-center justify-center"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
        </div>

        <div className="shrink-0 px-4 pb-3">
          <p className="text-2xl font-bold">{trip.name} 타임라인</p>
        </div>

        <div ref={listScrollRef} className="flex-1 min-h-0 overflow-y-auto px-4 pb-10 flex flex-col gap-4">
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
                          if ((e.target as HTMLElement).closest("button, textarea, input")) return;
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
                            const selectedUrls = selectPhotoUrls(post, photoDataPreference);
                            const previewSrc = resolveUrl(selectedUrls.previewUrl);
                            const detailSrc = resolveUrl(selectedUrls.detailUrl);
                            return (
                              <div key={post.postId} className={`rounded-2xl border p-3 flex flex-col gap-2 snap-start basis-full shrink-0 ${borderColor}`}>
                                <div className="flex items-start justify-between gap-3">
                                  <p className={`text-xs font-semibold ${labelColor}`}>{label}</p>
                                  {post.authorMemberId === currentUser.id && (
                                    <div className="flex shrink-0 gap-2 text-xs font-semibold">
                                      <button
                                        type="button"
                                        onClick={() => {
                                          setEditingPostId(post.postId);
                                          setContentDraft(post.content ?? "");
                                          setPostActionError("");
                                        }}
                                        className="text-blue-500"
                                      >
                                        내용 수정
                                      </button>
                                      <button
                                        type="button"
                                        onClick={() => void deletePost(post.postId)}
                                        disabled={postActionId === post.postId}
                                        className="text-red-500 disabled:opacity-50"
                                      >
                                        삭제
                                      </button>
                                    </div>
                                  )}
                                </div>
                                <div className="flex items-center justify-center rounded-xl overflow-hidden" style={{ height: "360px" }}>
                                  {previewSrc ? (
                                    // 원격 S3 URL에 브라우저 표준 lazy loading을 직접 적용한다.
                                    // eslint-disable-next-line @next/next/no-img-element
                                    <img
                                      src={previewSrc}
                                      alt="여행 사진"
                                      loading="lazy"
                                      decoding="async"
                                      draggable={false}
                                      className="max-w-full max-h-full object-contain cursor-pointer"
                                      onClick={() => detailSrc && setLightbox(detailSrc)}
                                    />
                                  ) : (
                                    <p className="text-sm text-gray-400">사진을 불러올 수 없습니다.</p>
                                  )}
                                </div>
                                {editingPostId === post.postId ? (
                                  <div className="flex flex-col gap-2">
                                    <textarea
                                      value={contentDraft}
                                      onChange={event => setContentDraft(event.target.value)}
                                      maxLength={1000}
                                      rows={3}
                                      placeholder="사진과 함께 남길 내용을 입력하세요."
                                      className="w-full resize-none rounded-xl border border-gray-200 bg-white p-3 text-sm outline-none focus:border-blue-400"
                                    />
                                    <div className="flex justify-end gap-2">
                                      <button
                                        type="button"
                                        onClick={() => setEditingPostId(null)}
                                        className="rounded-lg px-3 py-1.5 text-xs font-semibold text-gray-500"
                                      >
                                        취소
                                      </button>
                                      <button
                                        type="button"
                                        onClick={() => void updatePostContent(post.postId)}
                                        disabled={postActionId === post.postId}
                                        className="rounded-lg bg-blue-500 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                                      >
                                        저장
                                      </button>
                                    </div>
                                  </div>
                                ) : post.content ? (
                                  <p className="whitespace-pre-wrap break-words text-sm text-gray-700">{post.content}</p>
                                ) : null}
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

          <div ref={loadMoreRef} className="min-h-8 flex items-center justify-center">
            {isLoadingPosts && (
              <p className="text-xs text-gray-400">사진을 불러오는 중...</p>
            )}
            {postLoadError && (
              <button
                type="button"
                onClick={() => void loadPostPage(nextCursor, groups.length === 0)}
                className="text-xs font-semibold text-blue-500"
              >
                다시 불러오기
              </button>
            )}
          </div>
          {postActionError && (
            <p className="px-1 text-center text-xs text-red-500">{postActionError}</p>
          )}
        </div>
      </div>
    </>
  );
}
