import Combine
import SwiftUI
import UIKit

struct TripDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var viewModel: TripDetailViewModel
    private let memberID: Int64?
    @State private var isChatPresented = false
    @State private var isInvitePresented = false
    @State private var selectedTab = TripDetailTab.overview
    @State private var refreshVersion = 0
    @State private var isChildPagePresented = false
    @State private var isFullTimelinePresented = false
    @ObservedObject private var eventCenter: TripEventNotificationCenter
    @ObservedObject private var chatUnreadStore = ChatUnreadStore.shared
    @EnvironmentObject private var chatNotificationCoordinator: ChatNotificationCoordinator
    private let presentsChatOnLoad: Bool
    @State private var didPresentRequestedChat = false

    init(
        tripID: Int64,
        memberID: Int64?,
        eventCenter: TripEventNotificationCenter,
        presentsChatOnLoad: Bool = false
    ) {
        _viewModel = StateObject(wrappedValue: TripDetailViewModel(tripID: tripID))
        self.memberID = memberID
        self.eventCenter = eventCenter
        self.presentsChatOnLoad = presentsChatOnLoad
    }

    var body: some View {
        VStack(spacing: 0) {
            if let trip = viewModel.trip {
                let tripStatus = TripDateFormatter.status(startDate: trip.startDate, nights: trip.nights)

                TripRoomHeader(
                    trip: trip,
                    notificationText: eventCenter.bannerText,
                    noticePhase: eventCenter.noticePhase,
                    canSynchronize: currentSyncScope.map(eventCenter.hasPendingChanges(for:)) ?? false,
                    showsShare: selectedTab == .overview,
                    titleOverride: selectedTab == .photo && tripStatus == .during
                        ? "\(currentDayNumber(for: trip))일차 사진 기록"
                        : nil,
                    onShowAll: selectedTab == .photo && tripStatus == .during
                        ? { isFullTimelinePresented = true }
                        : nil,
                    unreadChatCount: chatUnreadStore.count(for: trip.id),
                    onHome: {
                        eventCenter.stop()
                        dismiss()
                    },
                    onSynchronize: {
                        guard let scope = currentSyncScope else { return }
                        refreshVersion += 1
                        Task {
                            await viewModel.load()
                            if scope == .tripOverview, viewModel.errorMessage == nil {
                                eventCenter.markSynchronized(scope)
                            }
                        }
                    },
                    onChat: {
                        chatUnreadStore.markRoomOpened(tripID: trip.id)
                        chatNotificationCoordinator.setChatRoomOpen(trip.id, isOpen: true)
                        isChatPresented = true
                    },
                    onInvite: { isInvitePresented = true }
                )

                ZStack {
                    TripOverviewView(
                        trip: trip,
                        memberID: memberID,
                        eventCenter: eventCenter,
                        onChildPageVisibilityChange: { isChildPagePresented = $0 }
                    )
                    .id("overview-\(refreshVersion)")
                    .tripTabLayer(isSelected: selectedTab == .overview)

                    PlaceCandidatesView(trip: trip, memberID: memberID, eventCenter: eventCenter)
                        .id("place-\(refreshVersion)")
                        .tripTabLayer(isSelected: selectedTab == .place)

                    if tripStatus == .before {
                        VoteListView(
                            trip: trip,
                            memberID: memberID,
                            eventCenter: eventCenter,
                            onChildPageVisibilityChange: { isChildPagePresented = $0 }
                        )
                        .id("vote-\(refreshVersion)")
                        .tripTabLayer(isSelected: selectedTab == .vote)
                    }

                    photoTab(for: trip)
                        .tripTabLayer(isSelected: selectedTab == .photo)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    if !isChildPagePresented {
                        TripBottomNavigation(
                            tabs: tripStatus == .before
                                ? [.overview, .place, .vote, .photo]
                                : [.overview, .place, .photo],
                            selection: $selectedTab
                        )
                        .padding(.horizontal, 20)
                        .padding(.top, 6)
                        .padding(.bottom, 4)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }
                .animation(.easeInOut(duration: 0.24), value: isChildPagePresented)
            } else if viewModel.isLoading {
                TripLogLoadingIndicator("여행방 불러오는 중", size: 24)
            } else {
                ContentUnavailableView(
                    "여행방을 불러올 수 없습니다",
                    systemImage: "exclamationmark.triangle",
                    description: Text(viewModel.errorMessage ?? "잠시 후 다시 시도해 주세요.")
                )
            }
        }
        .task {
            await viewModel.load()
            await chatUnreadStore.refresh()
            if viewModel.errorMessage == nil {
                eventCenter.markSynchronized(.tripOverview)
            }
            if let tripID = viewModel.trip?.id {
                eventCenter.start(tripID: tripID)
            }
            presentRequestedChatIfNeeded()
        }
        .onChange(of: selectedTab) { _, _ in
            Task { await chatUnreadStore.refresh() }
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active, let tripID = viewModel.trip?.id else { return }
            eventCenter.resume(tripID: tripID)
        }
        .toolbar(.hidden, for: .navigationBar)
        .navigationBarBackButtonHidden(true)
        .background(Color(uiColor: .systemGroupedBackground))
        .sheet(isPresented: $isChatPresented, onDismiss: {
            if let tripID = viewModel.trip?.id {
                chatNotificationCoordinator.setChatRoomOpen(tripID, isOpen: false)
            }
            Task { await chatUnreadStore.refresh() }
        }) {
            if let trip = viewModel.trip {
                AdaptiveChatSheet(trip: trip, memberID: memberID)
            }
        }
        .sheet(isPresented: $isInvitePresented) {
            if let trip = viewModel.trip {
                InviteMembersSheet(trip: trip, memberID: memberID) {
                    await viewModel.load()
                }
                .presentationDragIndicator(.visible)
            }
        }
        .fullScreenCover(isPresented: $isFullTimelinePresented) {
            FullTimelineScreen(trip: viewModel.trip, memberID: memberID)
        }
        .sensoryFeedback(.selection, trigger: selectedTab)
        .onDisappear {
            if let tripID = viewModel.trip?.id {
                chatNotificationCoordinator.setChatRoomOpen(tripID, isOpen: false)
            }
        }
    }

    @ViewBuilder
    private func photoTab(for trip: TripDetail) -> some View {
        let tripStatus = TripDateFormatter.status(startDate: trip.startDate, nights: trip.nights)

        if tripStatus == .before {
            PreTripTimelineView { tab in
                selectedTab = tab
            }
        } else if tripStatus == .during {
            PhotoUploadSheet(
                trip: trip,
                dayNumber: currentDayNumber(for: trip),
                embedded: true,
                onShowAll: { isFullTimelinePresented = true }
            ) {
                Task { await viewModel.load() }
            }
        } else {
            TripTimelineView(trip: trip, memberID: memberID)
                .id("photo-\(refreshVersion)")
        }
    }

    private func currentDayNumber(for trip: TripDetail) -> Int {
        guard let start = TripDateFormatter.date(from: trip.startDate) else { return 1 }
        let calendar = Calendar.current
        let startDay = calendar.startOfDay(for: start)
        let today = calendar.startOfDay(for: Date())
        let difference = calendar.dateComponents([.day], from: startDay, to: today).day ?? 0
        return min(trip.dayCount, max(1, difference + 1))
    }

    private var currentSyncScope: TripEventSyncScope? {
        switch selectedTab {
        case .overview: .tripOverview
        case .place: .candidates
        case .vote: .voteList
        case .photo: nil
        }
    }

    private func presentRequestedChatIfNeeded() {
        guard presentsChatOnLoad,
              !didPresentRequestedChat,
              let trip = viewModel.trip else { return }

        didPresentRequestedChat = true
        chatUnreadStore.markRoomOpened(tripID: trip.id)
        chatNotificationCoordinator.setChatRoomOpen(trip.id, isOpen: true)
        isChatPresented = true
    }

}

private struct TripRoomHeader: View {
    let trip: TripDetail
    let notificationText: String?
    let noticePhase: TripEventNoticePhase
    let canSynchronize: Bool
    let showsShare: Bool
    let titleOverride: String?
    let onShowAll: (() -> Void)?
    let unreadChatCount: Int
    let onHome: () -> Void
    let onSynchronize: () -> Void
    let onChat: () -> Void
    let onInvite: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            roundButton("house", accessibilityLabel: "홈으로 돌아가기", action: onHome)

            TripEventHeaderNotice(message: notificationText, phase: noticePhase) {
                if let titleOverride {
                    Text(titleOverride)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                } else {
                    VStack(spacing: 1) {
                        Text(trip.name)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        Text("\(trip.region) · \(trip.nights)박 \(trip.nights + 1)일")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
            }
            .frame(maxWidth: .infinity)

            HStack(spacing: 8) {
                if let onShowAll {
                    Button("전체보기", action: onShowAll)
                        .font(.caption.bold())
                        .foregroundStyle(TripLogPalette.blue)
                        .frame(minWidth: 62, minHeight: 40)
                        .buttonStyle(.plain)
                } else {
                    roundButton(
                        "arrow.clockwise",
                        highlighted: canSynchronize,
                        accessibilityLabel: "동기화",
                        disabled: !canSynchronize,
                        action: onSynchronize
                    )
                    ZStack(alignment: .topTrailing) {
                        roundButton("bubble.left.and.bubble.right", accessibilityLabel: "채팅 열기", action: onChat)
                        ChatUnreadCountBadge(count: unreadChatCount)
                            .offset(x: 3, y: -3)
                            .allowsHitTesting(false)
                    }
                }
                if showsShare && onShowAll == nil {
                    roundButton("link", accessibilityLabel: "초대 링크 공유", action: onInvite)
                }
            }
        }
        .padding(.horizontal, TripLogDesign.horizontalPadding)
        .padding(.top, 8)
        .padding(.bottom, 10)
        .background(TripLogPalette.background)
    }

    private func roundButton(
        _ systemName: String,
        tint: Color = TripLogPalette.blue,
        highlighted: Bool = false,
        accessibilityLabel: String,
        disabled: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .semibold))
        }
        .animation(.easeInOut(duration: 0.24), value: highlighted)
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .tint(highlighted ? TripLogPalette.blue : tint)
        .disabled(disabled)
        .accessibilityLabel(accessibilityLabel)
    }
}

private struct FullTimelineScreen: View {
    let trip: TripDetail?
    let memberID: Int64?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                TripLogBackButton { dismiss() }
                Spacer()
            }
            .padding(.horizontal, TripLogDesign.horizontalPadding)
            .padding(.top, 8)
            .padding(.bottom, 8)

            if let trip {
                TripTimelineView(trip: trip, memberID: memberID)
            }
        }
        .background(TripLogPalette.background)
    }
}

private struct PreTripTimelineView: View {
    let onSelect: (TripDetailTab) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 7) {
                    Text("아직 여행 시작 전이에요")
                        .font(.headline)
                    Text("계획을 한번 더 점검해보는 건 어때요?")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .background(TripLogPalette.surfaceMuted, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(TripLogPalette.border))

                preTripLink("여행 모임", color: TripLogPalette.blue, tab: .overview)
                preTripLink("후보 장소", color: .green, tab: .place)
                preTripLink("투표", color: .orange, tab: .vote)
            }
            .padding(.horizontal, TripLogDesign.horizontalPadding)
            .padding(.top, 8)
            .padding(.bottom, 100)
        }
    }

    private func preTripLink(_ title: String, color: Color, tab: TripDetailTab) -> some View {
        Button { onSelect(tab) } label: {
            HStack {
                Text(title).font(.body.bold())
                Spacer()
                Image(systemName: "arrow.right")
            }
            .foregroundStyle(color)
            .padding(.horizontal, 20)
            .frame(height: 54)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.roundedRectangle(radius: 16))
        .tint(color)
    }
}

private enum TripDetailTab: CaseIterable, Hashable {
    case overview, place, vote, photo

    var title: String {
        switch self {
        case .overview: "여행방"
        case .place: "장소"
        case .vote: "투표"
        case .photo: "기록"
        }
    }

    var icon: String {
        switch self {
        case .overview: "person.2"
        case .place: "mappin.and.ellipse"
        case .vote: "archivebox"
        case .photo: "photo.on.rectangle"
        }
    }
}

private extension View {
    func tripTabLayer(isSelected: Bool) -> some View {
        self
            .opacity(isSelected ? 1 : 0)
            .allowsHitTesting(isSelected)
            .accessibilityHidden(!isSelected)
            .zIndex(isSelected ? 1 : 0)
    }
}

private struct TripBottomNavigation: View {
    let tabs: [TripDetailTab]
    @Binding var selection: TripDetailTab

    var body: some View {
        GlassEffectContainer(spacing: 0) {
            HStack(spacing: 2) {
                ForEach(tabs, id: \.self) { tab in
                    Button {
                        select(tab)
                    } label: {
                        VStack(spacing: 2) {
                            Image(systemName: tab.icon)
                                .font(.system(size: 19, weight: .semibold))

                            Text(tab.title)
                                .font(.caption2.weight(.semibold))
                        }
                        .foregroundStyle(selection == tab ? TripLogPalette.blue : .secondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background {
                            if selection == tab {
                                Capsule()
                                    .fill(TripLogPalette.blue.opacity(0.14))
                            }
                        }
                        .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(tab.title)
                    .accessibilityAddTraits(selection == tab ? .isSelected : [])
                }
            }
            .padding(4)
            .glassEffect(.regular, in: Capsule())
        }
        .frame(width: navigationWidth)
        .frame(maxWidth: .infinity)
    }

    private var navigationWidth: CGFloat {
        CGFloat(tabs.count) * 88 + 8
    }

    private func select(_ tab: TripDetailTab) {
        guard selection != tab else { return }
        withAnimation(.easeInOut(duration: 0.2)) {
            selection = tab
        }
    }
}

private struct TripOverviewView: View {
    let trip: TripDetail
    let memberID: Int64?
    @ObservedObject var eventCenter: TripEventNotificationCenter
    let onChildPageVisibilityChange: (Bool) -> Void
    @StateObject private var viewModel: TripOverviewViewModel
    @State private var isBulkFreeTimeSheetPresented = false

    private var canEditFreeTimeSettings: Bool {
        memberID == trip.ownerId
            && TripDateFormatter.status(startDate: trip.startDate, nights: trip.nights) == .before
            && viewModel.settings?.editable == true
    }

    private var bulkFreeTimeInitialMinutes: Int {
        let values = viewModel.settings?.days
            .sorted { $0.dayNumber < $1.dayNumber }
            .map(\.freeTimeMinutes) ?? []
        guard let first = values.first else { return 60 }
        return values.allSatisfy { $0 == first } ? first : 60
    }

    private var orderedMembers: [TripMember] {
        let owner = trip.members.filter { $0.memberId == trip.ownerId }
        let otherMembers = trip.members.filter { $0.memberId != trip.ownerId }
        return owner + otherMembers
    }

    init(
        trip: TripDetail,
        memberID: Int64?,
        eventCenter: TripEventNotificationCenter,
        onChildPageVisibilityChange: @escaping (Bool) -> Void
    ) {
        self.trip = trip
        self.memberID = memberID
        self.eventCenter = eventCenter
        self.onChildPageVisibilityChange = onChildPageVisibilityChange
        _viewModel = StateObject(wrappedValue: TripOverviewViewModel(trip: trip))
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("여행 멤버").font(.headline)
                        Spacer()
                        Text("\(trip.members.count)명").font(.caption).foregroundStyle(.secondary)
                    }

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(orderedMembers) { member in
                                VStack(spacing: 6) {
                                    Image(systemName: "person.fill")
                                        .frame(width: 44, height: 44)
                                        .foregroundStyle(member.admin ? Color.orange : TripLogPalette.blue)
                                        .background((member.admin ? Color.orange : TripLogPalette.blue).opacity(0.14), in: Circle())
                                    Text(member.name).font(.caption)
                                }
                            }
                        }
                    }
                }
                .tripLogCard()

                HStack {
                    Text("일차별 계획").font(.headline)
                    Spacer()
                    if canEditFreeTimeSettings {
                        Button {
                            isBulkFreeTimeSheetPresented = true
                        } label: {
                            Image(systemName: "clock")
                                .font(.system(size: 17, weight: .bold))
                        }
                        .buttonStyle(.glass)
                        .buttonBorderShape(.circle)
                        .tint(TripLogPalette.blue)
                        .accessibilityLabel("자유시간 범위 일괄 설정")
                    }
                }

                ForEach(1...trip.dayCount, id: \.self) { dayNumber in
                    TripOverviewDayCard(
                        trip: trip,
                        memberID: memberID,
                        dayNumber: dayNumber,
                        timelines: viewModel.timelinesByDay[dayNumber] ?? [],
                        setting: viewModel.settings?.days.first(where: { $0.dayNumber == dayNumber }),
                        canEditFreeTime: memberID == trip.ownerId && viewModel.settings?.editable == true,
                        eventCenter: eventCenter,
                        onFreeTimeChange: { delta in
                            Task { await viewModel.changeFreeTime(dayNumber: dayNumber, by: delta) }
                        },
                        onChildPageVisibilityChange: onChildPageVisibilityChange
                    )
                }

                if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }
            .padding(.horizontal, TripLogDesign.horizontalPadding)
            .padding(.top, 16)
            .padding(.bottom, 80)
        }
        .task { await viewModel.load() }
        .tripLogRefreshable { await viewModel.load() }
        .sheet(isPresented: $isBulkFreeTimeSheetPresented) {
            TripRoomSettingsSheet(initialMinutes: bulkFreeTimeInitialMinutes) { minutes in
                try await viewModel.applyFreeTimeToAllDays(minutes: minutes)
            }
            .presentationDetents([.height(245)])
            .presentationDragIndicator(.visible)
        }
    }
}

@MainActor
private final class TripOverviewViewModel: ObservableObject {
    @Published var timelinesByDay: [Int: [TimelineItem]] = [:]
    @Published var settings: TripGroupSettings?
    @Published var errorMessage: String?

    private let trip: TripDetail
    private let service = TimelineService.shared

    init(trip: TripDetail) {
        self.trip = trip
    }

    func load() async {
        errorMessage = nil
        do {
            settings = try? await service.settings(tripID: trip.id)
            var loaded: [Int: [TimelineItem]] = [:]
            for dayNumber in 1...trip.dayCount {
                loaded[dayNumber] = try await service.timelines(tripID: trip.id, dayNumber: dayNumber)
                    .sorted { $0.startTime < $1.startTime }
            }
            timelinesByDay = loaded
        } catch {
            if !error.isRequestCancellation {
                errorMessage = error.localizedDescription
            }
        }
    }

    func changeFreeTime(dayNumber: Int, by delta: Int) async {
        guard settings?.editable == true else { return }
        let current = settings?.days.first(where: { $0.dayNumber == dayNumber })?.freeTimeMinutes ?? 60
        let next = min(180, max(30, current + delta))
        guard next != current else { return }
        do {
            settings = try await service.updateFreeTime(tripID: trip.id, dayNumber: dayNumber, minutes: next)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func applyFreeTimeToAllDays(minutes: Int) async throws {
        settings = try await service.updateAllFreeTime(tripID: trip.id, minutes: minutes)
    }
}

private struct TripOverviewDayCard: View {
    let trip: TripDetail
    let memberID: Int64?
    let dayNumber: Int
    let timelines: [TimelineItem]
    let setting: TripDayFreeTimeSetting?
    let canEditFreeTime: Bool
    @ObservedObject var eventCenter: TripEventNotificationCenter
    let onFreeTimeChange: (Int) -> Void
    let onChildPageVisibilityChange: (Bool) -> Void

    private var tripStatus: TripStatus {
        TripDateFormatter.status(startDate: trip.startDate, nights: trip.nights)
    }

    private var plannedTimelines: [TimelineItem] {
        timelines.filter { !$0.isFreeTime }
    }

    private var freeTimeMinutes: Int { setting?.freeTimeMinutes ?? 60 }

    var body: some View {
        VStack(spacing: 0) {
            NavigationLink {
                DayPlanView(
                    trip: trip,
                    dayNumber: dayNumber,
                    memberID: memberID,
                    eventCenter: eventCenter,
                    onNavigationVisibilityChange: onChildPageVisibilityChange
                )
            } label: {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(dayNumber)일차")
                                .font(.headline)
                            Text(TripDateFormatter.display.string(from: TripDateFormatter.dayDate(startDate: trip.startDate, dayNumber: dayNumber)))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if tripStatus == .before {
                            Text(plannedTimelines.isEmpty ? "작성 필요" : "계획 완료")
                                .font(.caption.bold())
                                .foregroundStyle(plannedTimelines.isEmpty ? Color.orange : TripLogPalette.blue)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background((plannedTimelines.isEmpty ? Color.orange : TripLogPalette.blue).opacity(0.14), in: Capsule())
                        }
                    }

                    if tripStatus == .before {
                        Text("\(max(1, plannedTimelines.count))개 시간 구간")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(TripLogPalette.blue)
                    } else {
                        timelineSummary
                    }
                }
                .padding(16)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if tripStatus == .before, memberID == trip.ownerId {
                Divider().padding(.horizontal, 16)
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("자유시간 범위")
                            .font(.caption.weight(.semibold))
                        Text("방장 전용 · 여행 시작 후 고정")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 6)
                    freeTimeButton(systemName: "minus", disabled: !canEditFreeTime || freeTimeMinutes <= 30) {
                        onFreeTimeChange(-30)
                    }
                    Text(minutesLabel)
                        .font(.caption.bold())
                        .foregroundStyle(TripLogPalette.blue)
                        .frame(minWidth: 48)
                    freeTimeButton(systemName: "plus", disabled: !canEditFreeTime || freeTimeMinutes >= 180) {
                        onFreeTimeChange(30)
                    }
                }
                .padding(16)
            }
        }
        .background(TripLogPalette.surfaceMuted, in: RoundedRectangle(cornerRadius: TripLogDesign.cardRadius, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: TripLogDesign.cardRadius, style: .continuous).stroke(TripLogPalette.border))
    }

    @ViewBuilder
    private var timelineSummary: some View {
        if plannedTimelines.isEmpty {
            HStack(spacing: 8) {
                Text("00:00~23:59")
                Text("기타")
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(TripLogPalette.surfaceSoft, in: Capsule())
                Text("자유시간")
            }
            .font(.caption)
        } else {
            VStack(spacing: 7) {
                ForEach(plannedTimelines) { item in
                    HStack(spacing: 8) {
                        Text("\(item.startLabel)~\(item.endLabel)")
                            .foregroundStyle(.secondary)
                        Text(item.confirmedPlaceName ?? "미확정")
                            .fontWeight(.semibold)
                        Spacer()
                    }
                    .font(.caption)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(TripLogPalette.surfaceSoft, in: RoundedRectangle(cornerRadius: 10))
                }
            }
        }
    }

    private func freeTimeButton(systemName: String, disabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.caption.bold())
                .frame(width: 18, height: 18)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .controlSize(.regular)
        .disabled(disabled)
    }

    private var minutesLabel: String {
        if freeTimeMinutes % 60 == 0 { return "\(freeTimeMinutes / 60)시간" }
        if freeTimeMinutes > 60 { return "\(freeTimeMinutes / 60)시간 \(freeTimeMinutes % 60)분" }
        return "\(freeTimeMinutes)분"
    }
}

private struct EditTripSheet: View {
    let trip: TripDetail
    let onSaved: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var region: String
    @State private var startDate: Date
    @State private var nights: Int
    @State private var isSaving = false
    @State private var errorMessage: String?
    private let service = TripService.shared

    init(trip: TripDetail, onSaved: @escaping () async -> Void) {
        self.trip = trip
        self.onSaved = onSaved
        _name = State(initialValue: trip.name)
        _region = State(initialValue: trip.region)
        _startDate = State(initialValue: TripDateFormatter.date(from: trip.startDate) ?? Date())
        _nights = State(initialValue: trip.nights)
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("여행 이름", text: $name)
                    .tripLogInputStyle(height: 44)
                TextField("지역", text: $region)
                    .tripLogInputStyle(height: 44)
                DatePicker("시작일", selection: $startDate, displayedComponents: .date)
                Stepper("\(nights)박 \(nights + 1)일", value: $nights, in: 0...30)
                if let errorMessage { Text(errorMessage).foregroundStyle(.red) }
            }
            .navigationTitle("여행 정보 수정")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") { Task { await save() } }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || region.trimmingCharacters(in: .whitespaces).isEmpty || isSaving)
                }
            }
        }
    }

    private func save() async {
        isSaving = true
        do {
            try await service.updateTrip(tripID: trip.id, name: name, region: region, startDate: startDate, nights: nights)
            await onSaved()
            dismiss()
        } catch { errorMessage = error.localizedDescription }
        isSaving = false
    }
}

@MainActor
private final class PastMatesViewModel: ObservableObject {
    @Published var mates: [PastMate] = []
    @Published var selected = Set<Int64>()
    @Published var search = ""
    @Published var isLoading = false
    @Published var isLoadingNext = false
    @Published var isInviting = false
    @Published var hasNext = false
    @Published var onlineIDs = Set<Int64>()
    @Published var errorMessage: String?

    let trip: TripDetail
    let isAdmin: Bool
    private let service = TripService.shared
    private var page = 0

    var existingMemberIDs: Set<Int64> {
        Set(trip.members.map(\.memberId))
    }

    init(trip: TripDetail, memberID: Int64?) {
        self.trip = trip
        isAdmin = trip.ownerId == memberID
            || trip.members.first(where: { $0.memberId == memberID })?.admin == true
    }

    func reload() async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let result = try await service.pastMates(search: search.trimmingCharacters(in: .whitespaces), page: 0)
            mates = result.items
            page = result.page
            hasNext = result.hasNext
            selected.formIntersection(Set(result.items.map(\.id)))
            await refreshOnline()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func loadNextIfNeeded(current mate: PastMate) async {
        guard mate.id == mates.last?.id, hasNext, !isLoading, !isLoadingNext else { return }
        isLoadingNext = true
        defer { isLoadingNext = false }

        do {
            let result = try await service.pastMates(
                search: search.trimmingCharacters(in: .whitespaces),
                page: page + 1
            )
            let knownIDs = Set(mates.map(\.id))
            mates.append(contentsOf: result.items.filter { !knownIDs.contains($0.id) })
            page = result.page
            hasNext = result.hasNext
            await refreshOnline()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func toggle(_ mate: PastMate) {
        guard isAdmin, !existingMemberIDs.contains(mate.id) else { return }
        if selected.contains(mate.id) { selected.remove(mate.id) }
        else { selected.insert(mate.id) }
    }

    func refreshOnline() async {
        guard !mates.isEmpty else {
            onlineIDs.removeAll()
            return
        }
        guard let statuses = try? await service.onlineStatus(memberIDs: mates.map(\.id)) else { return }
        onlineIDs = Set(statuses.compactMap { $0.value ? $0.key : nil })
    }

    func invite() async -> Bool {
        guard isAdmin, !selected.isEmpty, !isInviting else { return false }
        isInviting = true
        errorMessage = nil
        defer { isInviting = false }

        do {
            try await service.invite(tripID: trip.id, memberIDs: Array(selected))
            selected.removeAll()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }
}

private enum InviteMembersTab: String, CaseIterable, Identifiable {
    case link = "링크 초대"
    case past = "지난 메이트 초대"

    var id: Self { self }
}

private struct InviteMembersSheet: View {
    let trip: TripDetail
    let memberID: Int64?
    let onInvited: () async -> Void
    @State private var selectedTab = InviteMembersTab.link
    @State private var selectedDetent = PresentationDetent.height(425)

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Picker("초대 방식", selection: $selectedTab) {
                    ForEach(InviteMembersTab.allCases) { tab in
                        Text(tab.rawValue).tag(tab)
                    }
                }
                .pickerStyle(.segmented)

                Group {
                    switch selectedTab {
                    case .link:
                        InviteLinkPanel(trip: trip)
                    case .past:
                        PastMatesInvitePanel(trip: trip, memberID: memberID, onInvited: onInvited)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .padding(.horizontal, TripLogDesign.horizontalPadding)
            .padding(.top, 8)
            .navigationTitle("멤버 초대")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.height(425), .large], selection: $selectedDetent)
        .onChange(of: selectedTab) { _, tab in
            withAnimation(.easeInOut(duration: 0.25)) {
                selectedDetent = tab == .link ? .height(425) : .large
            }
        }
    }
}

private struct InviteLinkPanel: View {
    let trip: TripDetail
    @State private var copied = false

    private var inviteURL: URL {
        AppConfiguration.apiBaseURL
            .appendingPathComponent("invite")
            .appendingPathComponent(trip.joinCode)
    }

    var body: some View {
        VStack(spacing: 14) {
            Text("아래 링크를 친구에게 공유해 주세요.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text(inviteURL.absoluteString)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(TripLogPalette.blue)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 110)
                .padding(.horizontal, 14)
                .background(TripLogPalette.blue.opacity(0.09), in: RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(TripLogPalette.blue.opacity(0.18)))

            Button {
                UIPasteboard.general.url = inviteURL
                copied = true
                Task {
                    try? await Task.sleep(for: .seconds(2))
                    copied = false
                }
            } label: {
                Label(copied ? "복사 완료" : "링크 복사", systemImage: copied ? "checkmark" : "doc.on.doc")
                    .font(.subheadline.bold())
                    .frame(maxWidth: .infinity, minHeight: 50)
            }
            .buttonStyle(.glassProminent)
            .buttonBorderShape(.roundedRectangle(radius: 16))
            .tint(TripLogPalette.blue)

            ShareLink(item: inviteURL) {
                Label("링크 공유", systemImage: "square.and.arrow.up")
                    .font(.subheadline.bold())
                    .frame(maxWidth: .infinity, minHeight: 46)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.roundedRectangle(radius: 16))
            .tint(TripLogPalette.blue)

            Spacer(minLength: 0)
        }
    }
}

private struct PastMatesInvitePanel: View {
    @StateObject private var viewModel: PastMatesViewModel
    let onInvited: () async -> Void
    @Environment(\.dismiss) private var dismiss

    init(trip: TripDetail, memberID: Int64?, onInvited: @escaping () async -> Void) {
        _viewModel = StateObject(wrappedValue: PastMatesViewModel(trip: trip, memberID: memberID))
        self.onInvited = onInvited
    }

    var body: some View {
        VStack(spacing: 12) {
            TextField("이름으로 지난 메이트 검색", text: $viewModel.search)
                .textInputAutocapitalization(.never)
                .tripLogInputStyle()

            if !viewModel.isAdmin {
                Text("방장만 지난 메이트를 초대할 수 있어요. 조회는 가능합니다.")
                    .font(.caption)
                    .foregroundStyle(.orange)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .background(Color.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
            }

            if viewModel.isLoading && viewModel.mates.isEmpty {
                TripLogLoadingIndicator("불러오는 중", size: 24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.mates.isEmpty {
                ContentUnavailableView(
                    viewModel.search.trimmingCharacters(in: .whitespaces).isEmpty
                        ? "함께 여행한 메이트가 없습니다"
                        : "검색 결과가 없습니다",
                    systemImage: "person.2.slash"
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List(viewModel.mates) { mate in
                    mateRow(mate)
                        .listRowInsets(EdgeInsets(top: 5, leading: 0, bottom: 5, trailing: 0))
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                        .task { await viewModel.loadNextIfNeeded(current: mate) }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)

                if viewModel.isLoadingNext {
                    TripLogLoadingIndicator(size: 18, lineWidth: 1.8)
                }
            }

            if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button {
                Task {
                    if await viewModel.invite() {
                        await onInvited()
                        dismiss()
                    }
                }
            } label: {
                Text(viewModel.isInviting
                     ? "초대 중..."
                     : "여행방에 초대하기\(viewModel.selected.isEmpty ? "" : " (\(viewModel.selected.count)명)")")
                    .font(.subheadline.bold())
                    .frame(maxWidth: .infinity, minHeight: 50)
            }
            .buttonStyle(.glassProminent)
            .buttonBorderShape(.roundedRectangle(radius: 16))
            .tint(TripLogPalette.blue)
            .disabled(!viewModel.isAdmin || viewModel.selected.isEmpty || viewModel.isInviting)
        }
        .task(id: viewModel.search) {
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled else { return }
            await viewModel.reload()
        }
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(20))
                guard !Task.isCancelled else { return }
                await viewModel.refreshOnline()
            }
        }
    }

    @ViewBuilder
    private func mateRow(_ mate: PastMate) -> some View {
        let isExistingMember = viewModel.existingMemberIDs.contains(mate.id)
        let isSelected = viewModel.selected.contains(mate.id)

        Button { viewModel.toggle(mate) } label: {
            HStack(spacing: 12) {
                Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundStyle(isSelected ? TripLogPalette.blue : .secondary)

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(mate.name)
                            .font(.subheadline.bold())
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        if viewModel.onlineIDs.contains(mate.id) {
                            Circle().fill(.green).frame(width: 7, height: 7)
                            Text("접속중").font(.caption2.bold()).foregroundStyle(.green)
                        }
                    }
                    Text("최근 함께한 여행 \(pastMateLabel(mate))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer()
                if isExistingMember {
                    Text("이미 참여")
                        .font(.caption2.bold())
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(TripLogPalette.surfaceSoft, in: Capsule())
                }
            }
            .padding(12)
            .background(isSelected ? TripLogPalette.blue.opacity(0.09) : TripLogPalette.surfaceSoft)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(isSelected ? TripLogPalette.blue : Color.clear)
            )
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .opacity(isExistingMember ? 0.55 : 1)
        }
        .buttonStyle(.plain)
        .disabled(isExistingMember)
    }

    private func pastMateLabel(_ mate: PastMate) -> String {
        let date = mate.latestTravelDate.split(separator: "-").prefix(2).joined(separator: ".")
        guard let groupName = mate.latestGroupName, !groupName.isEmpty else { return date }
        return "\(groupName) (\(date))"
    }
}

private struct TripRoomSettingsSheet: View {
    let onApply: (Int) async throws -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var minutes = 60
    @State private var errorMessage: String?
    @State private var isSaving = false

    init(initialMinutes: Int, onApply: @escaping (Int) async throws -> Void) {
        self.onApply = onApply
        _minutes = State(initialValue: initialMinutes)
    }

    var body: some View {
        VStack(spacing: 0) {
            Text("모든 일차에 동일한 범위를 적용합니다.")
                .font(.caption)
                .foregroundStyle(TripLogPalette.textMuted)

            HStack(spacing: 20) {
                settingButton("minus", disabled: minutes <= 30 || isSaving) { minutes -= 30 }
                Text(minutesLabel)
                    .font(.body.bold())
                    .foregroundStyle(TripLogPalette.blue)
                    .frame(width: 96)
                settingButton("plus", disabled: minutes >= 180 || isSaving) { minutes += 30 }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 16)
            .padding(.vertical, 20)
            .glassEffect(
                .regular.tint(TripLogPalette.surfaceSoft.opacity(0.68)).interactive(),
                in: RoundedRectangle(cornerRadius: 16, style: .continuous)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(TripLogPalette.border.opacity(0.72), lineWidth: 1)
            )
            .padding(.top, 16)

            if let errorMessage {
                Text(errorMessage)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 12)
            }

            Button {
                Task { await save() }
            } label: {
                Text(isSaving ? "적용 중..." : "모두 적용")
                    .font(.body.bold())
                    .frame(maxWidth: .infinity, minHeight: 50)
            }
            .buttonStyle(.glassProminent)
            .tint(TripLogPalette.blue)
            .disabled(isSaving)
            .opacity(isSaving ? 0.5 : 1)
            .padding(.top, 20)
        }
        .padding(.horizontal, 20)
        .padding(.top, 28)
        .padding(.bottom, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.clear)
    }

    private func settingButton(_ icon: String, disabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.body.bold())
                .foregroundStyle(TripLogPalette.blue)
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.glass)
        .disabled(disabled)
        .opacity(disabled ? 0.35 : 1)
    }

    private var minutesLabel: String {
        if minutes % 60 == 0 { return "\(minutes / 60)시간" }
        if minutes > 60 { return "\(minutes / 60)시간 \(minutes % 60)분" }
        return "\(minutes)분"
    }
    private func save() async {
        guard !isSaving else { return }
        isSaving = true
        errorMessage = nil
        do {
            try await onApply(minutes)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
            isSaving = false
        }
    }
}
