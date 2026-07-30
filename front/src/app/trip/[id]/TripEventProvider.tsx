"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useParams } from "next/navigation";
import { apiFetch, API_BASE } from "../../lib";

export type TripEventPayload = {
  eventId: string;
  eventType: string;
  message: string;
  tripGroupId: number;
  actorMemberId?: number | null;
  dayNumber?: number | null;
  timelineId?: number | null;
  timelineIds?: number[];
  voteId?: number | null;
  tripPlaceId?: number | null;
  occurredAt?: string;
};

export type TripEventNotice = {
  noticeId: string;
  message: string;
  eventCount: number;
};

type TripEventNoticeTopic = "trip" | "place" | "timeline" | "vote";

type TripEventContextValue = {
  latestEvent: TripEventPayload | null;
  activeNotice: TripEventNotice | null;
};

const TripEventContext = createContext<TripEventContextValue>({
  latestEvent: null,
  activeNotice: null,
});

const TRIP_EVENT_NOTICE_DURATION_MS = 5000;
const TRIP_EVENT_NOTICE_GROUPING_MS = 4700;
const TRIP_EVENT_NOTICE_COOLDOWN_MS = 3000;
const TRIP_EVENT_RECONNECT_DELAY_MS = 2000;

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

const readTripEventPayload = (data: string): TripEventPayload | null => {
  if (!data) return null;

  try {
    const payload = JSON.parse(data) as TripEventPayload;
    if (!payload.eventId || !payload.eventType || !payload.message) return null;
    return payload;
  } catch {
    return null;
  }
};

const getEventChangeCount = (event: TripEventPayload) => {
  if (
    event.eventType === "TIMELINE_BATCH_CREATED" &&
    event.timelineIds &&
    event.timelineIds.length > 0
  ) {
    return event.timelineIds.length;
  }
  return 1;
};

const getSameTypeSummary = (
  eventType: string,
  count: number,
  dayNumber?: number,
) => {
  const dayPrefix = dayNumber == null ? "" : `${dayNumber}일차 `;

  switch (eventType) {
    case "TRIP_GROUP_UPDATED":
      return `여행방 정보 변경 ${count}건`;
    case "TRIP_MEMBER_JOINED":
      return `여행 멤버 추가 ${count}명`;
    case "WISH_PLACE_ADDED":
      return `후보 장소 추가 ${count}개`;
    case "TIMELINE_CREATED":
    case "TIMELINE_BATCH_CREATED":
      return `${dayPrefix}시간 구간 추가 ${count}개`;
    case "TIMELINE_TIME_UPDATED":
      return `${dayPrefix}시간 변경 ${count}건`;
    case "TIMELINE_DELETED":
      return `${dayPrefix}시간 구간 삭제 ${count}개`;
    case "VOTE_CREATED":
      return `${dayPrefix}투표 생성 ${count}개`;
    case "VOTE_PARTICIPATION_UPDATED":
      return `${dayPrefix}투표 변경 ${count}건`;
    case "TIMELINE_PLACE_CONFIRMED":
      return `${dayPrefix}장소 확정 ${count}건`;
    case "VOTE_EXPIRED":
      return `${dayPrefix}투표 종료 ${count}건`;
    default:
      return dayNumber == null
        ? `여행방 변경 ${count}건`
        : `${dayNumber}일차 변경 ${count}건`;
  }
};

const getSummaryEventType = (eventType: string) => {
  if (eventType === "TIMELINE_BATCH_CREATED") {
    return "TIMELINE_CREATED";
  }
  return eventType;
};

const getNoticeTopic = (eventType: string): TripEventNoticeTopic => {
  switch (eventType) {
    case "TRIP_GROUP_UPDATED":
    case "TRIP_MEMBER_JOINED":
      return "trip";
    case "WISH_PLACE_ADDED":
      return "place";
    case "TIMELINE_CREATED":
    case "TIMELINE_BATCH_CREATED":
    case "TIMELINE_TIME_UPDATED":
    case "TIMELINE_DELETED":
    case "TIMELINE_PLACE_CONFIRMED":
      return "timeline";
    case "VOTE_CREATED":
    case "VOTE_PARTICIPATION_UPDATED":
    case "VOTE_EXPIRED":
      return "vote";
    default:
      return "trip";
  }
};

const getSameTopicSummary = (
  topic: TripEventNoticeTopic,
  count: number,
  dayNumber?: number,
) => {
  const dayPrefix = dayNumber == null ? "" : `${dayNumber}일차 `;

  switch (topic) {
    case "place":
      return `후보 장소 변경 ${count}건`;
    case "timeline":
      return `${dayPrefix}시간 구간 변경 ${count}건`;
    case "vote":
      return `${dayPrefix}투표 변경 ${count}건`;
    case "trip":
    default:
      return `여행방 변경 ${count}건`;
  }
};

const createGroupedNotice = (
  events: TripEventPayload[],
  noticeId: string,
): TripEventNotice => {
  const firstEventChangeCount = getEventChangeCount(events[0]);
  if (events.length === 1 && firstEventChangeCount === 1) {
    return {
      noticeId,
      message: events[0].message,
      eventCount: firstEventChangeCount,
    };
  }

  const eventCount = events.reduce(
    (count, event) => count + getEventChangeCount(event),
    0,
  );
  const summaryEventTypes = new Set(
    events.map(event => getSummaryEventType(event.eventType)),
  );
  const noticeTopics = new Set(
    events.map(event => getNoticeTopic(event.eventType)),
  );
  const dayNumbers = events
    .map(event => event.dayNumber)
    .filter((dayNumber): dayNumber is number => dayNumber != null);
  const uniqueDayNumbers = new Set(dayNumbers);
  const sameDayNumber =
    dayNumbers.length === events.length && uniqueDayNumbers.size === 1
      ? dayNumbers[0]
      : undefined;

  if (summaryEventTypes.size === 1) {
    return {
      noticeId,
      message: getSameTypeSummary(
        getSummaryEventType(events[0].eventType),
        eventCount,
        sameDayNumber,
      ),
      eventCount,
    };
  }

  if (noticeTopics.size === 1) {
    return {
      noticeId,
      message: getSameTopicSummary(
        getNoticeTopic(events[0].eventType),
        eventCount,
        sameDayNumber,
      ),
      eventCount,
    };
  }

  return {
    noticeId,
    message: sameDayNumber == null
      ? `여행방 변경 ${eventCount}건`
      : `${sameDayNumber}일차 변경 ${eventCount}건`,
    eventCount,
  };
};

export function useTripEvent() {
  return useContext(TripEventContext);
}

export function TripEventProvider({ children }: { children: ReactNode }) {
  const { id } = useParams<{ id: string }>();
  const [latestEvent, setLatestEvent] = useState<TripEventPayload | null>(null);
  const [activeNotice, setActiveNotice] = useState<TripEventNotice | null>(null);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const noticeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const noticeGroupingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const noticeCooldownTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const latestEventTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isNoticeAcceptingEvents = useRef(false);
  const activeNoticeEvents = useRef<TripEventPayload[]>([]);
  const queuedNoticeEvents = useRef<TripEventPayload[]>([]);
  const startNoticeRef = useRef<(events: TripEventPayload[]) => void>(() => {});

  const startNotice = useCallback((events: TripEventPayload[]) => {
    if (events.length === 0) return;

    const noticeId = events[0].eventId;
    activeNoticeEvents.current = events;
    isNoticeAcceptingEvents.current = true;
    setActiveNotice(createGroupedNotice(events, noticeId));
    noticeGroupingTimer.current = setTimeout(() => {
      isNoticeAcceptingEvents.current = false;
      noticeGroupingTimer.current = null;
    }, TRIP_EVENT_NOTICE_GROUPING_MS);
    noticeTimer.current = setTimeout(() => {
      setActiveNotice(null);
      activeNoticeEvents.current = [];
      isNoticeAcceptingEvents.current = false;
      noticeTimer.current = null;

      noticeCooldownTimer.current = setTimeout(() => {
        noticeCooldownTimer.current = null;
        const queuedEvents = queuedNoticeEvents.current;
        queuedNoticeEvents.current = [];
        startNoticeRef.current(queuedEvents);
      }, TRIP_EVENT_NOTICE_COOLDOWN_MS);
    }, TRIP_EVENT_NOTICE_DURATION_MS);
  }, []);

  useEffect(() => {
    startNoticeRef.current = startNotice;
  }, [startNotice]);

  const openNotice = useCallback((event: TripEventPayload) => {
    setLatestEvent(event);
    if (latestEventTimer.current) clearTimeout(latestEventTimer.current);
    latestEventTimer.current = setTimeout(() => {
      setLatestEvent(null);
      latestEventTimer.current = null;
    }, TRIP_EVENT_NOTICE_DURATION_MS);

    if (noticeTimer.current && isNoticeAcceptingEvents.current) {
      const groupedEvents = [...activeNoticeEvents.current, event];
      activeNoticeEvents.current = groupedEvents;
      setActiveNotice(currentNotice => (
        currentNotice
          ? createGroupedNotice(groupedEvents, currentNotice.noticeId)
          : currentNotice
      ));
      return;
    }

    if (noticeTimer.current || noticeCooldownTimer.current) {
      queuedNoticeEvents.current = [...queuedNoticeEvents.current, event];
      return;
    }

    startNotice([event]);
  }, [startNotice]);

  useEffect(() => {
    if (!id) return;

    let closed = false;
    const controller = new AbortController();

    const handleEvent = (rawEvent: string) => {
      const { eventName, data } = parseSseEvent(rawEvent);
      if (eventName !== "TRIP_EVENT") return;

      const payload = readTripEventPayload(data);
      if (payload) openNotice(payload);
    };

    const connect = async () => {
      try {
        const response = await apiFetch(`${API_BASE}/api/v1/trips/${id}/events/subscribe`, {
          headers: { Accept: "text/event-stream" },
          signal: controller.signal,
        });

        if (!response.ok || !response.body) {
          throw new Error("여행방 변경 알림 연결에 실패했습니다.");
        }

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
          console.error("[여행방 SSE 연결 실패]", error);
        }
      }

      if (!closed) {
        reconnectTimer.current = setTimeout(connect, TRIP_EVENT_RECONNECT_DELAY_MS);
      }
    };

    connect();

    return () => {
      closed = true;
      controller.abort();
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current);
    };
  }, [id, openNotice]);

  useEffect(() => () => {
    if (noticeTimer.current) clearTimeout(noticeTimer.current);
    if (noticeGroupingTimer.current) clearTimeout(noticeGroupingTimer.current);
    if (noticeCooldownTimer.current) clearTimeout(noticeCooldownTimer.current);
    if (latestEventTimer.current) clearTimeout(latestEventTimer.current);
  }, []);

  const contextValue = useMemo(
    () => ({ latestEvent, activeNotice }),
    [latestEvent, activeNotice],
  );

  return (
    <TripEventContext.Provider value={contextValue}>
      {children}
    </TripEventContext.Provider>
  );
}
