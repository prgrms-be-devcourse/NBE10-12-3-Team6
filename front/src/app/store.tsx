"use client";

import { createContext, useContext, useState, useEffect, ReactNode } from "react";

// ── Types ─────────────────────────────────────────────────────────────────────

export type PlanTheme = "meal" | "cafe" | "activity" | "etc";

export interface User {
  id: number;
  name: string;
  color: string;
  isAdmin?: boolean;
}

export interface ActivityBlock {
  id: string;
  order: number;
  theme: PlanTheme;
  startMinute: number;
  endMinute: number;
  voteId?: string | null;
  confirmedPlaceName?: string | null;
  category?: string | null;
}

export interface PlanCandidate {
  id: string;
  authorId: number;
  authorName: string;
  placeName: string;
  address: string;
  category?: string;
}

export interface PhotoRecord {
  id: string;
  blockId: string;
  title: string;
  status: "uploaded" | "skipped";
}

export interface TripDay {
  id: string;
  dayNumber: number;
  date: string;
  isPlanSkipped: boolean;
  isPlanCompleted: boolean;
  blocks: ActivityBlock[];
  selectedCandidateByBlock: Record<string, string>;
  votedUserIDsByBlockAndCandidate: Record<string, Record<string, number[]>>;
  records: PhotoRecord[];
}

export interface Trip {
  id: string;
  name: string;
  region: string;
  startDate: string;
  nights: number;
  members: User[];
  days: TripDay[];
  candidates: PlanCandidate[];
  inviteCode: string;
  inviteJoinIndex: number;
}

interface CreateTripData {
  name: string;
  region: string;
  startDate: string;
  nights: number;
}

interface ApiTripItem {
  id: number;
  name: string;
  region: string;
  nights: number;
  startDate: string;
  joinUrl?: string;
}

interface StoreCtx {
  isLoggedIn: boolean;
  currentUser: User;
  trips: Trip[];
  login: (name?: string, id?: number) => void;
  signup: (nickname: string) => void;
  createTrip: (data: CreateTripData) => string;
  updateTrip: (trip: Trip) => void;
  upsertTrip: (trip: Trip) => void;
  loadTrips: (items: ApiTripItem[]) => void;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

export function uid() {
  return Math.random().toString(36).slice(2, 10);
}

function makeInviteCode(): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
}

function makeDay(dayNumber: number, startDate: string, offsetDays: number): TripDay {
  const d = new Date(startDate + "T00:00:00");
  d.setDate(d.getDate() + offsetDays);
  const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  return {
    id: uid(),
    dayNumber,
    date: dateStr,
    isPlanSkipped: false,
    isPlanCompleted: false,
    blocks: [{ id: uid(), order: 1, theme: "meal", startMinute: 9 * 60, endMinute: 10 * 60 }],
    selectedCandidateByBlock: {},
    votedUserIDsByBlockAndCandidate: {},
    records: [],
  };
}

// ── Mock users for invite simulation ─────────────────────────────────────────

export const MOCK_USERS: User[] = [
  { id: 1, name: "민준", color: "orange" },
  { id: 2, name: "서연", color: "green" },
  { id: 3, name: "도윤", color: "purple" },
  { id: 4, name: "하린", color: "pink" },
  { id: 5, name: "유찬", color: "teal" },
  { id: 6, name: "지민", color: "indigo" },
  { id: 7, name: "태오", color: "teal" },
  { id: 8, name: "나은", color: "cyan" },
  { id: 9, name: "이준", color: "brown" },
  { id: 10, name: "소율", color: "red" },
];

// ── Context ───────────────────────────────────────────────────────────────────

const Ctx = createContext<StoreCtx | null>(null);

export function TripLogProvider({ children }: { children: ReactNode }) {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [currentUser, setCurrentUser] = useState<User>({ id: 0, name: "", color: "blue" });
  const [trips, setTrips] = useState<Trip[]>([]);

  useEffect(() => {
    setIsLoggedIn(!!localStorage.getItem("accessToken"));
    setCurrentUser({
      id: Number(localStorage.getItem("userId") ?? 0),
      name: localStorage.getItem("userName") ?? "",
      color: "blue",
    });
  }, []);

  const login = (name?: string, id?: number) => {
    const newName = name ?? "";
    const newId = id ?? 0;
    if (typeof window !== "undefined") {
      localStorage.setItem("userName", newName);
      localStorage.setItem("userId", String(newId));
    }
    setCurrentUser(u => ({ ...u, id: newId, name: newName }));
    setIsLoggedIn(true);
  };

  const signup = (nickname: string) => {
    if (nickname.trim()) setCurrentUser(u => ({ ...u, name: nickname.trim() }));
    setIsLoggedIn(true);
  };

  const createTrip = (data: CreateTripData): string => {
    const days = Array.from({ length: data.nights + 1 }, (_, i) =>
      makeDay(i + 1, data.startDate, i)
    );
    const id = uid();
    setTrips(t => [
      ...t,
      {
        id,
        name: data.name,
        region: data.region,
        startDate: data.startDate,
        nights: data.nights,
        members: [{ ...currentUser, isAdmin: true }],
        days,
        candidates: [],
        inviteCode: makeInviteCode(),
        inviteJoinIndex: 0,
      },
    ]);
    return id;
  };

  const updateTrip = (trip: Trip) => {
    setTrips(t => t.map(x => (x.id === trip.id ? trip : x)));
  };

  const upsertTrip = (trip: Trip) => {
    setTrips(t => t.some(x => x.id === trip.id) ? t.map(x => x.id === trip.id ? trip : x) : [...t, trip]);
  };

  const loadTrips = (items: ApiTripItem[]) => {
    setTrips(items.map(item => ({
      id: String(item.id),
      name: item.name,
      region: item.region,
      startDate: item.startDate,
      nights: item.nights,
      members: [],
      days: Array.from({ length: item.nights + 1 }, (_, i) => makeDay(i + 1, item.startDate, i)),
      candidates: [],
      inviteCode: item.joinUrl ?? "",
      inviteJoinIndex: 0,
    })));
  };

  return (
    <Ctx.Provider value={{ isLoggedIn, currentUser, trips, login, signup, createTrip, updateTrip, upsertTrip, loadTrips }}>
      {children}
    </Ctx.Provider>
  );
}

export function useStore() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useStore outside TripLogProvider");
  return ctx;
}
