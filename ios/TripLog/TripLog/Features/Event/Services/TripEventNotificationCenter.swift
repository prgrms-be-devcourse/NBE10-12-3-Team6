import Combine
import Foundation
import SwiftUI

enum TripEventNoticePhase: Equatable {
    case titleVisible
    case titleHidden
    case messageVisible
    case messageExiting
    case titleReturning

    var isTitleVisible: Bool {
        self == .titleVisible || self == .titleReturning
    }

    var isMessageVisible: Bool {
        self == .messageVisible
    }
}

@MainActor
final class TripEventNotificationCenter: ObservableObject {
    @Published private(set) var bannerText: String?
    @Published private(set) var noticePhase: TripEventNoticePhase = .titleVisible
    @Published private(set) var latestEvent: TripEventPayload?
    @Published private(set) var pendingEvents: [TripEventPayload] = []
    @Published private(set) var isConnected = false
    @Published private(set) var terminalError: String?

    private let stream = TripEventStream()
    private var activeNoticeEvents: [TripEventPayload] = []
    private var queuedNoticeEvents: [TripEventPayload] = []
    private var isAcceptingEvents = false
    private var groupingTask: Task<Void, Never>?
    private var noticeTask: Task<Void, Never>?
    private var cooldownTask: Task<Void, Never>?
    private var latestEventTask: Task<Void, Never>?
    private var activeTripID: Int64?

    init() {
        stream.onEvent = { [weak self] event in self?.receive(event) }
        stream.onStateChange = { [weak self] connected in self?.isConnected = connected }
        stream.onTerminalError = { [weak self] message in self?.terminalError = message }
    }

    func start(tripID: Int64) {
        guard activeTripID != tripID else { return }
        if activeTripID != nil {
            resetEventState()
        }
        activeTripID = tripID
        terminalError = nil
        stream.connect(tripID: tripID)
    }

    func resume(tripID: Int64) {
        if activeTripID != tripID {
            start(tripID: tripID)
            return
        }

        terminalError = nil
        stream.connect(tripID: tripID, forceReconnect: true)
    }

    func stop() {
        activeTripID = nil
        stream.disconnect()
        resetEventState()
    }

    private func resetEventState() {
        groupingTask?.cancel()
        noticeTask?.cancel()
        cooldownTask?.cancel()
        latestEventTask?.cancel()
        groupingTask = nil
        noticeTask = nil
        cooldownTask = nil
        latestEventTask = nil
        activeNoticeEvents.removeAll()
        queuedNoticeEvents.removeAll()
        isAcceptingEvents = false
        bannerText = nil
        latestEvent = nil
        pendingEvents.removeAll()
        noticePhase = .titleVisible
    }

    func hasPendingChanges(for scope: TripEventSyncScope) -> Bool {
        pendingEvents.contains(where: scope.matches)
    }

    func markSynchronized(_ scope: TripEventSyncScope) {
        pendingEvents.removeAll(where: scope.matches)
    }

    private func receive(_ event: TripEventPayload) {
        latestEvent = event
        latestEventTask?.cancel()
        latestEventTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(5))
            guard !Task.isCancelled else { return }
            self?.latestEvent = nil
            self?.latestEventTask = nil
        }

        if !pendingEvents.contains(where: { $0.eventId == event.eventId }) {
            pendingEvents.append(event)
        }
        openNotice(event)
    }

    private func openNotice(_ event: TripEventPayload) {
        if noticeTask != nil, isAcceptingEvents {
            activeNoticeEvents.append(event)
            bannerText = summary(for: activeNoticeEvents)
            return
        }

        if noticeTask != nil || cooldownTask != nil {
            queuedNoticeEvents.append(event)
            return
        }

        startNotice(with: [event])
    }

    private func startNotice(with events: [TripEventPayload]) {
        guard !events.isEmpty else { return }

        activeNoticeEvents = events
        isAcceptingEvents = true
        noticePhase = .titleVisible
        bannerText = summary(for: events)

        groupingTask?.cancel()
        groupingTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(4.7))
            guard !Task.isCancelled, let self else { return }
            self.isAcceptingEvents = false
            self.groupingTask = nil
        }

        noticeTask?.cancel()
        noticeTask = Task { [weak self] in
            guard let self else { return }

            await Task.yield()
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut(duration: 0.32)) {
                self.noticePhase = .titleHidden
            }

            try? await Task.sleep(for: .seconds(0.36))
            guard !Task.isCancelled else { return }
            withAnimation(.timingCurve(0.22, 1, 0.36, 1, duration: 0.42)) {
                self.noticePhase = .messageVisible
            }

            try? await Task.sleep(for: .seconds(4.04))
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut(duration: 0.32)) {
                self.noticePhase = .messageExiting
            }

            try? await Task.sleep(for: .seconds(0.3))
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut(duration: 0.32)) {
                self.noticePhase = .titleReturning
            }

            try? await Task.sleep(for: .seconds(0.3))
            guard !Task.isCancelled else { return }
            self.bannerText = nil
            self.activeNoticeEvents.removeAll()
            self.isAcceptingEvents = false
            self.noticePhase = .titleVisible
            self.noticeTask = nil

            self.cooldownTask = Task { [weak self] in
                try? await Task.sleep(for: .seconds(3))
                guard !Task.isCancelled, let self else { return }
                self.cooldownTask = nil
                let queued = self.queuedNoticeEvents
                self.queuedNoticeEvents.removeAll()
                self.startNotice(with: queued)
            }
        }
    }

    private func summary(for events: [TripEventPayload]) -> String {
        guard let first = events.first else { return "새로운 변경" }
        if events.count == 1, first.changeCount == 1 {
            return first.message
        }

        let eventCount = events.reduce(0) { $0 + $1.changeCount }
        let summaryTypes = Set(events.map(\.summaryEventType))
        let topics = Set(events.map(\.topic))
        let dayNumbers = events.compactMap(\.dayNumber)
        let sameDayNumber: Int? = dayNumbers.count == events.count && Set(dayNumbers).count == 1
            ? dayNumbers.first
            : nil

        if summaryTypes.count == 1 {
            return sameTypeSummary(
                eventType: first.summaryEventType,
                count: eventCount,
                dayNumber: sameDayNumber
            )
        }

        if topics.count == 1 {
            return sameTopicSummary(topic: first.topic, count: eventCount, dayNumber: sameDayNumber)
        }

        return sameDayNumber == nil
            ? "여행방 변경 \(eventCount)건"
            : "\(sameDayNumber!)일차 변경 \(eventCount)건"
    }

    private func sameTypeSummary(eventType: String, count: Int, dayNumber: Int?) -> String {
        let dayPrefix = dayNumber.map { "\($0)일차 " } ?? ""
        switch eventType {
        case "TRIP_GROUP_UPDATED": return "여행방 정보 변경 \(count)건"
        case "TRIP_MEMBER_JOINED": return "여행 멤버 추가 \(count)명"
        case "FREE_TIME_RANGE_UPDATED": return "자유시간 범위 변경 \(count)건"
        case "WISH_PLACE_ADDED": return "후보 장소 추가 \(count)개"
        case "WISH_PLACE_DELETED": return "후보 장소 삭제 \(count)개"
        case "TIMELINE_CREATED": return "\(dayPrefix)시간 구간 추가 \(count)개"
        case "TIMELINE_TIME_UPDATED": return "\(dayPrefix)시간 변경 \(count)건"
        case "TIMELINE_DELETED": return "\(dayPrefix)시간 구간 삭제 \(count)개"
        case "VOTE_CREATED": return "\(dayPrefix)투표 생성 \(count)개"
        case "VOTE_PARTICIPATION_UPDATED": return "\(dayPrefix)투표 변경 \(count)건"
        case "TIMELINE_PLACE_CONFIRMED": return "\(dayPrefix)장소 확정 \(count)건"
        case "VOTE_EXPIRED": return "\(dayPrefix)투표 종료 \(count)건"
        default:
            return dayNumber == nil
                ? "여행방 변경 \(count)건"
                : "\(dayNumber!)일차 변경 \(count)건"
        }
    }

    private func sameTopicSummary(topic: TripEventTopic, count: Int, dayNumber: Int?) -> String {
        let dayPrefix = dayNumber.map { "\($0)일차 " } ?? ""
        switch topic {
        case .place: return "후보 장소 변경 \(count)건"
        case .timeline: return "\(dayPrefix)시간 구간 변경 \(count)건"
        case .vote: return "\(dayPrefix)투표 변경 \(count)건"
        case .trip: return "여행방 변경 \(count)건"
        }
    }
}

struct TripEventHeaderNotice<Title: View>: View {
    private let message: String?
    private let phase: TripEventNoticePhase
    private let title: Title

    init(
        message: String?,
        phase: TripEventNoticePhase,
        @ViewBuilder title: () -> Title
    ) {
        self.message = message
        self.phase = phase
        self.title = title()
    }

    var body: some View {
        ZStack {
            title
                .opacity(phase.isTitleVisible ? 1 : 0)
                .scaleEffect(phase.isTitleVisible ? 1 : 0.96)

            if let message {
                HStack(spacing: 6) {
                    Image(systemName: "bell.fill")
                        .font(.system(size: 11, weight: .semibold))

                    Text(message)
                        .font(.caption.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.72)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .frame(maxWidth: .infinity)
                .frame(height: 34)
                .background(TripLogPalette.blue, in: Capsule())
                .opacity(phase.isMessageVisible ? 1 : 0)
                .offset(y: phase.isMessageVisible ? 0 : -18)
                .scaleEffect(
                    phase.isMessageVisible ? 1 : 0.82,
                    anchor: .top
                )
                .accessibilityElement(children: .combine)
                .accessibilityLabel(message)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 40)
        .clipped()
    }
}
