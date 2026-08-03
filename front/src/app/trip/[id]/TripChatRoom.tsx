"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Client, type IMessage } from "@stomp/stompjs";
import { apiFetch, API_BASE, WS_BASE } from "../../lib";
import { useStore } from "../../store";

type ChatMessageType = "TALK" | "SYSTEM";

interface ChatMessage {
  id: number;
  tripGroupId: number;
  senderId: number | null;
  senderName: string | null;
  messageType: ChatMessageType;
  content: string;
  createdAt: string;
}

interface ChatMessagePage {
  messages: ChatMessage[];
  hasNext: boolean;
}

const SEND_TIMEOUT_MS = 8000;
const READ_DEBOUNCE_MS = 1000;
const SYSTEM_GROUP_GAP_MS = 5000;
const SYSTEM_GROUP_MIN_SIZE = 3;

type RenderItem =
  | { kind: "single"; message: ChatMessage }
  | { kind: "group"; key: string; items: ChatMessage[] };

function formatTime(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function mergeAscending(prev: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  const byId = new Map(prev.map(m => [m.id, m]));
  for (const m of incoming) byId.set(m.id, m);
  return Array.from(byId.values()).sort((a, b) => a.id - b.id);
}

// SYSTEM 메시지끼리의 간격만으로 클러스터링한다. TALK 메시지가 사이에 끼어 있어도 무시한다.
function buildRenderItems(messages: ChatMessage[]): RenderItem[] {
  const systemMessages = messages.filter(m => m.messageType === "SYSTEM");
  const clusterOf = new Map<number, ChatMessage[]>();
  let currentCluster: ChatMessage[] = [];
  systemMessages.forEach((m, i) => {
    const prev = systemMessages[i - 1];
    const gap = prev ? new Date(m.createdAt).getTime() - new Date(prev.createdAt).getTime() : Infinity;
    if (gap > SYSTEM_GROUP_GAP_MS) currentCluster = [];
    currentCluster.push(m);
    clusterOf.set(m.id, currentCluster);
  });

  const items: RenderItem[] = [];
  const emitted = new Set<ChatMessage[]>();
  for (const m of messages) {
    if (m.messageType !== "SYSTEM") {
      items.push({ kind: "single", message: m });
      continue;
    }
    const cluster = clusterOf.get(m.id)!;
    if (cluster.length < SYSTEM_GROUP_MIN_SIZE) {
      items.push({ kind: "single", message: m });
      continue;
    }
    // 그룹은 클러스터의 마지막(가장 최신) 메시지 위치에서 한 번만 렌더한다.
    if (cluster[cluster.length - 1].id !== m.id) continue;
    if (emitted.has(cluster)) continue;
    emitted.add(cluster);
    items.push({ kind: "group", key: `group-${cluster[0].id}-${cluster[cluster.length - 1].id}`, items: cluster });
  }
  return items;
}

export default function TripChatRoom({ tripGroupId }: { tripGroupId: number }) {
  const { currentUser } = useStore();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [hasMoreOlder, setHasMoreOlder] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [input, setInput] = useState("");
  const [pendingContent, setPendingContent] = useState<string | null>(null);
  const [sendFailed, setSendFailed] = useState(false);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

  const listRef = useRef<HTMLDivElement | null>(null);
  const clientRef = useRef<Client | null>(null);
  const lastReceivedIdRef = useRef<number | null>(null);
  const pendingRef = useRef<{ content: string } | null>(null);
  const failTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const readTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const markRead = (messageId: number) => {
    apiFetch(`${API_BASE}/api/v1/trips/${tripGroupId}/chat/read`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ lastReadMessageId: messageId }),
    }).catch(() => {});
  };

  const scheduleMarkRead = (messageId: number) => {
    if (readTimerRef.current) clearTimeout(readTimerRef.current);
    readTimerRef.current = setTimeout(() => markRead(messageId), READ_DEBOUNCE_MS);
  };

  const scrollToBottomIfNear = () => {
    requestAnimationFrame(() => {
      const el = listRef.current;
      if (el && el.scrollHeight - el.scrollTop - el.clientHeight < 120) {
        el.scrollTop = el.scrollHeight;
      }
    });
  };

  const appendMessage = (msg: ChatMessage) => {
    setMessages(prev => (prev.some(m => m.id === msg.id) ? prev : mergeAscending(prev, [msg])));
    if (lastReceivedIdRef.current == null || msg.id > lastReceivedIdRef.current) {
      lastReceivedIdRef.current = msg.id;
    }
    if (pendingRef.current && msg.senderId === currentUser.id && msg.content === pendingRef.current.content) {
      pendingRef.current = null;
      setPendingContent(null);
      setSendFailed(false);
      if (failTimerRef.current) clearTimeout(failTimerRef.current);
    }
    scheduleMarkRead(msg.id);
    scrollToBottomIfNear();
  };

  const recoverGap = async (cursor: number) => {
    try {
      const res = await apiFetch(
        `${API_BASE}/api/v1/trips/${tripGroupId}/chat/messages/after?cursor=${cursor}&size=100`,
      );
      const body = await res.json();
      const page: ChatMessagePage = body.data;
      page.messages.forEach(appendMessage);
    } catch {
      // 최선 노력 복구이므로 실패해도 무시한다
    }
  };

  // 초기 히스토리 로드
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const res = await apiFetch(`${API_BASE}/api/v1/trips/${tripGroupId}/chat/messages?size=30`);
      const body = await res.json();
      const page: ChatMessagePage = body.data;
      if (cancelled) return;
      const ascending = [...page.messages].reverse();
      setMessages(prev => mergeAscending(prev, ascending));
      setHasMoreOlder(page.hasNext);
      if (ascending.length > 0) {
        const lastId = ascending[ascending.length - 1].id;
        lastReceivedIdRef.current = lastId;
        markRead(lastId);
      }
      requestAnimationFrame(() => {
        if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
      });
    })();
    return () => {
      cancelled = true;
    };
  }, [tripGroupId]);

  // STOMP 연결 (채팅 화면 진입 시 연결, 종료 시 해제)
  useEffect(() => {
    const client = new Client({
      brokerURL: `${WS_BASE}/ws`,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });
    clientRef.current = client;

    client.onConnect = () => {
      client.subscribe(`/sub/trips/${tripGroupId}/chat`, (frame: IMessage) => {
        appendMessage(JSON.parse(frame.body));
      });
      client.subscribe("/user/queue/errors", () => {
        pendingRef.current = null;
        setSendFailed(true);
      });
      if (lastReceivedIdRef.current != null) recoverGap(lastReceivedIdRef.current);
    };

    client.activate();

    const handleVisibility = () => {
      if (document.visibilityState !== "visible") return;
      if (!client.connected) {
        client.activate();
      } else if (lastReceivedIdRef.current != null) {
        recoverGap(lastReceivedIdRef.current);
      }
    };
    document.addEventListener("visibilitychange", handleVisibility);

    return () => {
      document.removeEventListener("visibilitychange", handleVisibility);
      client.deactivate();
      if (failTimerRef.current) clearTimeout(failTimerRef.current);
      if (readTimerRef.current) clearTimeout(readTimerRef.current);
      if (lastReceivedIdRef.current != null) markRead(lastReceivedIdRef.current);
    };
  }, [tripGroupId]);

  const loadOlder = async () => {
    if (messages.length === 0 || loadingOlder) return;
    setLoadingOlder(true);
    const oldestId = messages[0].id;
    const el = listRef.current;
    const prevHeight = el?.scrollHeight ?? 0;
    try {
      const res = await apiFetch(
        `${API_BASE}/api/v1/trips/${tripGroupId}/chat/messages?cursor=${oldestId}&size=30`,
      );
      const body = await res.json();
      const page: ChatMessagePage = body.data;
      const older = [...page.messages].reverse();
      setMessages(prev => mergeAscending(prev, older));
      setHasMoreOlder(page.hasNext);
      requestAnimationFrame(() => {
        if (el) el.scrollTop = el.scrollHeight - prevHeight;
      });
    } finally {
      setLoadingOlder(false);
    }
  };

  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    if (e.currentTarget.scrollTop < 40 && hasMoreOlder && !loadingOlder) loadOlder();
  };

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    const content = input.trim();
    if (!content || !clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: `/pub/trips/${tripGroupId}/chat`,
      body: JSON.stringify({ content }),
    });
    pendingRef.current = { content };
    setPendingContent(content);
    setSendFailed(false);
    setInput("");
    if (failTimerRef.current) clearTimeout(failTimerRef.current);
    failTimerRef.current = setTimeout(() => {
      if (pendingRef.current?.content === content) setSendFailed(true);
    }, SEND_TIMEOUT_MS);
  };

  const renderItems = useMemo(() => buildRenderItems(messages), [messages]);

  const toggleGroup = (key: string) => {
    setExpandedGroups(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div
        ref={listRef}
        onScroll={handleScroll}
        className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto px-4 py-3"
        aria-label="채팅 메시지 목록"
      >
        {loadingOlder && <div className="py-1 text-center text-xs text-gray-400">불러오는 중...</div>}
        {renderItems.map(item => {
          if (item.kind === "group") {
            const isExpanded = expandedGroups.has(item.key);
            return (
              <div key={item.key} className="my-1 flex flex-col items-center gap-1 text-xs text-gray-400">
                {isExpanded ? (
                  <>
                    {item.items.map(m => (
                      <div key={m.id}>{m.content}</div>
                    ))}
                    <button type="button" onClick={() => toggleGroup(item.key)} className="text-gray-300 underline">
                      접기
                    </button>
                  </>
                ) : (
                  <button type="button" onClick={() => toggleGroup(item.key)} className="underline">
                    이벤트 {item.items.length}건 · 자세히 보기
                  </button>
                )}
              </div>
            );
          }

          const m = item.message;
          return m.messageType === "SYSTEM" ? (
            <div key={m.id} className="my-1 text-center text-xs text-gray-400">
              {m.content}
            </div>
          ) : (
            <div
              key={m.id}
              className={`flex flex-col ${m.senderId === currentUser.id ? "items-end" : "items-start"}`}
            >
              {m.senderId !== currentUser.id && (
                <span className="mb-0.5 px-1 text-[11px] text-gray-400">{m.senderName ?? "알 수 없음"}</span>
              )}
              <div
                className={`max-w-[75%] whitespace-pre-wrap break-words rounded-2xl px-3 py-2 text-sm ${
                  m.senderId === currentUser.id ? "bg-blue-500 text-white" : "bg-gray-100 text-gray-900"
                }`}
              >
                {m.content}
              </div>
              <span className="mt-0.5 px-1 text-[10px] text-gray-300">{formatTime(m.createdAt)}</span>
            </div>
          );
        })}
      </div>

      {pendingContent && (
        <div className="shrink-0 px-4 pb-1 text-[11px] text-gray-400">
          {sendFailed ? "전송 실패 · 다시 시도해주세요" : "전송 중..."}
        </div>
      )}

      <form onSubmit={handleSend} className="flex shrink-0 items-center gap-2 border-t border-gray-100 px-3 py-2">
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          placeholder="메시지 입력"
          className="flex-1 rounded-full bg-gray-100 px-4 py-2 text-sm outline-none"
        />
        <button
          type="submit"
          disabled={!input.trim()}
          aria-label="메시지 전송"
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-500 text-white disabled:bg-gray-200"
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 12h14M13 6l6 6-6 6" />
          </svg>
        </button>
      </form>
    </div>
  );
}
