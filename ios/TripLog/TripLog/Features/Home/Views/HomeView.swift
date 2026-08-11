import SwiftUI

private enum HomeTripRoute: Hashable {
    case trip(Int64)
    case chat(Int64, UUID)

    var tripID: Int64 {
        switch self {
        case .trip(let tripID), .chat(let tripID, _):
            tripID
        }
    }

    var presentsChatOnLoad: Bool {
        if case .chat = self { return true }
        return false
    }
}

struct HomeView: View {
    let member: Member?
    @Binding var hidesBottomNavigation: Bool
    @StateObject private var viewModel = HomeViewModel()
    @StateObject private var tripEventCenter = TripEventNotificationCenter()
    @ObservedObject private var chatUnreadStore = ChatUnreadStore.shared
    @EnvironmentObject private var chatNotificationCoordinator: ChatNotificationCoordinator
    @State private var isDatePickerPresented = false
    @State private var hasSearched = false
    @State private var navigationPath: [HomeTripRoute] = []
    @State private var isDeleteTripsPresented = false

    var body: some View {
        NavigationStack(path: $navigationPath) {
            VStack(spacing: 0) {
                VStack(spacing: 0) {
                    header

                    VStack(alignment: .leading, spacing: 0) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("안녕하세요, \(member?.name ?? "여행자")님")
                                .font(.title2.bold())
                            Text("여행 모임을 만들고 초대 링크로 멤버를 초대해보세요.")
                                .font(.subheadline)
                                .foregroundStyle(TripLogPalette.textMuted)
                        }
                        .padding(.bottom, 20)

                        searchControls
                            .padding(.bottom, 16)
                    }
                    .padding(.horizontal, TripLogDesign.horizontalPadding)
                }
                .background(TripLogPalette.background.ignoresSafeArea(edges: .top))

                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        if let error = viewModel.errorMessage {
                            Text(error)
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(.red)
                                .padding(.bottom, 10)
                        }

                        tripListContent
                    }
                    .id(viewModel.resultAnimationVersion)
                    .transition(
                        .asymmetric(
                            insertion: .offset(y: 18)
                                .combined(with: .scale(scale: 0.98, anchor: .top))
                                .combined(with: .opacity),
                            removal: .opacity
                        )
                    )
                    .animation(
                        .spring(response: 0.48, dampingFraction: 0.84),
                        value: viewModel.resultAnimationVersion
                    )
                    .padding(.horizontal, TripLogDesign.horizontalPadding)
                    .padding(.trailing, 2)
                    .padding(.top, 16)
                    .padding(.bottom, 104)
                }
                .tripLogRefreshable {
                    await viewModel.load()
                    await chatUnreadStore.refresh()
                }
                .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea(edges: .bottom))
            }
            .navigationDestination(for: HomeTripRoute.self) { route in
                TripDetailView(
                    tripID: route.tripID,
                    memberID: member?.id,
                    eventCenter: tripEventCenter,
                    presentsChatOnLoad: route.presentsChatOnLoad
                )
            }
            .onChange(of: navigationPath.isEmpty, initial: true) { _, isEmpty in
                hidesBottomNavigation = !isEmpty
                if isEmpty {
                    tripEventCenter.stop()
                    Task { await chatUnreadStore.refresh() }
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $viewModel.isCreateTripPresented) {
                CreateTripView { name, region, startDate, nights in
                    await viewModel.createTrip(name: name, region: region, startDate: startDate, nights: nights)
                }
                .presentationDetents([.height(560)])
                .presentationDragIndicator(.visible)
            }
            .sheet(isPresented: $viewModel.isJoinTripPresented) {
                JoinTripView { code in await viewModel.join(code: code) }
                    .presentationDetents([.medium])
                    .presentationDragIndicator(.visible)
            }
            .sheet(isPresented: $isDeleteTripsPresented) {
                DeleteTripsSheet(trips: deletableTrips) { ids in
                    await viewModel.deleteTrips(ids: ids)
                }
                .presentationDetents([.height(deleteTripsSheetHeight)])
                .presentationDragIndicator(.visible)
            }
            .task {
                await viewModel.load()
                await chatUnreadStore.refresh()
            }
            .onChange(of: viewModel.resultAnimationVersion) { _, _ in
                Task { await chatNotificationCoordinator.refreshRooms() }
            }
            .onReceive(NotificationCenter.default.publisher(for: .openTripChatFromNotification)) { notification in
                guard let tripID = tripGroupID(from: notification.userInfo) else { return }
                navigationPath = [.chat(tripID, UUID())]
            }
        }
    }

    private var header: some View {
        HStack {
            Text("내 여행").font(.largeTitle.bold())

            Spacer()

            HStack(spacing: 8) {
                if !deletableTrips.isEmpty {
                    Button {
                        isDeleteTripsPresented = true
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 17, weight: .semibold))
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .tint(TripLogPalette.textSoft)
                    .accessibilityLabel("모임방 삭제")
                }

                Button {
                    viewModel.isCreateTripPresented = true
                } label: {
                    Image(systemName: "plus")
                        .font(.title3.weight(.semibold))
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .tint(TripLogPalette.blue)
                .accessibilityLabel("여행 모임 만들기")
            }
        }
        .padding(.horizontal, TripLogDesign.horizontalPadding)
        .padding(.top, 8)
        .padding(.bottom, 8)
    }

    private var searchControls: some View {
        VStack(spacing: 10) {
            TextField("여행 이름, 지역, 멤버명으로 검색", text: $viewModel.keyword)
                .textInputAutocapitalization(.never)
                .submitLabel(.search)
                .onSubmit { runSearch() }
                .tripLogInputStyle()

            HStack(spacing: 8) {
                Button {
                    isDatePickerPresented = true
                } label: {
                    HStack(spacing: 8) {
                        Text(dateFilterLabel)
                            .foregroundStyle(viewModel.startDate == nil ? .secondary : .primary)
                        Spacer()
                        Image(systemName: "calendar")
                            .font(.system(size: 18))
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 12)
                    .frame(height: 38)
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.roundedRectangle(radius: TripLogDesign.controlRadius))
                .controlSize(.small)
                .tint(viewModel.startDate == nil ? TripLogPalette.textSoft : TripLogPalette.blue)
                .popover(
                    isPresented: $isDatePickerPresented,
                    attachmentAnchor: .rect(.bounds),
                    arrowEdge: .top
                ) {
                    DateFilterPopover(selectedDate: viewModel.startDate) { date in
                        viewModel.startDate = date
                        runSearch()
                    }
                    .presentationCompactAdaptation(.popover)
                }

                if viewModel.startDate != nil {
                    Button {
                        viewModel.startDate = nil
                        viewModel.keyword = ""
                        hasSearched = false
                        Task { await viewModel.load() }
                    } label: {
                        Image(systemName: "xmark")
                            .frame(width: 38, height: 38)
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.roundedRectangle(radius: 12))
                    .controlSize(.small)
                    .tint(TripLogPalette.textSoft)
                    .accessibilityLabel("날짜 검색 초기화")
                }

                Button { runSearch() } label: {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 17, weight: .bold))
                        .frame(width: 38, height: 38)
                }
                .buttonStyle(.glassProminent)
                .buttonBorderShape(.roundedRectangle(radius: 12))
                .controlSize(.small)
                .tint(TripLogPalette.blue)
                .disabled(viewModel.keyword.trimmingCharacters(in: .whitespaces).isEmpty && viewModel.startDate == nil)
                .accessibilityLabel("여행 검색")
            }
        }
    }

    @ViewBuilder
    private var tripListContent: some View {
        if viewModel.isLoading {
            Text("불러오는 중...")
                .font(.subheadline)
                .foregroundStyle(TripLogPalette.textSoft)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 40)
        } else if viewModel.trips.isEmpty && hasSearched {
            Text("찾는 여행 모임이 없습니다")
                .font(.subheadline)
                .foregroundStyle(TripLogPalette.textSoft)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 48)
        } else if viewModel.trips.isEmpty {
            VStack(spacing: 16) {
                Image(systemName: "link")
                    .font(.system(size: 48, weight: .semibold))
                    .foregroundStyle(TripLogPalette.blue.opacity(0.8))
                Text("아직 여행 모임이 없어요")
                    .font(.body.weight(.semibold))
                Text("여행 모임을 만들면 초대 링크가 생성됩니다.")
                    .font(.subheadline)
                    .foregroundStyle(TripLogPalette.textMuted)
                Button("여행 모임 만들기") {
                    viewModel.isCreateTripPresented = true
                }
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 24)
                .frame(height: 48)
                .buttonStyle(.glassProminent)
                .buttonBorderShape(.roundedRectangle(radius: 16))
                .tint(TripLogPalette.blue)
            }
            .frame(maxWidth: .infinity)
            .padding(28)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
        } else {
            ForEach(sortedTrips) { trip in
                NavigationLink(value: HomeTripRoute.trip(trip.id)) {
                    TripCard(trip: trip, unreadCount: chatUnreadStore.count(for: trip.id))
                }
                .buttonStyle(.plain)
                .onAppear {
                    guard sortedTrips.last?.id == trip.id else { return }
                    Task { await viewModel.loadMore() }
                }
            }

            if viewModel.isLoadingMore {
                TripLogLoadingIndicator()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
        }
    }

    private func tripGroupID(from userInfo: [AnyHashable: Any]?) -> Int64? {
        let value = userInfo?[ChatNotificationNavigation.tripGroupIDKey]

        if let number = value as? NSNumber {
            return number.int64Value
        }
        if let integer = value as? Int64 {
            return integer
        }
        if let integer = value as? Int {
            return Int64(integer)
        }
        if let text = value as? String {
            return Int64(text)
        }
        return nil
    }

    private func runSearch() {
        hasSearched = true
        Task { await viewModel.load() }
    }

    private var dateFilterLabel: String {
        guard let date = viewModel.startDate else { return "날짜로 검색" }
        return TripDateFormatter.filterDisplay.string(from: date)
    }

    private var sortedTrips: [TripSummary] {
        viewModel.trips.sorted { left, right in
            statusOrder(TripDateFormatter.status(startDate: left.startDate, nights: left.nights))
                < statusOrder(TripDateFormatter.status(startDate: right.startDate, nights: right.nights))
        }
    }

    private var deletableTrips: [TripSummary] {
        guard let memberID = member?.id else { return [] }
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())

        return viewModel.trips.filter { trip in
            guard trip.ownerId == memberID,
                  let startDate = TripDateFormatter.date(from: trip.startDate) else { return false }
            let daysUntilStart = calendar.dateComponents(
                [.day],
                from: today,
                to: calendar.startOfDay(for: startDate)
            ).day ?? 0
            return daysUntilStart >= 1
        }
    }

    private var deleteTripsSheetHeight: CGFloat {
        let visibleRowCount = min(max(deletableTrips.count, 1), 4)
        let rowHeight: CGFloat = 65
        let rowSpacing: CGFloat = 10
        let listHeight = rowHeight * CGFloat(visibleRowCount)
            + rowSpacing * CGFloat(max(visibleRowCount - 1, 0))
        let headerActionsAndSafeAreaHeight: CGFloat = 174
        return min(520, headerActionsAndSafeAreaHeight + listHeight)
    }

    private func statusOrder(_ status: TripStatus) -> Int {
        switch status {
        case .during: 0
        case .before: 1
        case .after: 2
        }
    }
}

private struct DeleteTripsSheet: View {
    let trips: [TripSummary]
    let onDelete: ([Int64]) async -> Bool

    @Environment(\.dismiss) private var dismiss
    @State private var selectedIDs: Set<Int64> = []
    @State private var isConfirming = false
    @State private var isDeleting = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("모임방 삭제")
                        .font(.title3.bold())
                    Text("방장으로 있고 여행 시작 전날까지 남은 모임방만 삭제할 수 있어요.")
                        .font(.caption)
                        .foregroundStyle(TripLogPalette.textMuted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, TripLogDesign.horizontalPadding)
                .padding(.top, 20)
                .padding(.bottom, 16)

                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(trips) { trip in
                            Button {
                                toggle(trip.id)
                            } label: {
                                HStack(alignment: .top, spacing: 12) {
                                    Image(systemName: selectedIDs.contains(trip.id) ? "checkmark.square.fill" : "square")
                                        .font(.system(size: 21, weight: .semibold))
                                        .foregroundStyle(selectedIDs.contains(trip.id) ? TripLogPalette.blue : TripLogPalette.textSoft)

                                    VStack(alignment: .leading, spacing: 5) {
                                        Text(trip.name)
                                            .font(.subheadline.bold())
                                            .lineLimit(1)
                                        Text(tripDescription(trip))
                                            .font(.caption)
                                            .foregroundStyle(TripLogPalette.textMuted)
                                    }

                                    Spacer(minLength: 0)
                                }
                                .padding(14)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(
                                    selectedIDs.contains(trip.id)
                                        ? TripLogPalette.blue.opacity(0.10)
                                        : TripLogPalette.surface,
                                    in: RoundedRectangle(cornerRadius: 16)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 16)
                                        .stroke(
                                            selectedIDs.contains(trip.id) ? TripLogPalette.blue : TripLogPalette.border,
                                            lineWidth: selectedIDs.contains(trip.id) ? 1.5 : 1
                                        )
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, TripLogDesign.horizontalPadding)
                    .padding(.bottom, 8)
                }
                .frame(height: listViewportHeight)
                .scrollDisabled(trips.count <= 4)

                if let errorMessage {
                    Text(errorMessage)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.red)
                        .padding(.horizontal, TripLogDesign.horizontalPadding)
                        .padding(.bottom, 8)
                }

                HStack(spacing: 10) {
                    Button {
                        dismiss()
                    } label: {
                        Text("취소")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 38)
                    }
                        .buttonStyle(.glass)
                        .buttonBorderShape(.roundedRectangle(radius: 12))
                        .controlSize(.regular)
                        .tint(TripLogPalette.blue)
                        .disabled(isDeleting)

                    Button {
                        isConfirming = true
                    } label: {
                        Text(selectedIDs.isEmpty ? "삭제" : "\(selectedIDs.count)개 삭제")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 38)
                    }
                    .buttonStyle(.glassProminent)
                    .buttonBorderShape(.roundedRectangle(radius: 12))
                    .controlSize(.regular)
                    .tint(.red)
                    .disabled(selectedIDs.isEmpty || isDeleting)
                }
                .padding(.horizontal, TripLogDesign.horizontalPadding)
                .padding(.bottom, 16)
            }
            .toolbar(.hidden, for: .navigationBar)
            .background(Color.clear)
            .alert("선택한 \(selectedIDs.count)개 모임방을 삭제할까요?", isPresented: $isConfirming) {
                Button("취소", role: .cancel) {}
                Button("삭제", role: .destructive) {
                    Task { await performDelete() }
                }
            } message: {
                Text("모임방과 관련된 정보가 함께 삭제되며 삭제 후에는 되돌릴 수 없습니다.")
            }
        }
    }

    private func toggle(_ id: Int64) {
        if selectedIDs.contains(id) {
            selectedIDs.remove(id)
        } else {
            selectedIDs.insert(id)
        }
    }

    private var listViewportHeight: CGFloat {
        let visibleRowCount = min(max(trips.count, 1), 4)
        let rowHeight: CGFloat = 65
        let rowSpacing: CGFloat = 10
        return rowHeight * CGFloat(visibleRowCount)
            + rowSpacing * CGFloat(max(visibleRowCount - 1, 0))
            + 8
    }

    private func tripDescription(_ trip: TripSummary) -> String {
        let dateText = TripDateFormatter.date(from: trip.startDate)
            .map(TripDateFormatter.display.string) ?? trip.startDate
        return "\(trip.region) · \(trip.nights)박 \(trip.nights + 1)일 · \(dateText) 시작"
    }

    private func performDelete() async {
        guard !selectedIDs.isEmpty, !isDeleting else { return }
        isDeleting = true
        errorMessage = nil
        let succeeded = await onDelete(Array(selectedIDs))
        isDeleting = false

        if succeeded {
            dismiss()
        } else {
            errorMessage = "모임방을 삭제하지 못했습니다. 잠시 후 다시 시도해주세요."
        }
    }
}

private struct DateFilterPopover: View {
    let onSelect: (Date) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var date: Date

    init(selectedDate: Date?, onSelect: @escaping (Date) -> Void) {
        self.onSelect = onSelect
        _date = State(initialValue: selectedDate ?? Date())
    }

    var body: some View {
        DatePicker(
            "여행 날짜",
            selection: $date,
            displayedComponents: .date
        )
        .labelsHidden()
        .datePickerStyle(.graphical)
        .environment(\.locale, Locale(identifier: "ko_KR"))
        .padding(10)
        .frame(width: 330)
        .onChange(of: date) { oldDate, newDate in
            guard !Calendar.current.isDate(oldDate, inSameDayAs: newDate) else { return }
            onSelect(newDate)
            dismiss()
        }
    }
}

private struct TripCard: View {
    let trip: TripSummary
    let unreadCount: Int

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 7) {
                HStack(spacing: 6) {
                    Text(trip.name).font(.headline)
                    ChatUnreadCountBadge(count: unreadCount)
                }
                Text("\(trip.region) · \(trip.nights)박 \(trip.nights + 1)일")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                if let date = TripDateFormatter.date(from: trip.startDate) {
                    Text("\(TripDateFormatter.display.string(from: date)) 시작")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }
            }

            Spacer()

            StatusBadge(status: TripDateFormatter.status(startDate: trip.startDate, nights: trip.nights))
        }
        .padding(16)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(uiColor: .separator).opacity(0.35), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.05), radius: 3, y: 1)
    }
}

private struct StatusBadge: View {
    let status: TripStatus

    var body: some View {
        Text(status.title)
            .font(.caption.bold())
            .foregroundStyle(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(color.opacity(0.12), in: Capsule())
    }

    private var color: Color {
        switch status {
        case .before: .blue
        case .during: .green
        case .after: .secondary
        }
    }
}

#Preview {
    HomeView(
        member: Member(id: 1, email: "dev@example.com", name: "개발자"),
        hidesBottomNavigation: .constant(false)
    )
    .environmentObject(ChatNotificationCoordinator())
}
