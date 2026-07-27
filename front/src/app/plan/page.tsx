"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import AnimatedBottomSheet from "../components/AnimatedBottomSheet";

// ── Shared small components ───────────────────────────────────────────────────

function StepHeader({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div className="flex flex-col gap-1">
      <h2 className="text-xl font-bold">{title}</h2>
      <p className="text-sm text-gray-500">{subtitle}</p>
    </div>
  );
}

function InfoBox({ text }: { text: string }) {
  return (
    <div className="p-3 bg-yellow-50 rounded-2xl">
      <p className="text-sm text-gray-600">{text}</p>
    </div>
  );
}

function MockInput({ title, value }: { title: string; value: string }) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-xs font-bold text-gray-500 uppercase">{title}</span>
      <div className="p-3 bg-gray-100 rounded-xl text-sm text-gray-800">{value}</div>
    </div>
  );
}

function TimeControl({
  title,
  hour,
  minute,
  onIncrease,
  onDecrease,
}: {
  title: string;
  hour: number;
  minute: number;
  onIncrease: () => void;
  onDecrease: () => void;
}) {
  const formatted = `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
  return (
    <div className="flex items-center justify-between p-3 bg-gray-100 rounded-xl">
      <div className="flex flex-col gap-0.5">
        <span className="text-xs font-bold text-gray-500 uppercase">{title}</span>
        <span className="text-xl font-bold">{formatted}</span>
      </div>
      <div className="flex items-center gap-3">
        <button onClick={onDecrease} className="text-blue-500 active:opacity-60">
          <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11H7v-2h10v2z" />
          </svg>
        </button>
        <button onClick={onIncrease} className="text-blue-500 active:opacity-60">
          <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11h-4v4h-2v-4H7v-2h4V7h2v4h4v2z" />
          </svg>
        </button>
      </div>
    </div>
  );
}

function ThemeTag({
  color,
  children,
}: {
  color: string;
  children: React.ReactNode;
}) {
  return (
    <span className={`text-xs font-bold px-2.5 py-1.5 rounded-full ${color}`}>
      {children}
    </span>
  );
}

// ── Step 0: Duration ──────────────────────────────────────────────────────────

function StepDuration({ nights, onDecrement, onIncrement }: {
  nights: number;
  onDecrement: () => void;
  onIncrement: () => void;
}) {
  const days = nights + 1;
  return (
    <div className="flex flex-col gap-4">
      <StepHeader
        title="1단계. 몇 박 며칠인가요?"
        subtitle="직접 입력하지 않고 +, -로 쉽게 조정"
      />
      <div className="flex items-center justify-between p-4 bg-blue-50 rounded-2xl">
        <button onClick={onDecrement} className="text-blue-500 active:opacity-60">
          <svg className="w-10 h-10" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11H7v-2h10v2z" />
          </svg>
        </button>
        <div className="text-center">
          <p className="text-4xl font-bold">{nights}박 {days}일</p>
          <p className="text-sm text-gray-500 mt-1">1일차부터 {days}일차까지 자동 생성됩니다.</p>
        </div>
        <button onClick={onIncrement} className="text-blue-500 active:opacity-60">
          <svg className="w-10 h-10" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11h-4v4h-2v-4H7v-2h4V7h2v4h4v2z" />
          </svg>
        </button>
      </div>
      {Array.from({ length: days }, (_, i) => (
        <div key={i} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
          <span className="font-semibold text-sm">{i + 1}일차</span>
          <span className="text-sm text-gray-500">{i + 1 === days ? "마지막 날" : "숙박 포함"}</span>
        </div>
      ))}
    </div>
  );
}

// ── Step 1: First Region ──────────────────────────────────────────────────────

function StepFirstRegion({ hour, minute, onIncrease, onDecrease }: {
  hour: number; minute: number; onIncrease: () => void; onDecrease: () => void;
}) {
  return (
    <div className="flex flex-col gap-4">
      <StepHeader
        title="2단계. 첫 지역을 정해주세요"
        subtitle="여행의 시작 지역과 예상 도착 시간을 정합니다."
      />
      <MockInput title="여행 지역" value="부산" />
      <TimeControl title="예상 도착 시간" hour={hour} minute={minute} onIncrease={onIncrease} onDecrease={onDecrease} />
      <InfoBox text="부산 도착 시간이 정해지면, 숙소 체크인 전까지 남는 시간을 기준으로 활동을 추천/입력할 수 있습니다." />
    </div>
  );
}

// ── Step 2: Region Move ───────────────────────────────────────────────────────

function StepRegionMove() {
  return (
    <div className="flex flex-col gap-4">
      <StepHeader
        title="3단계. 지역을 이동하나요?"
        subtitle="2박 3일이라면 2일차, 3일차에 지역 이동 가능성을 확인합니다."
      />
      {[
        { day: "2일차", region: "경주", isMove: true, note: "지역 이동 있음" },
        { day: "3일차", region: "부산", isMove: false, note: "이동 없이 부산에서 더 놀다가 귀가" },
      ].map((row) => (
        <div key={row.day} className="flex items-start gap-3 p-3 bg-gray-50 rounded-xl">
          <div className={`w-5 h-5 mt-0.5 shrink-0 rounded flex items-center justify-center ${row.isMove ? "bg-blue-500" : "border-2 border-gray-300"}`}>
            {row.isMove && (
              <svg className="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
              </svg>
            )}
          </div>
          <div>
            <p className="font-semibold text-sm">{row.day}</p>
            <p className="text-sm text-gray-500">{row.region} · {row.note}</p>
          </div>
        </div>
      ))}
      <InfoBox text="지역 이동을 체크한 날짜는 그날 계획을 세울 때 다시 지역, 도착 시간, 숙소 여부를 물어봅니다." />
    </div>
  );
}

// ── Step 3: Accommodation ─────────────────────────────────────────────────────

function AccomCard({ day, hotel, checkIn, checkOut, arrival, vote }: {
  day: string; hotel: string; checkIn: string; checkOut: string; arrival: string; vote: string;
}) {
  return (
    <div className="p-4 bg-gray-50 rounded-2xl flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <span className="font-semibold text-sm">{day}</span>
        <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-blue-100 text-blue-600">{vote}</span>
      </div>
      <p className="text-lg font-bold">{hotel}</p>
      <p className="text-sm text-gray-500">도착 예상 {arrival} · 체크인 {checkIn} · 체크아웃 {checkOut}</p>
    </div>
  );
}

function StepAccommodation() {
  return (
    <div className="flex flex-col gap-4">
      <StepHeader
        title="4단계. 숙소를 정해주세요"
        subtitle="마지막 날을 제외하고 숙소 정보를 정합니다. 숙소는 랜덤보다 투표가 자연스러운 것 같습니다."
      />
      <AccomCard day="1일차 숙소" hotel="해운대 오션뷰 호텔" checkIn="15:00" checkOut="다음날 11:00" arrival="15:20" vote="3명 중 2명 선택" />
      <AccomCard day="2일차 숙소" hotel="부산역 근처 호텔" checkIn="16:00" checkOut="마지막 날 10:30" arrival="21:30" vote="투표 완료" />
      <InfoBox text="2일차에 지역을 이동하거나 숙소가 바뀌면, 해당 날짜에 새 숙소 정보를 다시 입력하도록 유도합니다." />
    </div>
  );
}

// ── Step 4: Departure ─────────────────────────────────────────────────────────

function StepDeparture() {
  return (
    <div className="flex flex-col gap-4">
      <StepHeader
        title="5단계. 언제 만나서 출발하나요?"
        subtitle="출발 시간은 여행 시작 알림 기준이 됩니다."
      />
      <MockInput title="만나는 장소" value="서울역 2층 대합실" />
      <div className="flex items-center justify-between p-3 bg-gray-100 rounded-xl">
        <div>
          <p className="text-xs font-bold text-gray-500 uppercase">만나는 시간</p>
          <p className="text-xl font-bold mt-0.5">09:00</p>
        </div>
        <div className="flex gap-2 text-blue-500">
          <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11H7v-2h10v2z" /></svg>
          <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11h-4v4h-2v-4H7v-2h4V7h2v4h4v2z" /></svg>
        </div>
      </div>
      <div className="flex items-center justify-between p-3 bg-gray-100 rounded-xl">
        <div>
          <p className="text-xs font-bold text-gray-500 uppercase">출발 시간</p>
          <p className="text-xl font-bold mt-0.5">09:30</p>
        </div>
        <div className="flex gap-2 text-blue-500">
          <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11H7v-2h10v2z" /></svg>
          <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11h-4v4h-2v-4H7v-2h4V7h2v4h4v2z" /></svg>
        </div>
      </div>
      <InfoBox text="09:00에 '곧 여행이 시작됩니다. 첫 사진을 남겨볼까요?' 알림이 올 예정입니다." />
    </div>
  );
}

// ── Timeline Plan Block ───────────────────────────────────────────────────────

function TimelineBlock({
  title,
  time,
  items,
}: {
  title: string;
  time: string;
  items: { type: string; name: string; detail: string }[];
}) {
  return (
    <div className="p-4 bg-gray-50 rounded-2xl flex flex-col gap-3">
      <div>
        <p className="font-semibold">{title}</p>
        <p className="text-sm text-gray-500">{time}</p>
      </div>
      {items.map((item, i) => (
        <div key={i} className="flex items-start gap-3 p-3 bg-white rounded-xl">
          <ThemeTag color="bg-green-100 text-green-600">{item.type}</ThemeTag>
          <div>
            <p className="text-sm font-semibold">{item.name}</p>
            <p className="text-xs text-gray-500">{item.detail}</p>
          </div>
        </div>
      ))}
    </div>
  );
}

function ThemePicker({ onVoteClick }: { onVoteClick: () => void }) {
  return (
    <div className="flex flex-col gap-3">
      <p className="font-semibold">테마 선택</p>
      <div className="grid grid-cols-3 gap-2">
        {[
          { label: "식사", icon: "🍴" },
          { label: "카페", icon: "☕" },
          { label: "활동", icon: "🚶" },
        ].map((t) => (
          <button key={t.label} className="flex flex-col items-center gap-2 p-3 bg-blue-50 text-blue-600 rounded-xl active:opacity-70">
            <span className="text-xl">{t.icon}</span>
            <span className="text-xs font-bold">{t.label}</span>
          </button>
        ))}
      </div>
      <button
        onClick={onVoteClick}
        className="w-full py-3 rounded-xl bg-blue-500 text-white font-semibold text-sm flex items-center justify-center gap-2"
      >
        <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11h-4v4h-2v-4H7v-2h4V7h2v4h4v2z" />
        </svg>
        테마 후보 등록 / 투표 화면 보기
      </button>
      <p className="text-xs text-gray-400">사용자가 다음을 누르기 전까지 테마를 계속 추가할 수 있습니다.</p>
    </div>
  );
}

// ── Step 5: Day 1 schedule ────────────────────────────────────────────────────

function StepDay1({ onVote }: { onVote: () => void }) {
  return (
    <div className="flex flex-col gap-4">
      <StepHeader
        title="6단계. 1일차에 뭘 할까요?"
        subtitle="도착 시간과 체크인 시간 사이, 체크인 이후 일정을 테마별로 추가합니다."
      />
      <TimelineBlock
        title="체크인 전 남는 시간"
        time="13:00 ~ 15:00"
        items={[
          { type: "식사", name: "돼지국밥", detail: "한식 · 점심" },
          { type: "카페", name: "오션뷰 카페", detail: "일반 카페 · 휴식" },
        ]}
      />
      <TimelineBlock
        title="체크인 후"
        time="16:00 이후"
        items={[
          { type: "활동", name: "광안리 산책", detail: "야경 보기" },
          { type: "식사", name: "초밥집", detail: "일식 · 저녁" },
        ]}
      />
      <ThemePicker onVoteClick={onVote} />
    </div>
  );
}

// ── Step 6: Day 2 schedule ────────────────────────────────────────────────────

function StepDay2({ onVote }: { onVote: () => void }) {
  return (
    <div className="flex flex-col gap-4">
      <StepHeader
        title="7단계. 2일차에는 뭐할까요?"
        subtitle="지역 이동 여부에 따라 2일차 일정 흐름을 따로 정합니다."
      />
      <TimelineBlock
        title="2일차 · 지역 이동"
        time="11:00 ~ 13:00"
        items={[{ type: "이동", name: "부산 숙소 출발", detail: "11:00 출발 · 13:00 경주 도착" }]}
      />
      <TimelineBlock
        title="2일차 · 경주 도착 후"
        time="13:00 ~ 19:30"
        items={[
          { type: "식사", name: "경주 한식집", detail: "13:10 · 점심" },
          { type: "활동", name: "황리단길 구경", detail: "14:30 · 사진 명소" },
          { type: "카페", name: "한옥 카페", detail: "16:00 · 디저트" },
          { type: "복귀", name: "부산역 근처 호텔로 이동", detail: "20:00 출발 · 21:30 도착" },
        ]}
      />
      <ThemePicker onVoteClick={onVote} />
      <InfoBox text="2일차는 지역 이동이 있기 때문에 이동 전, 도착 후, 복귀 전 일정을 나누어 계획할 수 있습니다." />
    </div>
  );
}

// ── Step 7: Day 3 schedule ────────────────────────────────────────────────────

function StepDay3({ onVote }: { onVote: () => void }) {
  return (
    <div className="flex flex-col gap-4">
      <StepHeader
        title="8단계. 3일차에는 뭐할까요?"
        subtitle="마지막 날은 체크아웃 이후 바로 귀가할지, 추가 활동 후 귀가할지 정합니다."
      />
      <TimelineBlock
        title="3일차 · 체크아웃 이후"
        time="10:30 ~ 17:00"
        items={[
          { type: "식사", name: "밀면", detail: "12:00 · 점심" },
          { type: "활동", name: "해운대 산책", detail: "14:00 · 마지막 일정" },
          { type: "카페", name: "부산역 근처 카페", detail: "15:30 · 기차 전 휴식" },
          { type: "귀가", name: "부산역 출발", detail: "17:00" },
        ]}
      />
      <ThemePicker onVoteClick={onVote} />
      <InfoBox text="마지막 날 바로 집에 간다면 체크아웃 시간이 귀가 출발 시간으로 자동 설정됩니다." />
    </div>
  );
}

// ── Step 8: Summary ───────────────────────────────────────────────────────────

function SummaryDayCard({
  day,
  region,
  time,
  items,
}: {
  day: string;
  region: string;
  time: string;
  items: { type: string; name: string; detail: string }[];
}) {
  return (
    <div className="p-4 bg-gray-50 rounded-2xl flex flex-col gap-3">
      <div className="flex items-start justify-between">
        <div>
          <p className="font-bold text-base">{day}</p>
          <p className="text-sm text-blue-500">{region}</p>
        </div>
        <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-gray-200 text-gray-600">
          {items.length}개 일정
        </span>
      </div>
      <p className="text-xs text-gray-400">{time}</p>
      {items.map((item, i) => (
        <div key={i} className="flex items-start gap-3 p-3 bg-white rounded-xl">
          <span className="text-xs font-bold px-2 py-1.5 rounded-full bg-green-100 text-green-600 shrink-0 w-10 text-center">
            {item.type}
          </span>
          <div>
            <p className="text-sm font-semibold">{item.name}</p>
            <p className="text-xs text-gray-500">{item.detail}</p>
          </div>
        </div>
      ))}
    </div>
  );
}

function StepSummary() {
  return (
    <div className="flex flex-col gap-4">
      <StepHeader
        title="세운 계획을 한눈에 확인해요"
        subtitle="여행 기간, 멤버, 숙소, 날짜별 일정이 하나의 계획으로 정리됩니다."
      />

      {/* Header card */}
      <div className="p-4 bg-blue-50 rounded-2xl flex flex-col gap-3">
        <div className="flex items-start justify-between">
          <div>
            <p className="font-bold text-base">2박 3일 여행</p>
            <p className="text-sm text-gray-500 mt-0.5">여행친구1, 여행친구2, 여행친구3</p>
          </div>
          <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-green-100 text-green-600">계획 완료</span>
        </div>
        <div className="grid grid-cols-3 gap-2">
          {[
            { title: "기간", value: "2박 3일" },
            { title: "지역", value: "부산/경주" },
            { title: "숙소", value: "2곳" },
          ].map((b) => (
            <div key={b.title} className="bg-white/80 rounded-xl py-2.5 text-center">
              <p className="text-xs text-gray-500">{b.title}</p>
              <p className="text-xs font-bold mt-0.5">{b.value}</p>
            </div>
          ))}
        </div>
      </div>

      <SummaryDayCard
        day="1일차" region="부산"
        time="13:00 부산 도착 → 15:20 숙소 도착"
        items={[
          { type: "식사", name: "돼지국밥", detail: "13:30 · 한식 · 투표 반영" },
          { type: "카페", name: "오션뷰 카페", detail: "14:30 · 체크인 전 휴식" },
          { type: "숙소", name: "해운대 오션뷰 호텔", detail: "15:20 도착 · 15:00 체크인 가능" },
          { type: "활동", name: "광안리 산책", detail: "19:00 · 야경 보기" },
          { type: "식사", name: "초밥집", detail: "20:00 · 저녁" },
        ]}
      />

      <SummaryDayCard
        day="2일차" region="경주 이동"
        time="11:00 부산 출발 → 13:00 경주 도착 → 21:30 숙소 도착"
        items={[
          { type: "이동", name: "부산 숙소 출발", detail: "11:00" },
          { type: "식사", name: "경주 한식집", detail: "13:10 · 점심" },
          { type: "활동", name: "황리단길 구경", detail: "14:30 · 사진 명소" },
          { type: "카페", name: "한옥 카페", detail: "16:00 · 디저트" },
          { type: "복귀", name: "부산역 근처 호텔로 이동", detail: "20:00 출발 · 21:30 도착" },
        ]}
      />

      <SummaryDayCard
        day="3일차" region="부산"
        time="10:30 체크아웃 → 17:00 부산역 출발"
        items={[
          { type: "숙소", name: "체크아웃", detail: "10:30" },
          { type: "식사", name: "밀면", detail: "12:00 · 점심" },
          { type: "활동", name: "해운대 산책", detail: "14:00 · 마지막 일정" },
          { type: "카페", name: "부산역 근처 카페", detail: "15:30 · 귀가 전 휴식" },
          { type: "귀가", name: "부산역 출발", detail: "17:00" },
        ]}
      />

      <InfoBox text="이 계획은 여행 시작 모드에서 일정 순서대로 카드처럼 표시되고, 각 일정마다 사진이나 숏폼을 남길 수 있습니다." />

      <Link
        href="/capture"
        className="w-full py-4 rounded-2xl bg-green-500 text-white font-semibold text-center text-base block"
      >
        여행 시작 화면 미리보기
      </Link>
    </div>
  );
}

// ── Vote Sheet ────────────────────────────────────────────────────────────────

type VoteTheme = "meal" | "cafe" | "activity";

const themeData = {
  meal: {
    label: "식사",
    candidates: [
      { name: "돼지국밥", detail: "부산 대표 음식 · 점심", votes: 2 },
      { name: "밀면", detail: "가볍게 먹기 좋음", votes: 1 },
      { name: "초밥", detail: "바다 근처 식당", votes: 0 },
    ],
  },
  cafe: {
    label: "카페",
    candidates: [
      { name: "오션뷰 카페", detail: "바다 보이는 카페", votes: 2 },
      { name: "보드게임 카페", detail: "비 올 때 좋음", votes: 1 },
      { name: "디저트 카페", detail: "케이크 맛집", votes: 0 },
    ],
  },
  activity: {
    label: "활동",
    candidates: [
      { name: "광안리 산책", detail: "야경 보기 좋음", votes: 2 },
      { name: "요트 체험", detail: "예약 필요", votes: 1 },
      { name: "전망대 구경", detail: "사진 찍기 좋음", votes: 0 },
    ],
  },
};

function VoteSheet({
  dayTitle,
  onClose,
}: {
  dayTitle: string;
  onClose: () => void;
}) {
  const [theme, setTheme] = useState<VoteTheme>("meal");
  const [selected, setSelected] = useState("돼지국밥");
  const [confirmed, setConfirmed] = useState(false);

  const data = themeData[theme];

  return (
    <AnimatedBottomSheet onClose={onClose} className="max-h-[90vh] flex flex-col">
      {(close) => (
        <>
        <div className="flex items-center justify-between px-4 pt-5 pb-3 border-b border-gray-100 shrink-0">
          <h2 className="text-base font-bold">일정 후보 투표</h2>
          <button onClick={close} className="text-blue-500 font-medium">닫기</button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-5">
          <div>
            <p className="text-xl font-bold">{dayTitle}에는 뭐할까요?</p>
            <p className="text-sm text-gray-500 mt-1">식사, 카페, 활동 후보를 등록하고 여행친구들과 투표로 일정을 확정하는 화면입니다.</p>
          </div>

          {/* Theme tabs */}
          <div>
            <p className="font-semibold text-sm mb-2">테마 선택</p>
            <div className="grid grid-cols-3 gap-2">
              {(["meal", "cafe", "activity"] as VoteTheme[]).map((t) => (
                <button
                  key={t}
                  onClick={() => { setTheme(t); setSelected(themeData[t].candidates[0].name); setConfirmed(false); }}
                  className={`flex flex-col items-center gap-1.5 p-3 rounded-xl border-2 transition-colors ${
                    theme === t
                      ? "bg-blue-50 border-blue-400 text-blue-600"
                      : "bg-gray-50 border-transparent text-gray-600"
                  }`}
                >
                  <span className="text-xl">{t === "meal" ? "🍴" : t === "cafe" ? "☕" : "🚶"}</span>
                  <span className="text-xs font-bold">{themeData[t].label}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Candidate register preview */}
          <div className="p-4 bg-gray-50 rounded-2xl flex flex-col gap-3">
            <p className="font-semibold text-sm">{data.label} 후보 등록</p>
            <p className="text-xs text-gray-500">여행친구들이 가고 싶은 후보를 등록할 수 있습니다.</p>
            <div className="p-3 bg-white rounded-xl">
              <p className="text-xs font-bold text-gray-500 uppercase mb-1">{data.label} 후보 이름</p>
              <p className="text-sm text-gray-700">{data.candidates[0].name}</p>
            </div>
            <div className="p-3 bg-white rounded-xl">
              <p className="text-xs font-bold text-gray-500 uppercase mb-1">세부 설명</p>
              <p className="text-sm text-gray-700">{data.candidates[0].detail}</p>
            </div>
            <button className="w-full py-3 rounded-xl bg-gray-200 text-gray-700 text-sm font-semibold flex items-center justify-center gap-2">
              <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11h-4v4h-2v-4H7v-2h4V7h2v4h4v2z" /></svg>
              후보 추가하기
            </button>
          </div>

          {/* Voting */}
          <div className="p-4 bg-gray-50 rounded-2xl flex flex-col gap-3">
            <div className="flex items-start justify-between">
              <div>
                <p className="font-semibold text-sm">{data.label} 후보 투표</p>
                <p className="text-xs text-gray-500 mt-0.5">여행친구1, 여행친구2, 여행친구3이 함께 선택 중</p>
              </div>
              <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-orange-100 text-orange-600">진행 중</span>
            </div>

            {data.candidates.map((c) => (
              <button
                key={c.name}
                onClick={() => { setSelected(c.name); setConfirmed(false); }}
                className={`flex items-start gap-3 p-3 bg-white rounded-xl border-2 transition-colors text-left w-full ${
                  selected === c.name ? "border-blue-400" : "border-transparent"
                }`}
              >
                <div className={`w-5 h-5 mt-0.5 rounded-full border-2 flex items-center justify-center shrink-0 ${
                  selected === c.name ? "border-blue-500 bg-blue-500" : "border-gray-300"
                }`}>
                  {selected === c.name && <div className="w-2 h-2 rounded-full bg-white" />}
                </div>
                <div className="flex-1">
                  <p className="text-sm font-semibold">{c.name}</p>
                  <p className="text-xs text-gray-500">{c.detail}</p>
                </div>
                <span className="text-xs font-bold px-2 py-1 rounded-full bg-blue-100 text-blue-600">{c.votes}표</span>
              </button>
            ))}

            <button
              onClick={() => setConfirmed(true)}
              className="w-full py-3 rounded-xl bg-blue-500 text-white font-semibold text-sm"
            >
              투표 결과로 일정 확정
            </button>
            <p className="text-xs text-gray-400">한 멤버는 하나의 후보에만 투표할 수 있다는 전제로 만든 UI입니다.</p>
          </div>

          {/* Confirmed result */}
          {confirmed && (
            <div className="p-4 bg-green-50 rounded-2xl flex flex-col gap-3">
              <div className="flex items-center gap-2">
                <svg className="w-5 h-5 text-green-500" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-2 16l-4-4 1.41-1.41L10 14.17l6.59-6.59L18 9l-8 8z" />
                </svg>
                <p className="font-semibold text-sm">일정 확정 완료</p>
              </div>
              <div className="p-3 bg-green-100 rounded-xl">
                <p className="font-bold">{dayTitle} {data.label} - {selected}</p>
                <p className="text-sm text-gray-600 mt-0.5">투표 결과가 {dayTitle} 일정에 반영됩니다.</p>
              </div>
              <p className="text-xs text-gray-500">여행 시작 모드에서는 이 일정이 카드로 표시되고, 해당 일정에 사진이나 숏폼을 남길 수 있습니다.</p>
            </div>
          )}
        </div>
        </>
      )}
    </AnimatedBottomSheet>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────

const MAX_STEP = 8;

export default function PlanPage() {
  const router = useRouter();
  const [step, setStep] = useState(0);
  const [nights, setNights] = useState(2);
  const [arrivalHour, setArrivalHour] = useState(13);
  const [arrivalMinute, setArrivalMinute] = useState(0);
  const [showVote, setShowVote] = useState(false);
  const [voteDayTitle, setVoteDayTitle] = useState("1일차");

  const openVote = (dayTitle: string) => {
    setVoteDayTitle(dayTitle);
    setShowVote(true);
  };

  const increaseTime = () => {
    let m = arrivalMinute + 30;
    let h = arrivalHour;
    if (m >= 60) { m = 0; h = (h + 1) % 24; }
    setArrivalHour(h);
    setArrivalMinute(m);
  };

  const decreaseTime = () => {
    let m = arrivalMinute - 30;
    let h = arrivalHour;
    if (m < 0) { m = 30; h = h - 1; if (h < 0) h = 23; }
    setArrivalHour(h);
    setArrivalMinute(m);
  };

  const stepContent = [
    <StepDuration
      nights={nights}
      onDecrement={() => setNights(Math.max(1, nights - 1))}
      onIncrement={() => setNights(nights + 1)}
    />,
    <StepFirstRegion
      hour={arrivalHour}
      minute={arrivalMinute}
      onIncrease={increaseTime}
      onDecrease={decreaseTime}
    />,
    <StepRegionMove />,
    <StepAccommodation />,
    <StepDeparture />,
    <StepDay1 onVote={() => openVote("1일차")} />,
    <StepDay2 onVote={() => openVote("2일차")} />,
    <StepDay3 onVote={() => openVote("3일차")} />,
    <StepSummary />,
  ];

  return (
    <div className="flex flex-col min-h-screen">
      {/* Nav bar */}
      <div className="flex items-center gap-3 px-4 pt-12 pb-2">
        <button onClick={() => router.back()} className="text-blue-500">
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 className="font-semibold text-base flex-1 text-center">2박 3일 여행</h1>
        <div className="w-6" />
      </div>

      {/* Progress */}
      <div className="px-4 py-2">
        <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
          <div
            className="h-full bg-blue-500 rounded-full transition-all duration-300"
            style={{ width: `${((step + 1) / (MAX_STEP + 1)) * 100}%` }}
          />
        </div>
        <p className="text-xs text-gray-400 text-right mt-1">{step + 1} / {MAX_STEP + 1}</p>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto px-4 py-2 pb-6">
        {stepContent[step]}
      </div>

      {/* Bottom buttons */}
      <div className="flex gap-3 px-4 py-4 border-t border-gray-100 shrink-0">
        <button
          onClick={() => setStep((s) => Math.max(0, s - 1))}
          disabled={step === 0}
          className="flex-1 py-4 rounded-2xl bg-gray-100 text-gray-700 font-semibold disabled:opacity-40"
        >
          이전
        </button>
        <button
          onClick={() => {
            if (step < MAX_STEP) setStep((s) => s + 1);
            else router.push("/home");
          }}
          className="flex-1 py-4 rounded-2xl bg-blue-500 text-white font-semibold"
        >
          {step === MAX_STEP ? "계획 완료" : "다음"}
        </button>
      </div>

      {showVote && (
        <VoteSheet dayTitle={voteDayTitle} onClose={() => setShowVote(false)} />
      )}
    </div>
  );
}
