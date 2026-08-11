import SwiftUI

struct DayPlanView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: DayPlanViewModel
    @ObservedObject private var eventCenter: TripEventNotificationCenter
    @State private var isChatPresented = false
    @ObservedObject private var chatUnreadStore = ChatUnreadStore.shared
    private let memberID: Int64?
    private let onNavigationVisibilityChange: (Bool) -> Void

    init(
        trip: TripDetail,
        dayNumber: Int,
        memberID: Int64? = nil,
        eventCenter: TripEventNotificationCenter,
        onNavigationVisibilityChange: @escaping (Bool) -> Void = { _ in }
    ) {
        _viewModel = StateObject(wrappedValue: DayPlanViewModel(trip: trip, dayNumber: dayNumber))
        self.memberID = memberID
        self.eventCenter = eventCenter
        self.onNavigationVisibilityChange = onNavigationVisibilityChange
    }

    private var isAdmin: Bool {
        guard let memberID else { return false }
        return memberID == viewModel.trip.ownerId
            || viewModel.trip.members.first(where: { $0.memberId == memberID })?.admin == true
    }
    private var tripStarted: Bool { TripDateFormatter.status(startDate: viewModel.trip.startDate, nights: viewModel.trip.nights) != .before }

    var body: some View {
        VStack(spacing: 0) {
            DayPlanHeader(
                dayNumber: viewModel.dayNumber,
                notificationText: eventCenter.bannerText,
                noticePhase: eventCenter.noticePhase,
                canSynchronize: eventCenter.hasPendingChanges(for: timelineSyncScope),
                unreadChatCount: chatUnreadStore.count(for: viewModel.trip.id),
                onBack: { dismiss() },
                onSynchronize: {
                    Task {
                        await viewModel.load()
                        if viewModel.errorMessage == nil {
                            eventCenter.markSynchronized(timelineSyncScope)
                        }
                    }
                },
                onChat: {
                    chatUnreadStore.markRoomOpened(tripID: viewModel.trip.id)
                    isChatPresented = true
                }
            )

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(viewModel.dayNumber)일차 계획")
                            .font(.title.bold())
                        Text("시간 구간을 정한 뒤, 여행 전체 후보 중 하나를 선택합니다.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    if let error = viewModel.errorMessage {
                        Text(error)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.red)
                    }

                    planContent
                }
                .padding(TripLogDesign.horizontalPadding)
                .padding(.bottom, 40)
            }
            .tripLogRefreshable { await viewModel.load() }

            if shouldShowDraftFooter {
                VStack(spacing: 8) {
                    Button(viewModel.isSaving ? "저장 중" : "시간 범위 설정 완료") {
                        Task { _ = await viewModel.completeDrafts() }
                    }
                    .buttonStyle(TripLogPrimaryButtonStyle(color: TripLogPalette.blue))
                    .disabled(viewModel.isSaving)
                    .opacity(viewModel.isSaving ? 0.5 : 1)
                }
                .padding(.horizontal, TripLogDesign.horizontalPadding)
                .padding(.vertical, 14)
                .background(TripLogPalette.surface)
                .overlay(alignment: .top) { Divider() }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .task {
            await viewModel.load()
            await chatUnreadStore.refresh()
            if viewModel.errorMessage == nil {
                eventCenter.markSynchronized(timelineSyncScope)
            }
        }
        .onAppear { onNavigationVisibilityChange(true) }
        .onDisappear { onNavigationVisibilityChange(false) }
        .sheet(isPresented: $isChatPresented, onDismiss: {
            Task { await chatUnreadStore.refresh() }
        }) {
            AdaptiveChatSheet(trip: viewModel.trip, memberID: memberID)
        }
    }

    private var shouldShowDraftFooter: Bool {
        isAdmin && !tripStarted && !viewModel.isLoading && viewModel.timelines.isEmpty
    }

    private var timelineSyncScope: TripEventSyncScope {
        .timeline(dayNumber: viewModel.dayNumber)
    }

    @ViewBuilder
    private var planContent: some View {
        if viewModel.isLoading && viewModel.timelines.isEmpty {
            TripLogLoadingIndicator(size: 24)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 40)
        } else if viewModel.timelines.isEmpty {
            if isAdmin && !tripStarted {
                draftEditor
            } else {
                TripLogEmptyView(
                    icon: "clock.badge.plus",
                    title: isAdmin ? "아직 시간 구간이 없어요" : "방장이 시간 구간을 설정 중입니다",
                    message: isAdmin ? "시간 구간을 추가해 여행 계획을 만들어보세요." : "아직 확정된 시간 구간이 없습니다."
                )
            }
        } else {
            completedPlan
        }
    }

    private var draftEditor: some View {
        VStack(spacing: 14) {
            HStack(spacing: 12) {
                Text("시간 구간").font(.headline)
                Spacer()
                countButton("minus", disabled: viewModel.drafts.count <= 1 || viewModel.isSaving) {
                    withAnimation(.easeInOut(duration: 0.18)) { viewModel.removeLastDraft() }
                }
                Text("\(viewModel.drafts.count)")
                    .font(.headline)
                    .frame(width: 24)
                countButton("plus", disabled: viewModel.isSaving) {
                    withAnimation(.easeInOut(duration: 0.18)) { viewModel.addDraft() }
                }
            }

            ForEach(Array(viewModel.drafts.enumerated()), id: \.element.id) { index, draft in
                DraftTimelineCard(index: index, draft: draft) { value in
                    viewModel.updateDraft(draft, start: value)
                } onEndChange: { value in
                    viewModel.updateDraft(draft, end: value)
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
    }

    private var completedPlan: some View {
        VStack(spacing: 14) {
            HStack {
                Text(viewModel.isEditingTimeRanges ? "시간 구간" : "확정된 계획")
                    .font(.headline)
                Spacer()
                if !tripStarted {
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            viewModel.errorMessage = nil
                            viewModel.isEditingTimeRanges.toggle()
                        }
                    } label: {
                        Image(systemName: "pencil")
                            .font(.system(size: 17, weight: .bold))
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .tint(viewModel.isEditingTimeRanges ? TripLogPalette.blue : .primary)
                    .disabled(viewModel.isSaving)
                    .accessibilityLabel(viewModel.isEditingTimeRanges ? "시간 구간 편집 종료" : "시간 구간 편집")
                }
            }

            if viewModel.isEditingTimeRanges {
                Text("수정된 정보는 즉시 저장됩니다.")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(TripLogPalette.textMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(TripLogPalette.surfaceMuted, in: RoundedRectangle(cornerRadius: 12))

                ForEach(Array(viewModel.timelines.enumerated()), id: \.element.id) { index, item in
                    EditableTimelineCard(
                        index: index,
                        item: item,
                        disabled: viewModel.isSaving,
                        onStartChange: { value in
                            Task { await viewModel.updateTimeline(item, start: value) }
                        },
                        onEndChange: { value in
                            Task { await viewModel.updateTimeline(item, end: value) }
                        },
                        onDelete: isAdmin ? {
                            Task { await viewModel.delete(item) }
                        } : nil
                    )
                }

                if isAdmin {
                    AddTimelineSlot(disabled: viewModel.isSaving) {
                        Task { await viewModel.addTimeline() }
                    }
                }
            } else {
                ForEach(Array(viewModel.timelines.enumerated()), id: \.element.id) { index, item in
                    TimelineSummaryCard(index: index, item: item)
                }
            }
        }
    }

    private func countButton(
        _ systemName: String,
        disabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 15, weight: .bold))
                .frame(width: 18, height: 18)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .disabled(disabled)
    }
}

private struct DayPlanHeader: View {
    let dayNumber: Int
    let notificationText: String?
    let noticePhase: TripEventNoticePhase
    let canSynchronize: Bool
    let unreadChatCount: Int
    let onBack: () -> Void
    let onSynchronize: () -> Void
    let onChat: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            circleButton("chevron.left", label: "뒤로가기", action: onBack)
            TripEventHeaderNotice(message: notificationText, phase: noticePhase) {
                Text("\(dayNumber)일차")
                    .font(.subheadline.bold())
            }
            .frame(maxWidth: .infinity)
            HStack(spacing: 8) {
                circleButton(
                    "arrow.clockwise",
                    highlighted: canSynchronize,
                    label: "동기화",
                    disabled: !canSynchronize,
                    action: onSynchronize
                )
                ZStack(alignment: .topTrailing) {
                    circleButton("bubble.left.and.bubble.right", label: "채팅 열기", action: onChat)
                    ChatUnreadCountBadge(count: unreadChatCount)
                        .offset(x: 3, y: -3)
                        .allowsHitTesting(false)
                }
            }
        }
        .padding(.horizontal, TripLogDesign.horizontalPadding)
        .padding(.top, 8)
        .padding(.bottom, 8)
        .background(TripLogPalette.background)
    }

    private func circleButton(
        _ systemName: String,
        tint: Color = TripLogPalette.blue,
        highlighted: Bool = false,
        label: String,
        disabled: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .semibold))
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .tint(highlighted ? TripLogPalette.blue : tint)
        .disabled(disabled)
        .animation(.easeInOut(duration: 0.24), value: highlighted)
        .accessibilityLabel(label)
    }
}

private struct TimelineRow: View {
    let index: Int
    let item: TimelineItem
    let canEdit: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.isFreeTime ? "자유시간" : "\(index + 1)번째 시간 구간")
                        .font(.headline)
                    if !item.isFreeTime {
                        Text(durationLabel)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                if !item.isFreeTime && canEdit {
                    Menu {
                        Button("수정", systemImage: "pencil", action: onEdit)
                        Button("삭제", systemImage: "trash", role: .destructive, action: onDelete)
                    } label: {
                        Image(systemName: "ellipsis")
                            .frame(width: 38, height: 38)
                            .background(TripLogPalette.surfaceSoft, in: Circle())
                    }
                }
            }

            HStack(spacing: 10) {
                timeBox(title: "시작", value: item.startLabel)
                Text("~")
                    .font(.headline)
                    .foregroundStyle(.secondary)
                timeBox(title: "종료", value: item.endLabel)
            }

            if let place = item.confirmedPlaceName {
                Label(place, systemImage: item.isFreeTime ? "clock" : "mappin.and.ellipse")
                    .font(.subheadline)
            } else if item.voteId != nil {
                Label("장소 투표 진행 중", systemImage: "checklist")
                    .font(.subheadline)
                    .foregroundStyle(.orange)
            } else if !item.isFreeTime {
                Text("확정 장소 없음").font(.caption).foregroundStyle(.secondary)
            }
        }
        .tripLogCard()
    }

    private func timeBox(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            HStack {
                Text(value).font(.subheadline.bold())
                Spacer()
                Image(systemName: "clock")
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 12)
            .frame(height: 44)
            .background(TripLogPalette.surface, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(TripLogPalette.border))
        }
        .frame(maxWidth: .infinity)
    }

    private var durationLabel: String {
        guard let start = LocalDateTimeFormatter.date(from: item.startTime),
              let end = LocalDateTimeFormatter.date(from: item.endTime) else { return "" }
        let minutes = max(0, Int(end.timeIntervalSince(start) / 60))
        if minutes >= 60, minutes % 60 == 0 { return "\(minutes / 60)시간" }
        if minutes >= 60 { return "\(minutes / 60)시간 \(minutes % 60)분" }
        return "\(minutes)분"
    }
}

private struct DraftTimelineCard: View {
    let index: Int
    let draft: TimelineDraft
    let onStartChange: (Date) -> Void
    let onEndChange: (Date) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 3) {
                Text("\(index + 1)번째 시간 구간")
                    .font(.headline)
                Text(TimelineTimeText.duration(start: draft.start, end: draft.end))
                    .font(.caption)
                    .foregroundStyle(TripLogPalette.textSoft)
            }

            TimelineRangeControls(
                start: draft.start,
                end: draft.end,
                disabled: false,
                onStartChange: onStartChange,
                onEndChange: onEndChange
            )
        }
        .tripLogCard()
    }
}

private struct EditableTimelineCard: View {
    let index: Int
    let item: TimelineItem
    let disabled: Bool
    let onStartChange: (Date) -> Void
    let onEndChange: (Date) -> Void
    let onDelete: (() -> Void)?

    private var start: Date {
        LocalDateTimeFormatter.date(from: item.startTime) ?? Date()
    }

    private var end: Date {
        LocalDateTimeFormatter.date(from: item.endTime) ?? Date()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("\(index + 1)번째 시간 구간")
                        .font(.headline)
                    Text(TimelineTimeText.duration(start: start, end: end))
                        .font(.caption)
                        .foregroundStyle(TripLogPalette.textSoft)
                }
                Spacer()
                if let onDelete {
                    Menu {
                        Button(role: .destructive, action: onDelete) {
                            Label {
                                Text("삭제")
                                    .foregroundStyle(.red)
                            } icon: {
                                Image(systemName: "trash")
                                    .foregroundStyle(.red)
                            }
                        }
                    } label: {
                        Image(systemName: "minus")
                            .font(.system(size: 15, weight: .bold))
                            .frame(width: 18, height: 18)
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .controlSize(.regular)
                    .tint(.red)
                    .disabled(disabled)
                    .opacity(disabled ? 0.4 : 1)
                    .accessibilityLabel("\(index + 1)번째 시간 구간 삭제")
                }
            }

            TimelineRangeControls(
                start: start,
                end: end,
                disabled: disabled,
                onStartChange: onStartChange,
                onEndChange: onEndChange
            )
        }
        .tripLogCard()
    }
}

private struct TimelineRangeControls: View {
    let start: Date
    let end: Date
    let disabled: Bool
    let onStartChange: (Date) -> Void
    let onEndChange: (Date) -> Void

    var body: some View {
        HStack(spacing: 12) {
            TimelineTimeButton(title: "시작", value: start, disabled: disabled, onChange: onStartChange)
            Text("~")
                .font(.headline)
                .foregroundStyle(TripLogPalette.textSoft)
                .padding(.top, 20)
            TimelineTimeButton(title: "종료", value: end, disabled: disabled, onChange: onEndChange)
        }
    }
}

private struct TimelineTimeButton: View {
    let title: String
    let value: Date
    let disabled: Bool
    let onChange: (Date) -> Void
    @State private var isPickerPresented = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption)
                .foregroundStyle(TripLogPalette.textMuted)

            Button {
                isPickerPresented = true
            } label: {
                HStack(spacing: 8) {
                    Text(TimelineTimeText.time(value))
                        .font(.subheadline.bold())
                        .monospacedDigit()
                    Spacer(minLength: 4)
                    Image(systemName: "clock")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(TripLogPalette.textMuted)
                }
                .padding(.horizontal, 12)
                .frame(maxWidth: .infinity)
                .frame(height: 46)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.roundedRectangle(radius: 12))
            .tint(TripLogPalette.textSoft)
            .disabled(disabled)
            .opacity(disabled ? 0.6 : 1)
        }
        .frame(maxWidth: .infinity)
        .sheet(isPresented: $isPickerPresented) {
            TimelineTimePickerSheet(value: value, onConfirm: onChange)
                .presentationDetents([.height(348)])
                .presentationDragIndicator(.visible)
        }
    }
}

private struct TimelineSummaryCard: View {
    let index: Int
    let item: TimelineItem

    var body: some View {
        HStack(spacing: 16) {
            VStack(spacing: 5) {
                Text(item.startLabel)
                    .font(.caption.bold())
                Rectangle()
                    .fill(TripLogPalette.border)
                    .frame(width: 2, height: 30)
                Text(item.endLabel)
                    .font(.caption.bold())
            }
            .foregroundStyle(TripLogPalette.textMuted)

            VStack(alignment: .leading, spacing: 5) {
                if item.isFreeTime {
                    HStack(spacing: 7) {
                        categoryBadge(item.category ?? "기타")
                        Text("자유시간").font(.subheadline.bold())
                    }
                } else if let place = item.confirmedPlaceName {
                    HStack(spacing: 7) {
                        categoryBadge(item.category ?? "기타")
                        Text(place)
                            .font(.subheadline.bold())
                            .lineLimit(1)
                    }
                } else {
                    Text("아직 계획이 안 세워졌어요")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(TripLogPalette.textMuted)
                    Text(item.voteId == nil ? "투표 준비 중" : "후보 투표 진행 중")
                        .font(.caption.bold())
                        .foregroundStyle(item.voteId == nil ? TripLogPalette.textSoft : TripLogPalette.blue)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if item.confirmedPlaceName != nil {
                Image(systemName: "checkmark")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.green)
            }
        }
        .tripLogCard()
    }

    private func categoryBadge(_ text: String) -> some View {
        Text(text)
            .font(.caption2.bold())
            .foregroundStyle(TripLogPalette.textMuted)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(TripLogPalette.surfaceSoft, in: Capsule())
    }
}

private struct AddTimelineSlot: View {
    let disabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "plus")
                .font(.system(size: 19, weight: .bold))
        }
        .buttonStyle(.glassProminent)
        .buttonBorderShape(.circle)
        .tint(.green)
        .frame(maxWidth: .infinity)
        .frame(height: 92)
        .background(Color.green.opacity(0.06), in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.green.opacity(0.28), style: StrokeStyle(lineWidth: 2, dash: [7, 6]))
        )
        .disabled(disabled)
        .opacity(disabled ? 0.4 : 1)
        .accessibilityLabel("시간 구간 추가")
    }
}

private enum TimelineTimeText {
    static func time(_ date: Date) -> String {
        let components = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", components.hour ?? 0, components.minute ?? 0)
    }

    static func duration(start: Date, end: Date) -> String {
        let minutes = max(0, minuteValue(end) - minuteValue(start))
        if minutes >= 60, minutes % 60 == 0 { return "\(minutes / 60)시간" }
        if minutes >= 60 { return "\(minutes / 60)시간 \(minutes % 60)분" }
        return "\(minutes)분"
    }

    private static func minuteValue(_ date: Date) -> Int {
        let components = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (components.hour ?? 0) * 60 + (components.minute ?? 0)
    }
}

private struct FreeTimeControl: View {
    @ObservedObject var viewModel: DayPlanViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("자유시간 범위").font(.headline)
            Text(viewModel.settings?.editable == true ? "방장 전용 · 여행 시작 후 고정" : "여행 시작 후에는 변경할 수 없습니다.")
                .font(.caption)
                .foregroundStyle(.secondary)
            HStack {
                Button { Task { await viewModel.changeFreeTime(by: -30) } } label: {
                    Image(systemName: "minus")
                        .frame(width: 18, height: 18)
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .controlSize(.regular)
                .disabled(viewModel.settings?.editable != true || viewModel.freeTimeMinutes <= 30)

                Spacer()
                Text(minutesLabel).font(.headline).foregroundStyle(.blue)
                Spacer()

                Button { Task { await viewModel.changeFreeTime(by: 30) } } label: {
                    Image(systemName: "plus")
                        .frame(width: 18, height: 18)
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .controlSize(.regular)
                .disabled(viewModel.settings?.editable != true || viewModel.freeTimeMinutes >= 180)
            }
        }
        .tripLogCard()
    }

    private var minutesLabel: String {
        let minutes = viewModel.freeTimeMinutes
        if minutes % 60 == 0 { return "\(minutes / 60)시간" }
        if minutes > 60 { return "\(minutes / 60)시간 \(minutes % 60)분" }
        return "\(minutes)분"
    }
}

private struct TimelineTimePickerSheet: View {
    let onConfirm: (Date) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var draft: Date

    init(value: Date, onConfirm: @escaping (Date) -> Void) {
        self.onConfirm = onConfirm
        _draft = State(initialValue: value)
    }

    var body: some View {
        VStack(spacing: 12) {
            Picker("오전 또는 오후", selection: meridiemBinding) {
                Text("오전").tag(false)
                Text("오후").tag(true)
            }
            .pickerStyle(.segmented)

            Text(String(format: "%d:%02d", hour12, minute))
                .font(.system(size: 34, weight: .bold, design: .rounded))
                .monospacedDigit()

            HStack(spacing: 12) {
                valueControl(title: "시", value: String(hour12)) {
                    changeHour(by: -1)
                } onIncrease: {
                    changeHour(by: 1)
                }

                valueControl(title: "분", value: String(format: "%02d", minute)) {
                    changeMinutes(by: -5)
                } onIncrease: {
                    changeMinutes(by: 5)
                }
            }

            HStack(spacing: 8) {
                ForEach([0, 15, 30, 45], id: \.self) { option in
                    Button {
                        setMinute(option)
                    } label: {
                        Text(String(format: "%02d", option))
                            .font(.subheadline.bold())
                            .foregroundStyle(minute == option ? TripLogPalette.blue : TripLogPalette.textMuted)
                            .frame(maxWidth: .infinity)
                            .frame(height: 42)
                    }
                    .buttonStyle(.glass)
                    .tint(minute == option ? TripLogPalette.blue : nil)
                }
            }

            Button {
                onConfirm(draft)
                dismiss()
            } label: {
                Text("완료")
                    .font(.body.bold())
                    .frame(maxWidth: .infinity, minHeight: 50)
            }
            .buttonStyle(.glassProminent)
            .tint(TripLogPalette.blue)
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 0)
        .frame(maxWidth: .infinity, alignment: .top)
        .background(Color.clear)
    }

    private func valueControl(
        title: String,
        value: String,
        onDecrease: @escaping () -> Void,
        onIncrease: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(TripLogPalette.textMuted)

            HStack(spacing: 8) {
                roundButton("minus", label: "\(title) 감소", action: onDecrease)
                Text(value)
                    .font(.title3.bold())
                    .monospacedDigit()
                    .frame(width: 38)
                roundButton("plus", label: "\(title) 증가", action: onIncrease)
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity)
        .glassEffect(.regular.interactive(), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func roundButton(_ systemName: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .bold))
                .frame(width: 18, height: 18)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .controlSize(.regular)
        .accessibilityLabel(label)
    }

    private var hour24: Int {
        Calendar.current.component(.hour, from: draft)
    }

    private var hour12: Int {
        let hour = hour24 % 12
        return hour == 0 ? 12 : hour
    }

    private var minute: Int {
        Calendar.current.component(.minute, from: draft)
    }

    private var meridiemBinding: Binding<Bool> {
        Binding(
            get: { hour24 >= 12 },
            set: { isPM in
                let baseHour = hour24 % 12
                setTime(hour: baseHour + (isPM ? 12 : 0), minute: minute)
            }
        )
    }

    private func changeHour(by delta: Int) {
        let nextHour12 = ((hour12 - 1 + delta + 12) % 12) + 1
        let nextHour24: Int
        if hour24 >= 12 {
            nextHour24 = nextHour12 == 12 ? 12 : nextHour12 + 12
        } else {
            nextHour24 = nextHour12 == 12 ? 0 : nextHour12
        }
        setTime(hour: nextHour24, minute: minute)
    }

    private func changeMinutes(by delta: Int) {
        let current = hour24 * 60 + minute
        let next = min(23 * 60 + 59, max(0, current + delta))
        setTime(hour: next / 60, minute: next % 60)
    }

    private func setMinute(_ value: Int) {
        setTime(hour: hour24, minute: value)
    }

    private func setTime(hour: Int, minute: Int) {
        draft = Calendar.current.date(
            bySettingHour: hour,
            minute: minute,
            second: 0,
            of: draft
        ) ?? draft
    }
}
