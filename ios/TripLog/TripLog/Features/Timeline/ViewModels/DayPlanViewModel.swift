import Combine
import Foundation

struct TimelineDraft: Identifiable, Equatable {
    let id: UUID
    var start: Date
    var end: Date

    init(id: UUID = UUID(), start: Date, end: Date) {
        self.id = id
        self.start = start
        self.end = end
    }
}

@MainActor
final class DayPlanViewModel: ObservableObject {
    @Published var timelines: [TimelineItem] = []
    @Published var settings: TripGroupSettings?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var drafts: [TimelineDraft] = []
    @Published var isEditingTimeRanges = false
    @Published var isSaving = false

    let trip: TripDetail
    let dayNumber: Int
    private let service = TimelineService.shared

    init(trip: TripDetail, dayNumber: Int) {
        self.trip = trip
        self.dayNumber = dayNumber
    }

    var freeTimeMinutes: Int {
        settings?.days.first(where: { $0.dayNumber == dayNumber })?.freeTimeMinutes ?? 60
    }

    func load() async {
        isLoading = true
        errorMessage = nil
        do {
            async let timelineRequest = service.timelines(tripID: trip.id, dayNumber: dayNumber)
            async let settingsRequest = service.settings(tripID: trip.id)
            timelines = try await timelineRequest.sorted { $0.startTime < $1.startTime }
            settings = try await settingsRequest
            if timelines.isEmpty, drafts.isEmpty {
                drafts = [makeDraft(startMinute: 9 * 60)]
            }
        } catch {
            if !error.isRequestCancellation {
                errorMessage = error.localizedDescription
            }
        }
        isLoading = false
    }

    func addDraft() {
        let candidate = makeDraft(startMinute: suggestedNextStart(from: drafts))
        let next = drafts + [candidate]
        guard validate(next) else { return }
        drafts = next.sorted { minutes(from: $0.start) < minutes(from: $1.start) }
    }

    func removeLastDraft() {
        guard drafts.count > 1 else { return }
        drafts.removeLast()
        _ = validate(drafts)
    }

    func updateDraft(_ draft: TimelineDraft, start: Date? = nil, end: Date? = nil) {
        guard let index = drafts.firstIndex(where: { $0.id == draft.id }) else { return }
        if let start { drafts[index].start = start }
        if let end { drafts[index].end = end }
        drafts.sort { minutes(from: $0.start) < minutes(from: $1.start) }
        _ = validate(drafts)
    }

    func completeDrafts() async -> Bool {
        guard validate(drafts) else { return false }
        isSaving = true
        defer { isSaving = false }

        let request = TimelineAllCreateRequest(
            dayNumber: dayNumber,
            timelines: drafts.map { draft in
                TimelineCreateRequest(
                    dayNumber: dayNumber,
                    startTime: dateTime(from: draft.start),
                    endTime: dateTime(from: draft.end)
                )
            }
        )

        do {
            try await service.createAll(tripID: trip.id, request: request)
            drafts.removeAll()
            await load()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func addTimeline() async {
        let existingRanges = timelines.compactMap { item -> TimelineDraft? in
            guard let start = LocalDateTimeFormatter.date(from: item.startTime),
                  let end = LocalDateTimeFormatter.date(from: item.endTime) else { return nil }
            return TimelineDraft(start: start, end: end)
        }
        let candidate = makeDraft(startMinute: suggestedNextStart(from: existingRanges))
        guard validate(existingRanges + [candidate]) else { return }

        isSaving = true
        defer { isSaving = false }
        do {
            try await service.create(
                tripID: trip.id,
                request: TimelineCreateRequest(
                    dayNumber: dayNumber,
                    startTime: dateTime(from: candidate.start),
                    endTime: dateTime(from: candidate.end)
                )
            )
            await load()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func updateTimeline(_ item: TimelineItem, start: Date? = nil, end: Date? = nil) async {
        guard let currentStart = LocalDateTimeFormatter.date(from: item.startTime),
              let currentEnd = LocalDateTimeFormatter.date(from: item.endTime) else { return }
        let nextStart = start ?? currentStart
        let nextEnd = end ?? currentEnd

        let ranges = timelines.compactMap { timeline -> TimelineDraft? in
            if timeline.id == item.id {
                return TimelineDraft(start: nextStart, end: nextEnd)
            }
            guard let itemStart = LocalDateTimeFormatter.date(from: timeline.startTime),
                  let itemEnd = LocalDateTimeFormatter.date(from: timeline.endTime) else { return nil }
            return TimelineDraft(start: itemStart, end: itemEnd)
        }
        guard validate(ranges) else { return }

        isSaving = true
        defer { isSaving = false }
        do {
            try await service.update(
                tripID: trip.id,
                timelineID: item.timelineId,
                request: TimelineUpdateRequest(
                    startTime: dateTime(from: nextStart),
                    endTime: dateTime(from: nextEnd)
                )
            )
            await load()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func delete(_ item: TimelineItem) async {
        do {
            try await service.delete(tripID: trip.id, timelineID: item.timelineId)
            await load()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func validate(_ ranges: [TimelineDraft]) -> Bool {
        guard !ranges.contains(where: { minutes(from: $0.end) <= minutes(from: $0.start) }) else {
            errorMessage = "종료 시간이 시작 시간보다 늦어야 합니다."
            return false
        }

        let sorted = ranges.sorted { minutes(from: $0.start) < minutes(from: $1.start) }
        for index in sorted.indices.dropFirst() {
            if minutes(from: sorted[index - 1].end) > minutes(from: sorted[index].start) {
                errorMessage = "시간 구간이 서로 겹칩니다. 겹치지 않게 조정해주세요."
                return false
            }
        }

        errorMessage = nil
        return true
    }

    private func suggestedNextStart(from ranges: [TimelineDraft]) -> Int {
        let lastEnd = ranges.map { minutes(from: $0.end) }.max() ?? 9 * 60
        let rounded = min(23 * 60, (lastEnd / 15) * 15)
        return rounded >= 23 * 60 ? 9 * 60 : rounded
    }

    private func makeDraft(startMinute: Int) -> TimelineDraft {
        let endMinute = min(23 * 60 + 59, startMinute + 60)
        return TimelineDraft(start: time(from: startMinute), end: time(from: endMinute))
    }

    private func time(from minutes: Int) -> Date {
        Calendar.current.date(
            bySettingHour: minutes / 60,
            minute: minutes % 60,
            second: 0,
            of: Date()
        ) ?? Date()
    }

    private func minutes(from date: Date) -> Int {
        let components = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (components.hour ?? 0) * 60 + (components.minute ?? 0)
    }

    private func dateTime(from time: Date) -> String {
        let date = TripDateFormatter.dayDate(startDate: trip.startDate, dayNumber: dayNumber)
        return LocalDateTimeFormatter.string(date: date, time: time)
    }

    func changeFreeTime(by delta: Int) async {
        guard settings?.editable == true else { return }
        let next = min(180, max(30, freeTimeMinutes + delta))
        guard next != freeTimeMinutes else { return }
        do {
            settings = try await service.updateFreeTime(tripID: trip.id, dayNumber: dayNumber, minutes: next)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
