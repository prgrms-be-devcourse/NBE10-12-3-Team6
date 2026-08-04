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
  dominantColor?: string | null;
  createdAt?: string;
  startTime?: string;
  endTime?: string;
  confirmedPlaceName?: string;
  likeCount: number;
}

interface PostLikeStatus {
  postId: number;
  liked: boolean;
  likeCount: number;
}

const POST_CONTENT_MAX_LENGTH = 20;

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

const LIGHTBOX_EXIT_MS = 220;
const POST_ACTION_MENU_EXIT_MS = 150;
const DELETE_MODAL_EXIT_MS = 220;

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
  const [isLightboxClosing, setIsLightboxClosing] = useState(false);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [isLoadingPosts, setIsLoadingPosts] = useState(false);
  const [postLoadError, setPostLoadError] = useState(false);
  const [activeDots, setActiveDots] = useState<Record<string, number>>({});
  const [editingPostId, setEditingPostId] = useState<number | null>(null);
  const [openPostMenuId, setOpenPostMenuId] = useState<number | null>(null);
  const [closingPostMenuId, setClosingPostMenuId] = useState<number | null>(null);
  const [deleteTargetPostId, setDeleteTargetPostId] = useState<number | null>(null);
  const [isDeleteModalPresented, setIsDeleteModalPresented] = useState(false);
  const [isDeleteModalClosing, setIsDeleteModalClosing] = useState(false);
  const [contentDraft, setContentDraft] = useState("");
  const [contentEditError, setContentEditError] = useState("");
  const [postActionId, setPostActionId] = useState<number | null>(null);
  const [postActionError, setPostActionError] = useState("");
  const [likeStatuses, setLikeStatuses] = useState<Record<number, PostLikeStatus>>({});
  const [likeActionIds, setLikeActionIds] = useState<Set<number>>(new Set());
  const scrollRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const listScrollRef = useRef<HTMLDivElement | null>(null);
  const loadMoreRef = useRef<HTMLDivElement | null>(null);
  const loadingRef = useRef(false);
  const lightboxCloseTimerRef = useRef<number | null>(null);
  const postMenuRef = useRef<HTMLDivElement | null>(null);
  const postMenuCloseTimerRef = useRef<number | null>(null);
  const deleteModalCloseTimerRef = useRef<number | null>(null);

  const openLightbox = useCallback((imageUrl: string) => {
    if (lightboxCloseTimerRef.current !== null) {
      window.clearTimeout(lightboxCloseTimerRef.current);
      lightboxCloseTimerRef.current = null;
    }
    setIsLightboxClosing(false);
    setLightbox(imageUrl);
  }, []);

  const closeLightbox = useCallback(() => {
    if (!lightbox || isLightboxClosing) return;

    setIsLightboxClosing(true);
    const prefersReducedMotion = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    ).matches;
    lightboxCloseTimerRef.current = window.setTimeout(
      () => {
        setLightbox(null);
        setIsLightboxClosing(false);
        lightboxCloseTimerRef.current = null;
      },
      prefersReducedMotion ? 0 : LIGHTBOX_EXIT_MS,
    );
  }, [isLightboxClosing, lightbox]);

  useEffect(() => {
    if (!lightbox) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") closeLightbox();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [closeLightbox, lightbox]);

  useEffect(() => () => {
    if (lightboxCloseTimerRef.current !== null) {
      window.clearTimeout(lightboxCloseTimerRef.current);
    }
  }, []);

  const closePostActionMenu = useCallback(() => {
    if (openPostMenuId === null || closingPostMenuId !== null) return;

    setClosingPostMenuId(openPostMenuId);
    const prefersReducedMotion = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    ).matches;
    postMenuCloseTimerRef.current = window.setTimeout(
      () => {
        setOpenPostMenuId(null);
        setClosingPostMenuId(null);
        postMenuCloseTimerRef.current = null;
      },
      prefersReducedMotion ? 0 : POST_ACTION_MENU_EXIT_MS,
    );
  }, [closingPostMenuId, openPostMenuId]);

  const togglePostActionMenu = useCallback((postId: number) => {
    if (openPostMenuId === postId) {
      closePostActionMenu();
      return;
    }

    if (postMenuCloseTimerRef.current !== null) {
      window.clearTimeout(postMenuCloseTimerRef.current);
      postMenuCloseTimerRef.current = null;
    }
    setClosingPostMenuId(null);
    setOpenPostMenuId(postId);
  }, [closePostActionMenu, openPostMenuId]);

  const dismissPostActionMenu = useCallback(() => {
    if (postMenuCloseTimerRef.current !== null) {
      window.clearTimeout(postMenuCloseTimerRef.current);
      postMenuCloseTimerRef.current = null;
    }
    setOpenPostMenuId(null);
    setClosingPostMenuId(null);
  }, []);

  useEffect(() => {
    if (openPostMenuId === null) return;

    const handlePointerDown = (event: PointerEvent) => {
      if (!postMenuRef.current?.contains(event.target as Node)) {
        closePostActionMenu();
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") closePostActionMenu();
    };

    document.addEventListener("pointerdown", handlePointerDown, true);
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown, true);
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [closePostActionMenu, openPostMenuId]);

  useEffect(() => () => {
    if (postMenuCloseTimerRef.current !== null) {
      window.clearTimeout(postMenuCloseTimerRef.current);
    }
  }, []);

  const openDeleteModal = useCallback((postId: number) => {
    if (deleteModalCloseTimerRef.current !== null) {
      window.clearTimeout(deleteModalCloseTimerRef.current);
      deleteModalCloseTimerRef.current = null;
    }
    setPostActionError("");
    setIsDeleteModalClosing(false);
    setDeleteTargetPostId(postId);
  }, []);

  useEffect(() => {
    if (deleteTargetPostId === null) {
      setIsDeleteModalPresented(false);
      return;
    }

    setIsDeleteModalClosing(false);
    setIsDeleteModalPresented(false);
    const frame = window.requestAnimationFrame(() => {
      setIsDeleteModalPresented(true);
    });
    return () => window.cancelAnimationFrame(frame);
  }, [deleteTargetPostId]);

  const closeDeleteModal = useCallback(() => {
    if (deleteTargetPostId === null || isDeleteModalClosing) return;

    setIsDeleteModalPresented(false);
    setIsDeleteModalClosing(true);
    const prefersReducedMotion = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    ).matches;
    deleteModalCloseTimerRef.current = window.setTimeout(
      () => {
        setDeleteTargetPostId(null);
        setIsDeleteModalPresented(false);
        setIsDeleteModalClosing(false);
        setPostActionError("");
        deleteModalCloseTimerRef.current = null;
      },
      prefersReducedMotion ? 0 : DELETE_MODAL_EXIT_MS,
    );
  }, [deleteTargetPostId, isDeleteModalClosing]);

  useEffect(() => {
    if (deleteTargetPostId === null) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && postActionId === null) closeDeleteModal();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [closeDeleteModal, deleteTargetPostId, postActionId]);

  useEffect(() => () => {
    if (deleteModalCloseTimerRef.current !== null) {
      window.clearTimeout(deleteModalCloseTimerRef.current);
    }
  }, []);

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
    if (contentDraft.length > POST_CONTENT_MAX_LENGTH) {
      setContentEditError("20자 까지 입력이 가능합니다.");
      return;
    }
    setContentEditError("");
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
      setContentEditError("");
    } catch (error) {
      setPostActionError(error instanceof Error ? error.message : "게시글 내용을 수정하지 못했습니다.");
    } finally {
      setPostActionId(null);
    }
  };

  const deletePost = async (postId: number) => {
    if (!id || postActionId !== null) return;

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
      closeDeleteModal();
    } catch (error) {
      setPostActionError(error instanceof Error ? error.message : "게시글을 삭제하지 못했습니다.");
    } finally {
      setPostActionId(null);
    }
  };

  const toggleLike = async (post: Post) => {
    if (!id || likeActionIds.has(post.postId)) return;

    const current = likeStatuses[post.postId];
    if (!current) return;

    setLikeActionIds(ids => new Set(ids).add(post.postId));
    setPostActionError("");
    try {
      const response = await apiFetch(
        `${API_BASE}/api/v1/trips/${id}/posts/${post.postId}/likes`,
        { method: current.liked ? "DELETE" : "POST" }
      );
      if (!response.ok) throw new Error("좋아요 상태를 변경하지 못했습니다.");

      const body = await response.json();
      const status = (body.data ?? body) as PostLikeStatus;
      setLikeStatuses(statuses => ({ ...statuses, [post.postId]: status }));
    } catch (error) {
      setPostActionError(error instanceof Error ? error.message : "좋아요 상태를 변경하지 못했습니다.");
    } finally {
      setLikeActionIds(ids => {
        const next = new Set(ids);
        next.delete(post.postId);
        return next;
      });
    }
  };

  useEffect(() => {
    const firstFrame = requestAnimationFrame(() => {
      void loadPostPage(null, true);
    });
    return () => cancelAnimationFrame(firstFrame);
  }, [loadPostPage]);

  useEffect(() => {
    if (!id) return;

    const posts = groups.flatMap(group => group.posts);
    const missing = posts.filter(post => !likeStatuses[post.postId]);
    if (missing.length === 0) return;

    let cancelled = false;
    Promise.all(missing.map(async post => {
      const response = await apiFetch(
        `${API_BASE}/api/v1/trips/${id}/posts/${post.postId}/likes`
      );
      if (!response.ok) throw new Error("좋아요 상태를 불러오지 못했습니다.");
      const body = await response.json();
      return (body.data ?? body) as PostLikeStatus;
    }))
      .then(statuses => {
        if (cancelled) return;
        setLikeStatuses(current => ({
          ...current,
          ...Object.fromEntries(statuses.map(status => [status.postId, status])),
        }));
      })
      .catch(error => console.error(error));

    return () => { cancelled = true; };
  }, [groups, id, likeStatuses]);

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
        <div
          className={`photo-lightbox-backdrop fixed inset-0 z-[100] flex items-center justify-center ${
            isLightboxClosing ? "is-closing" : ""
          }`}
          role="dialog"
          aria-modal="true"
          aria-label="확대된 여행 사진"
          onClick={closeLightbox}
        >
          <div className="photo-lightbox-panel" onClick={event => event.stopPropagation()}>
            <div className="photo-lightbox-header">
              <button
                type="button"
                className="photo-lightbox-close-button ml-auto"
                aria-label="확대 사진 닫기"
                onClick={closeLightbox}
              >
                <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18 18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div className="photo-lightbox-stage">
              {/* 원격 S3 URL을 클릭할 때만 상세 이미지를 요청하기 위해 기본 img를 사용한다. */}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={lightbox}
                alt="확대된 여행 사진"
                draggable={false}
                className="photo-lightbox-image"
              />
            </div>
          </div>
        </div>
      )}
      {deleteTargetPostId !== null && (
        <div
          className={`delete-confirm-backdrop fixed inset-0 z-[110] flex items-center justify-center bg-black/55 px-5 ${
            isDeleteModalPresented ? "is-open" : ""
          } ${
            isDeleteModalClosing ? "is-closing" : ""
          }`}
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="delete-post-title"
          aria-describedby="delete-post-description"
          onClick={() => {
            if (postActionId === null) closeDeleteModal();
          }}
        >
          <div
            className="delete-confirm-card w-full max-w-sm rounded-3xl border border-gray-200 bg-white p-5 shadow-2xl"
            onClick={event => event.stopPropagation()}
          >
            <div className="flex flex-col items-center text-center">
              <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-500">
                <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1 14H6L5 6M10 11v5M14 11v5" />
                </svg>
              </div>
              <h2 id="delete-post-title" className="text-lg font-bold text-gray-900">
                사진을 삭제할까요?
              </h2>
              <p id="delete-post-description" className="mt-2 text-sm leading-6 text-gray-500">
                사진과 작성한 내용이 함께 삭제되며
                <br />
                삭제 후에는 되돌릴 수 없습니다.
              </p>
              {postActionError && (
                <p className="mt-3 text-sm font-semibold text-red-500">{postActionError}</p>
              )}
            </div>
            <div className="mt-5 grid grid-cols-2 gap-2.5">
              <button
                type="button"
                autoFocus
                onClick={closeDeleteModal}
                disabled={postActionId !== null}
                className="rounded-2xl bg-gray-100 py-3.5 text-sm font-semibold text-gray-700 transition-colors hover:bg-gray-200 disabled:opacity-50"
              >
                취소
              </button>
              <button
                type="button"
                onClick={() => void deletePost(deleteTargetPostId)}
                disabled={postActionId !== null}
                className="rounded-2xl bg-red-500 py-3.5 text-sm font-semibold text-white transition-[background-color,transform] hover:bg-red-600 active:scale-[0.98] disabled:opacity-50"
              >
                {postActionId === deleteTargetPostId ? "삭제 중..." : "삭제"}
              </button>
            </div>
          </div>
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
                          const labelColor = seg.type === "timeline" ? "text-blue-500" : "text-gray-500";
                          const labelSurface = seg.type === "timeline"
                            ? "border-blue-100 bg-blue-50"
                            : "border-gray-200 bg-gray-100";

                          return seg.posts.map(post => {
                            const selectedUrls = selectPhotoUrls(post, photoDataPreference);
                            const previewSrc = resolveUrl(selectedUrls.previewUrl);
                            const detailSrc = resolveUrl(selectedUrls.detailUrl);
                            const dominantColor = /^#[0-9a-fA-F]{6}$/.test(post.dominantColor ?? "")
                              ? post.dominantColor
                              : null;
                            return (
                              <div key={post.postId} className="snap-start basis-full shrink-0 rounded-2xl border border-blue-100 bg-white shadow-sm">
                                <div className="flex items-center justify-between gap-3 px-3 pb-2 pt-3">
                                  <p className={`min-w-0 truncate rounded-full border px-2.5 py-1 text-xs font-semibold ${labelColor} ${labelSurface}`}>
                                    {label}
                                  </p>
                                  {Number(post.authorMemberId) === Number(currentUser.id) && (
                                    <div
                                      ref={openPostMenuId === post.postId ? postMenuRef : undefined}
                                      className="relative shrink-0"
                                    >
                                      <button
                                        type="button"
                                        onClick={() => togglePostActionMenu(post.postId)}
                                        aria-label="게시글 메뉴"
                                        aria-haspopup="menu"
                                        aria-expanded={openPostMenuId === post.postId}
                                        className="flex h-8 w-8 items-center justify-center rounded-full bg-gray-50 text-gray-500 transition-colors hover:bg-gray-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-300"
                                      >
                                        <svg className="h-5 w-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                                          <circle cx="5" cy="12" r="1.5" />
                                          <circle cx="12" cy="12" r="1.5" />
                                          <circle cx="19" cy="12" r="1.5" />
                                        </svg>
                                      </button>

                                      {openPostMenuId === post.postId && (
                                        <div
                                          role="menu"
                                          aria-label="게시글 관리"
                                          className={`post-action-menu absolute right-0 top-full z-30 mt-1.5 w-36 overflow-hidden rounded-xl border border-gray-200 bg-white p-1.5 shadow-xl ${
                                            closingPostMenuId === post.postId ? "is-closing" : ""
                                          }`}
                                        >
                                          <button
                                            type="button"
                                            role="menuitem"
                                            onClick={() => {
                                              dismissPostActionMenu();
                                              setEditingPostId(post.postId);
                                              setContentDraft(post.content ?? "");
                                              setContentEditError("");
                                              setPostActionError("");
                                            }}
                                            className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2.5 text-left text-sm font-semibold text-gray-700 transition-colors hover:bg-gray-100"
                                          >
                                            <svg className="h-4 w-4 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="m16.862 3.487 3.651 3.651M18.688 1.662a2.582 2.582 0 0 1 3.65 3.65L8.5 19.15 3 21l1.85-5.5L18.688 1.662Z" />
                                            </svg>
                                            내용 수정
                                          </button>
                                          <button
                                            type="button"
                                            role="menuitem"
                                            onClick={() => {
                                              dismissPostActionMenu();
                                              openDeleteModal(post.postId);
                                            }}
                                            disabled={postActionId === post.postId}
                                            className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2.5 text-left text-sm font-semibold text-red-500 transition-colors hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
                                          >
                                            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1 14H6L5 6M10 11v5M14 11v5" />
                                            </svg>
                                            삭제
                                          </button>
                                        </div>
                                      )}
                                    </div>
                                  )}
                                </div>
                                <div
                                  className={`relative mx-3 flex items-center justify-center overflow-hidden rounded-xl bg-gray-50 ${previewSrc ? "h-auto" : "min-h-[320px]"}`}
                                  style={dominantColor ? { backgroundColor: dominantColor } : undefined}
                                >
                                  {previewSrc && !dominantColor && (
                                    <div
                                      aria-hidden="true"
                                      className="pointer-events-none absolute -inset-10 scale-110 bg-cover bg-center opacity-60 blur-3xl"
                                      style={{ backgroundImage: `url(${JSON.stringify(previewSrc)})` }}
                                    />
                                  )}
                                  {previewSrc ? (
                                    <button
                                      type="button"
                                      className="relative z-10 flex h-auto w-full cursor-zoom-in items-center justify-center"
                                      aria-label="사진 확대"
                                      disabled={!detailSrc}
                                      onClick={() => detailSrc && openLightbox(detailSrc)}
                                    >
                                      {/* 원격 S3 URL에 브라우저 표준 lazy loading을 직접 적용한다. */}
                                      {/* eslint-disable-next-line @next/next/no-img-element */}
                                      <img
                                        src={previewSrc}
                                        alt="여행 사진"
                                        loading="lazy"
                                        decoding="async"
                                        draggable={false}
                                        className="pointer-events-none h-auto max-h-[320px] max-w-full object-contain"
                                      />
                                    </button>
                                  ) : (
                                    <p className="text-sm text-gray-400">사진을 불러올 수 없습니다.</p>
                                  )}
                                </div>
                                <div className="mt-3 rounded-b-2xl border-t border-blue-100 bg-blue-50 px-3 py-3">
                                  <div className="flex min-h-8 min-w-0 items-center gap-3">
                                    <button
                                      type="button"
                                      onClick={() => void toggleLike(post)}
                                      disabled={!likeStatuses[post.postId] || likeActionIds.has(post.postId)}
                                      aria-label={likeStatuses[post.postId]?.liked ? "좋아요 취소" : "좋아요"}
                                      aria-pressed={likeStatuses[post.postId]?.liked ?? false}
                                      className={`flex w-fit shrink-0 items-center gap-1.5 rounded-full border border-blue-100 bg-white px-2.5 py-1.5 text-sm font-semibold shadow-sm transition-[color,background-color,transform] active:scale-95 disabled:cursor-not-allowed disabled:opacity-50 ${
                                        likeStatuses[post.postId]?.liked
                                          ? "text-red-500"
                                          : "text-blue-500"
                                      }`}
                                    >
                                      <svg
                                        className="h-5 w-5"
                                        viewBox="0 0 24 24"
                                        fill={likeStatuses[post.postId]?.liked ? "currentColor" : "none"}
                                        stroke="currentColor"
                                        strokeWidth="2"
                                        aria-hidden="true"
                                      >
                                        <path strokeLinecap="round" strokeLinejoin="round" d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78L12 21.23l8.84-8.84a5.5 5.5 0 0 0 0-7.78Z" />
                                      </svg>
                                      <span>{likeStatuses[post.postId]?.likeCount ?? post.likeCount ?? 0}</span>
                                    </button>
                                    {editingPostId !== post.postId && post.content && (
                                      <p className="min-w-0 flex-1 whitespace-pre-wrap break-words text-right text-sm leading-5 text-gray-700">
                                        {post.content}
                                      </p>
                                    )}
                                  </div>

                                  {editingPostId === post.postId ? (
                                    <div className="mt-2 flex flex-col gap-2">
                                      <textarea
                                        value={contentDraft}
                                        onChange={event => {
                                          const nextContent = event.target.value;
                                          setContentDraft(nextContent);
                                          if (nextContent.length <= POST_CONTENT_MAX_LENGTH) setContentEditError("");
                                        }}
                                        maxLength={POST_CONTENT_MAX_LENGTH}
                                        rows={2}
                                        placeholder="사진과 함께 남길 내용을 입력하세요."
                                        aria-invalid={!!contentEditError}
                                        aria-describedby={contentEditError ? `post-content-edit-error-${post.postId}` : undefined}
                                        className={`w-full resize-none rounded-xl border bg-white p-3 text-sm outline-none ${contentEditError ? "border-red-400" : "border-gray-200 focus:border-blue-400"}`}
                                      />
                                      <div className="flex items-start justify-between gap-3 px-1">
                                        <p id={`post-content-edit-error-${post.postId}`} className="text-xs font-semibold text-red-500">
                                          {contentEditError}
                                        </p>
                                        <p className={`shrink-0 text-xs ${contentDraft.length > POST_CONTENT_MAX_LENGTH ? "text-red-500" : "text-gray-400"}`}>
                                          {contentDraft.length}/{POST_CONTENT_MAX_LENGTH}
                                        </p>
                                      </div>
                                      <div className="flex justify-end gap-2">
                                        <button
                                          type="button"
                                          onClick={() => {
                                            setEditingPostId(null);
                                            setContentEditError("");
                                          }}
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
                                  ) : null}
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
