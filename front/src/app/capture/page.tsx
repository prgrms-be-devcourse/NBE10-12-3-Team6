"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

interface CaptureItem {
  question: string;
  answer: string;
  time: string;
  type: string;
}

const captureItems: CaptureItem[] = [
  { question: "드디어 만났어요!", answer: "서울역 2층 대합실", time: "09:00", type: "1일차 · 출발 전" },
  { question: "첫 여행지는 어디인가요?", answer: "부산 도착", time: "13:00", type: "1일차 · 도착" },
  { question: "부산에서 첫 끼는?", answer: "돼지국밥", time: "13:30", type: "1일차 · 식사" },
  { question: "체크인 전 어디서 쉴까요?", answer: "오션뷰 카페", time: "14:30", type: "1일차 · 카페" },
  { question: "숙소에 도착했나요?", answer: "해운대 오션뷰 호텔", time: "15:20", type: "1일차 · 숙소" },
  { question: "저녁 활동은?", answer: "광안리 산책", time: "19:00", type: "1일차 · 활동" },
  { question: "저녁은 어디서 먹을까요?", answer: "초밥집", time: "20:00", type: "1일차 · 식사" },
  { question: "오늘은 어디로 이동하나요?", answer: "경주로 이동", time: "11:00", type: "2일차 · 이동" },
  { question: "경주에 도착했나요?", answer: "경주 도착", time: "13:00", type: "2일차 · 도착" },
  { question: "경주에서 점심은?", answer: "경주 한식집", time: "13:10", type: "2일차 · 식사" },
  { question: "경주에서 어디를 구경할까요?", answer: "황리단길 구경", time: "14:30", type: "2일차 · 활동" },
  { question: "잠깐 쉬어갈 카페는?", answer: "한옥 카페", time: "16:00", type: "2일차 · 카페" },
  { question: "이제 어디로 돌아가나요?", answer: "부산으로 복귀", time: "20:00", type: "2일차 · 복귀" },
  { question: "오늘 숙소에 도착했나요?", answer: "부산역 근처 호텔", time: "21:30", type: "2일차 · 숙소" },
  { question: "마지막 날을 시작해볼까요?", answer: "체크아웃", time: "10:30", type: "3일차 · 숙소" },
  { question: "마지막 점심은?", answer: "밀면", time: "12:00", type: "3일차 · 식사" },
  { question: "마지막으로 어디를 둘러볼까요?", answer: "해운대 산책", time: "14:00", type: "3일차 · 활동" },
  { question: "기차 타기 전 어디서 쉴까요?", answer: "부산역 근처 카페", time: "15:30", type: "3일차 · 카페" },
  { question: "여행을 마무리할까요?", answer: "부산역 출발", time: "17:00", type: "3일차 · 귀가" },
];

export default function CapturePage() {
  const router = useRouter();
  const [index, setIndex] = useState(0);

  if (index >= captureItems.length) {
    return <MemoryDoneView />;
  }

  const item = captureItems[index];

  return (
    <div className="flex flex-col min-h-screen px-4">
      {/* Top bar */}
      <div className="flex items-center gap-3 pt-12 pb-2">
        <button onClick={() => router.back()} className="text-blue-500">
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 className="font-semibold text-base flex-1 text-center">2박 3일 여행 중</h1>
        <div className="w-6" />
      </div>

      {/* Progress dots */}
      <div className="flex gap-1 justify-center py-3">
        {captureItems.map((_, i) => (
          <div
            key={i}
            className={`h-1 rounded-full transition-all duration-300 ${
              i === index ? "w-4 bg-orange-400" : i < index ? "w-2 bg-orange-200" : "w-2 bg-gray-200"
            }`}
          />
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 flex flex-col items-center justify-center gap-6 py-4">
        <div className="text-center">
          <p className="text-sm text-gray-400">{item.time}에 알림이 올 예정입니다.</p>
        </div>

        <div className="flex flex-col items-center gap-4 w-full">
          <span className="text-xs font-bold px-3 py-1.5 rounded-full bg-orange-100 text-orange-600">
            {item.type}
          </span>
          <p className="text-xl font-bold text-center">{item.question}</p>
          <p className="text-4xl font-extrabold text-center leading-tight">{item.answer}</p>

          {/* Photo upload area */}
          <div className="w-full h-56 bg-gray-100 rounded-3xl flex flex-col items-center justify-center gap-3 mt-2">
            <svg className="w-10 h-10 text-gray-400" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 15.2A3.2 3.2 0 0 1 8.8 12 3.2 3.2 0 0 1 12 8.8 3.2 3.2 0 0 1 15.2 12 3.2 3.2 0 0 1 12 15.2M12 7a5 5 0 0 0-5 5 5 5 0 0 0 5 5 5 5 0 0 0 5-5 5 5 0 0 0-5-5m-7 11a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1h2l2-2h4l2 2h2a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H5z" />
            </svg>
            <p className="text-sm text-gray-400">사진 또는 숏폼 업로드 영역</p>
          </div>
        </div>
      </div>

      {/* Bottom buttons */}
      <div className="flex flex-col gap-3 py-4">
        <button
          onClick={() => setIndex((i) => i + 1)}
          className="w-full py-4 rounded-2xl bg-orange-500 text-white font-semibold"
        >
          사진 올리기
        </button>
        <button
          onClick={() => setIndex((i) => i + 1)}
          className="w-full py-4 rounded-2xl bg-gray-100 text-gray-700 font-semibold"
        >
          건너뛰기
        </button>
      </div>
    </div>
  );
}

// ── After all items done → memory view ───────────────────────────────────────

const memoryPages = [
  { title: "부산 도착", meta: "1일차 · 13:00 · 첫 번째 기록" },
  { title: "돼지국밥", meta: "1일차 · 13:30 · 점심 기록" },
  { title: "광안리 산책", meta: "1일차 · 19:00 · 야경 기록" },
  { title: "경주 도착", meta: "2일차 · 13:00 · 이동 기록" },
  { title: "황리단길 구경", meta: "2일차 · 14:30 · 경주 활동 기록" },
  { title: "한옥 카페", meta: "2일차 · 16:00 · 카페 기록" },
  { title: "부산역 근처 호텔", meta: "2일차 · 21:30 · 숙소 도착 기록" },
  { title: "해운대 산책", meta: "3일차 · 14:00 · 마지막 활동 기록" },
  { title: "부산역 출발", meta: "3일차 · 17:00 · 여행 마무리" },
];

const emojis = ["😍", "😂", "🔥", "👍"];

function MemoryDoneView() {
  const [page, setPage] = useState(0);
  const router = useRouter();

  return (
    <div className="flex flex-col min-h-screen px-4">
      <div className="flex items-center gap-3 pt-12 pb-2">
        <button onClick={() => router.back()} className="text-blue-500">
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 className="font-semibold text-base flex-1 text-center">여행 추억</h1>
        <div className="w-6" />
      </div>

      <h2 className="text-3xl font-bold text-center mt-4">여행 추억</h2>

      {/* Cards */}
      <div className="flex-1 flex flex-col items-center justify-center gap-4 py-4">
        <div
          className="w-full h-96 rounded-3xl flex flex-col items-center justify-center gap-3 text-white"
          style={{
            background: "linear-gradient(135deg, rgba(96,165,250,0.8) 0%, rgba(167,139,250,0.6) 100%)",
          }}
        >
          <svg className="w-10 h-10 text-white/80" fill="currentColor" viewBox="0 0 24 24">
            <path d="M22 16V4c0-1.1-.9-2-2-2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2zm-11-4l2.03 2.71L16 11l4 5H8l3-4zM2 6v14c0 1.1.9 2 2 2h14v-2H4V6H2z" />
          </svg>
          <p className="text-2xl font-bold text-center px-4">{memoryPages[page].title}</p>
          <p className="text-white/80 text-center text-sm px-4">{memoryPages[page].meta}</p>
        </div>

        {/* Emoji reactions */}
        <div className="flex gap-4 justify-center">
          {emojis.map((e) => (
            <button
              key={e}
              className="w-14 h-14 rounded-full bg-gray-100 flex items-center justify-center text-2xl active:scale-90 transition-transform"
            >
              {e}
            </button>
          ))}
        </div>

        <p className="text-xs text-gray-400 text-center">
          여행 중 남긴 사진과 숏폼이 일정 순서대로 모입니다.
        </p>

        {/* Page dots */}
        <div className="flex gap-1.5 justify-center">
          {memoryPages.map((_, i) => (
            <button
              key={i}
              onClick={() => setPage(i)}
              className={`rounded-full transition-all duration-300 ${
                i === page ? "w-4 h-2 bg-blue-500" : "w-2 h-2 bg-gray-300"
              }`}
            />
          ))}
        </div>

        {/* Prev / Next */}
        <div className="flex gap-3 w-full">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="flex-1 py-3 rounded-2xl bg-gray-100 text-gray-700 font-semibold disabled:opacity-40 text-sm"
          >
            이전
          </button>
          {page < memoryPages.length - 1 ? (
            <button
              onClick={() => setPage((p) => p + 1)}
              className="flex-1 py-3 rounded-2xl bg-blue-500 text-white font-semibold text-sm"
            >
              다음
            </button>
          ) : (
            <Link
              href="/home"
              className="flex-1 py-3 rounded-2xl bg-blue-500 text-white font-semibold text-sm text-center"
            >
              홈으로
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}
