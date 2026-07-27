"use client";

import { useRef, useState, useEffect, type MouseEvent } from "react";
import Link from "next/link";
import { useRouter, useParams } from "next/navigation";
import { Camera } from "@phosphor-icons/react";
import { useStore, TripDay, PhotoRecord, uid } from "../../../../../store";
import { API_BASE, apiFetch } from "../../../../../lib";

interface TimelineBlock {
  timeLineId?: number | null;
  startTime: string;
  endTime: string;
  confirmedPlaceName?: string | null;
  isTaken: boolean;
}

const UPLOAD_MODAL_EXIT_MS = 220;


export default function PhotoUploadPage() {
  const router = useRouter();
  const { id, dayNumber } = useParams<{ id: string; dayNumber: string }>();
  const { trips, updateTrip, upsertTrip } = useStore();

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [currentBlock, setCurrentBlock] = useState<TimelineBlock | null>(null);
  const [timelineLoaded, setTimelineLoaded] = useState(false);
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [uploadModalClosing, setUploadModalClosing] = useState(false);
  const [isLeavingPhotoPage, setIsLeavingPhotoPage] = useState(false);

  const trip = trips.find(t => t.id === id);
  const dayNum = parseInt(dayNumber);
  const dayIdx = trip?.days.findIndex(d => d.dayNumber === dayNum) ?? -1;

  const isDuringTrip = (() => {
    if (!trip) return false;
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const startDate = new Date(trip.startDate); startDate.setHours(0, 0, 0, 0);
    const endDate = new Date(trip.startDate); endDate.setDate(endDate.getDate() + trip.nights); endDate.setHours(23, 59, 59, 999);
    return today >= startDate && today <= endDate;
  })();

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
    if (!trip) return;
    if (!id || !isDuringTrip) {
      setTimelineLoaded(true);
      return;
    }
    apiFetch(`${API_BASE}/api/v1/trips/${id}/posts/is-taken?dayNumber=${dayNum}`)
      .then(r => r.json())
      .then(body => {
        setCurrentBlock(body.data ?? null);
      })
      .catch(() => {})
      .finally(() => setTimelineLoaded(true));
  }, [id, isDuringTrip, trip]);

  useEffect(() => {
    if (!showUploadModal) {
      setUploadModalOpen(false);
      return;
    }

    setUploadModalOpen(false);
    const firstFrame = requestAnimationFrame(() => {
      requestAnimationFrame(() => setUploadModalOpen(true));
    });

    return () => cancelAnimationFrame(firstFrame);
  }, [showUploadModal]);

  if (!trip || dayIdx < 0) return (
    <div className="flex items-center justify-center min-h-screen">
      <p className="text-gray-400 text-sm">불러오는 중...</p>
    </div>
  );

  const day = trip.days[dayIdx];

  const setDay = (updated: TripDay) => {
    updateTrip({ ...trip, days: trip.days.map((d, i) => i === dayIdx ? updated : d) });
  };

  const recordKey = currentBlock ? String(currentBlock.timeLineId) : `free-${dayNum}`;
  const record = day.records.find(r => r.blockId === recordKey);
  const showUploadButton = !!selectedFile || record?.status === "uploaded";

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setSelectedFile(file);
    setPreviewUrl(URL.createObjectURL(file));
  };

  const handleUpload = async () => {
    if (!selectedFile) {
      fileInputRef.current?.click();
      return;
    }
    setUploading(true);
    try {
      const form = new FormData();
      form.append("image", selectedFile);
      form.append(
        "request",
        new Blob([JSON.stringify({ timeLineId: currentBlock?.timeLineId ?? null })], { type: "application/json" })
      );
      const res = await apiFetch(`${API_BASE}/api/v1/trips/${id}/posts`, {
        method: "POST",
        body: form,
      });
      if (res.ok) {
        setUploadModalClosing(false);
        setShowUploadModal(true);
        return;
      }
      const title = currentBlock
        ? `${currentBlock.startTime.slice(11, 16)}~${currentBlock.endTime.slice(11, 16)} 활동`
        : "자유 시간";
      const already = day.records.find(r => r.blockId === recordKey);
      const newRecords = already
        ? day.records.map(r => r.blockId === recordKey ? { ...r, status: "uploaded" as const } : r)
        : [...day.records, { id: uid(), blockId: recordKey, title, status: "uploaded" } as PhotoRecord];
      setDay({ ...day, records: newRecords });
      setSelectedFile(null);
      setPreviewUrl(null);
    } catch (e) {
      console.error(e);
    } finally {
      setUploading(false);
    }
  };

  const closeUploadModal = () => {
    if (uploadModalClosing) return;
    setUploadModalOpen(false);
    setUploadModalClosing(true);
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.setTimeout(
      () => {
        setShowUploadModal(false);
        router.push(`/trip/${id}/timeline?from=timeline`);
      },
      prefersReducedMotion ? 0 : UPLOAD_MODAL_EXIT_MS
    );
  };

  const moveToTripTab = (target: "trip" | "candidates" | "vote" | "timeline") => {
    if (target === "timeline") return;
    sessionStorage.setItem(`active-tab-${id}`, target);
    sessionStorage.removeItem(`return-tab-${id}`);
    router.push(`/trip/${id}`);
  };

  const goAllTimelineWithTransition = (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    if (isLeavingPhotoPage) return;

    setIsLeavingPhotoPage(true);
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.setTimeout(
      () => router.push(`/trip/${id}/timeline?from=timeline`),
      prefersReducedMotion ? 0 : 240
    );
  };

  const goHomeWithTransition = (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    if (isLeavingPhotoPage) return;

    setIsLeavingPhotoPage(true);
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.setTimeout(
      () => router.push("/home"),
      prefersReducedMotion ? 0 : 240
    );
  };

  return (
    <div className={`photo-page-transition flex flex-col h-screen px-4 pt-3 pb-[4.875rem] ${isLeavingPhotoPage ? "trip-page-exit" : ""}`}>
      <div className="relative flex items-center shrink-0 h-24">
        <Link
          href="/home"
          onClick={goHomeWithTransition}
          aria-label="여행방 목록"
          className="trip-header-icon-button absolute left-0 top-1/2 z-10 w-10 h-10 -translate-y-1/2 rounded-full flex items-center justify-center"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth={2.2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 10.75 12 4l8.25 6.75" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M5.75 9.75V20h12.5V9.75" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 20v-5.25h4.5V20" />
          </svg>
        </Link>
        <h1 className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 font-semibold text-base whitespace-nowrap">{dayNum}일차 사진 기록</h1>
        {isDuringTrip ? (
          <Link
            href={`/trip/${id}/timeline?from=timeline`}
            onClick={goAllTimelineWithTransition}
            className="absolute right-0 top-1/2 -translate-y-1/2 text-xs font-semibold text-blue-500"
          >
            전체보기
          </Link>
        ) : (
          <div className="absolute right-0 top-1/2 w-16 -translate-y-1/2" />
        )}
      </div>

      {/* 현재 시간대 정보 */}
      <div className="shrink-0 pb-3">
        {!timelineLoaded ? (
          <div className="w-full p-4 rounded-3xl bg-gray-100 flex items-center justify-center">
            <p className="text-sm text-gray-400">불러오는 중...</p>
          </div>
        ) : currentBlock ? (
          <div className="w-full px-5 py-3 rounded-3xl bg-blue-50 flex flex-col items-center gap-1">
            <p className="text-xs font-bold text-blue-400">
              {currentBlock.startTime.slice(11, 16)} ~ {currentBlock.endTime.slice(11, 16)}
            </p>
            <p className="font-bold text-base text-blue-800 text-center">
              {currentBlock.confirmedPlaceName
                ? `${currentBlock.confirmedPlaceName} 에서의 한 컷`
                : "자유롭게 한 컷"}
            </p>
          </div>
        ) : (
          <div className="w-full px-5 py-3 rounded-3xl bg-gray-50 flex items-center gap-3">
            <span className="text-2xl">😊</span>
            <div>
              <p className="font-bold text-gray-700">자유 시간</p>
              <p className="text-xs text-gray-400">자유 시간에도 사진을 남겨보세요!</p>
            </div>
          </div>
        )}
      </div>

      {/* 카메라 / 미리보기 영역 */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        capture="environment"
        className="hidden"
        onChange={handleFileChange}
      />

      <button
        type="button"
        onClick={() => { if (!currentBlock?.isTaken) fileInputRef.current?.click(); }}
        aria-disabled={!!currentBlock?.isTaken}
        className={`photo-capture-panel flex-1 rounded-3xl overflow-hidden flex flex-col items-center justify-center gap-2 transition-opacity ${currentBlock?.isTaken ? "cursor-default" : "active:opacity-80"}`}
      >
        {currentBlock?.isTaken ? (
          <>
            <Camera size={52} weight="regular" className="text-blue-400" />
            <p className="font-bold text-blue-400 text-base">해당 타임라인에 이미 사진 찍으셨네요!</p>
            <p className="text-sm text-blue-400">전체 보기를 눌러 모임에서 찍은 사진을 구경하세요</p>
          </>
        ) : previewUrl ? (
          <img src={previewUrl} alt="preview" className="w-full h-full object-contain" />
        ) : (
          <>
            <Camera size={52} weight="regular" className="text-gray-400" />
            <p className="text-sm text-gray-400">탭해서 사진 찍기</p>
          </>
        )}
      </button>

      {showUploadModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div
            className={`upload-complete-backdrop absolute inset-0 bg-black/40 ${uploadModalOpen ? "is-open" : ""} ${uploadModalClosing ? "is-closing" : ""}`}
            onClick={closeUploadModal}
          />
          <div className={`upload-complete-card relative w-72 bg-white rounded-3xl p-6 flex flex-col items-center gap-4 shadow-xl ${uploadModalOpen ? "is-open" : ""} ${uploadModalClosing ? "is-closing" : ""}`}>
            <Camera size={52} weight="regular" className="text-green-500" />
            <p className="text-lg font-bold text-center">업로드 완료!</p>
            <p className="text-sm text-gray-500 text-center">사진이 모임에 공유되었어요.</p>
            <button
              onClick={closeUploadModal}
              className="w-full py-3.5 rounded-2xl font-semibold text-white"
              style={{ background: "#22c55e" }}
            >
              전체 사진 보기
            </button>
          </div>
        </div>
      )}

      {showUploadButton && (
        <div className="py-4 shrink-0">
          <button
            onClick={handleUpload}
            disabled={uploading || (isDuringTrip && !timelineLoaded) || !!currentBlock?.isTaken}
            className="w-full py-4 rounded-2xl text-white font-semibold transition-colors disabled:opacity-60"
            style={{
              background: record?.status === "uploaded"
                ? "#16a34a"
                : uploading ? "#86efac" : "#22c55e",
            }}
          >
            {uploading ? "업로드 중..." : record?.status === "uploaded" ? "✓ 사진 올렸어요" : "사진 올리기"}
          </button>
        </div>
      )}

      <div
        className="pointer-events-none fixed bottom-0 left-0 right-0 z-40 flex justify-center px-6"
        style={{ paddingBottom: "max(1rem, env(safe-area-inset-bottom))" }}
      >
        <div className="trip-floating-tab-bar pointer-events-auto is-timeline">
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
            const active = key === "timeline";
            return (
              <button
                key={key}
                onClick={() => moveToTripTab(key)}
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
    </div>
  );
}
