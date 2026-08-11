import SwiftUI

struct VoteListView: View {
    @StateObject private var viewModel: VoteListViewModel
    @ObservedObject private var eventCenter: TripEventNotificationCenter
    @State private var isHostMenuPresented = false
    private let onChildPageVisibilityChange: (Bool) -> Void

    init(
        trip: TripDetail,
        memberID: Int64?,
        eventCenter: TripEventNotificationCenter,
        onChildPageVisibilityChange: @escaping (Bool) -> Void = { _ in }
    ) {
        _viewModel = StateObject(wrappedValue: VoteListViewModel(trip: trip, memberID: memberID))
        self.eventCenter = eventCenter
        self.onChildPageVisibilityChange = onChildPageVisibilityChange
    }

    var body: some View {
        ScrollView {
                LazyVStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Text("일차별 투표")
                            .font(.body.weight(.semibold))
                        Spacer()
                        if viewModel.isAdmin {
                            Button {
                                withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                                    isHostMenuPresented.toggle()
                                }
                            } label: {
                                Image(systemName: "crown.fill")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundStyle(.yellow)
                            }
                            .buttonStyle(.glass)
                            .buttonBorderShape(.circle)
                            .tint(.yellow)
                            .accessibilityLabel("방장 투표 설정")
                        }
                    }
                    .overlay(alignment: .topTrailing) {
                        if viewModel.isAdmin && isHostMenuPresented {
                            HostVoteDefaultMenu(
                                isAnonymous: viewModel.isAnonymousVoteDefault,
                                isSaving: viewModel.isUpdatingAnonymous,
                                onChange: { value in
                                    Task { await viewModel.updateAnonymousVoteDefault(value) }
                                }
                            )
                            .offset(y: 48)
                            .transition(.scale(scale: 0.82, anchor: .topTrailing).combined(with: .opacity))
                            .zIndex(20)
                        }
                    }
                    .zIndex(20)
                    if let error = viewModel.errorMessage {
                        Text(error).font(.footnote).foregroundStyle(.red)
                    }
                    if viewModel.isLoading && viewModel.groups.isEmpty {
                        TripLogLoadingIndicator(size: 24)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 50)
                    } else if viewModel.groups.isEmpty {
                        TripLogEmptyView(icon: "checklist", title: "투표할 시간 구간이 없어요", message: "먼저 일차별 시간 구간을 만들어주세요.")
                    } else {
                        ForEach(Array(viewModel.groups.enumerated()), id: \.element.id) { dayIndex, group in
                            VoteDaySection(
                                dayNumber: dayIndex + 1,
                                group: group,
                                isAdmin: viewModel.isAdmin,
                                trip: viewModel.trip,
                                memberID: viewModel.memberID,
                                eventCenter: eventCenter,
                                onChildPageVisibilityChange: onChildPageVisibilityChange,
                                onCreate: { item in Task { await viewModel.createVote(for: item) } }
                            )
                        }
                    }
                }
                .padding(.horizontal, TripLogDesign.horizontalPadding)
                .padding(.top, 8)
                .padding(.bottom, 100)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .task {
            await viewModel.load()
            if viewModel.errorMessage == nil {
                eventCenter.markSynchronized(.voteList)
            }
        }
        .tripLogRefreshable { await viewModel.load() }
    }
}

private struct HostVoteDefaultMenu: View {
    let isAnonymous: Bool
    let isSaving: Bool
    let onChange: (Bool) -> Void

    var body: some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 2) {
                Text("익명 투표")
                    .font(.subheadline.weight(.semibold))
                Text("투표 기본값")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Toggle("", isOn: Binding(get: { isAnonymous }, set: onChange))
                .labelsHidden()
                .disabled(isSaving)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(TripLogPalette.surfaceMuted, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.secondary.opacity(0.24)))
        .padding(8)
        .frame(width: 208)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.secondary.opacity(0.25)))
        .shadow(color: .black.opacity(0.2), radius: 14, y: 7)
    }
}

private struct VoteDaySection: View {
    let dayNumber: Int
    let group: VoteDayGroup
    let isAdmin: Bool
    let trip: TripDetail
    let memberID: Int64?
    @ObservedObject var eventCenter: TripEventNotificationCenter
    let onChildPageVisibilityChange: (Bool) -> Void
    let onCreate: (VoteTimeline) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("\(dayNumber)일차 · \(displayDate)")
                .font(.headline)
            if group.timeLines.isEmpty {
                HStack {
                    Spacer()
                    Text("일정 확정 후 투표 가능")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer()
                }
                .padding(16)
                .background(TripLogPalette.surfaceMuted)
                .clipShape(RoundedRectangle(cornerRadius: TripLogDesign.cardRadius))
            } else {
                ForEach(group.timeLines) { item in
                    if let voteID = item.voteId {
                        NavigationLink {
                            VoteDetailView(
                                trip: trip,
                                memberID: memberID,
                                voteID: voteID,
                                title: "\(dayNumber)일차 \(item.timeLabel) 투표",
                                isAdmin: isAdmin,
                                eventCenter: eventCenter
                            )
                            .onAppear { onChildPageVisibilityChange(true) }
                            .onDisappear { onChildPageVisibilityChange(false) }
                        } label: {
                            VoteTimelineRow(item: item, actionTitle: actionTitle(for: item))
                        }
                        .buttonStyle(.plain)
                    } else {
                        Button { onCreate(item) } label: {
                            VoteTimelineRow(item: item, actionTitle: "투표 생성하기")
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var displayDate: String {
        guard let date = TripDateFormatter.date(from: group.date) else { return group.date }
        return TripDateFormatter.display.string(from: date)
    }

    private func actionTitle(for item: VoteTimeline) -> String {
        switch item.voteStatus {
        case "투표 확정": "확정됨"
        case "투표 기한 만료": "투표 마감"
        default: "투표하기"
        }
    }
}

private struct VoteTimelineRow: View {
    let item: VoteTimeline
    let actionTitle: String

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 5) {
                Text("\(item.timeLabel) 시작").font(.subheadline).foregroundStyle(.secondary)
                Text(displayTitle)
                    .font(.headline)
                    .foregroundStyle(item.voteId == nil ? .secondary : .primary)
            }
            Spacer()
            Text(actionTitle)
                .font(.caption.bold())
                .foregroundStyle(badgeColor)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(badgeColor.opacity(0.12), in: Capsule())
            Image(systemName: "chevron.right").foregroundStyle(.secondary)
        }
        .padding(16)
        .background(TripLogPalette.surfaceMuted)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var displayTitle: String {
        guard item.voteId != nil else { return "투표 없음" }
        return item.confirmedPlaceName.isEmpty ? "미확정" : item.confirmedPlaceName
    }

    private var badgeColor: Color {
        switch actionTitle {
        case "확정됨": .green
        case "투표 마감": .secondary
        default: .blue
        }
    }
}

struct VoteDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: VoteDetailViewModel
    @ObservedObject private var eventCenter: TripEventNotificationCenter
    let trip: TripDetail
    let memberID: Int64?
    let title: String
    @State private var confirmPresented = false
    @State private var isHostMenuPresented = false
    @State private var pendingPlaceID: Int64?
    @State private var isChatPresented = false
    @State private var localNoticeText: String?
    @State private var localNoticePhase: TripEventNoticePhase = .titleVisible
    @State private var localNoticeTask: Task<Void, Never>?
    @ObservedObject private var chatUnreadStore = ChatUnreadStore.shared

    init(
        trip: TripDetail,
        memberID: Int64?,
        voteID: Int64,
        title: String,
        isAdmin: Bool,
        eventCenter: TripEventNotificationCenter
    ) {
        self.trip = trip
        self.memberID = memberID
        self.title = title
        self.eventCenter = eventCenter
        _viewModel = StateObject(wrappedValue: VoteDetailViewModel(tripID: trip.id, voteID: voteID, isAdmin: isAdmin))
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .center, spacing: 12) {
                    Text(title)
                        .font(.title2.bold())
                        .lineLimit(1)
                    Spacer()
                    if viewModel.isAdmin {
                        Button {
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                                isHostMenuPresented.toggle()
                            }
                        } label: {
                            Image(systemName: "crown")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(.yellow)
                        }
                        .buttonStyle(.glass)
                        .buttonBorderShape(.circle)
                        .tint(.yellow)
                        .accessibilityLabel("방장 투표 설정")
                    }
                }
                .overlay(alignment: .topTrailing) {
                    if viewModel.isAdmin, isHostMenuPresented, let detail = viewModel.detail {
                        VoteDetailHostMenu(
                            isAnonymous: detail.isAnonymous,
                            locked: detail.voteStatus != "투표 진행중",
                            canConfirm: detail.voteStatus == "투표 진행중" && detail.voteResults.contains(where: { $0.count > 0 }),
                            onAnonymousChange: { value in Task { await viewModel.setAnonymous(value) } },
                            onConfirm: {
                                withAnimation { isHostMenuPresented = false }
                                confirmPresented = true
                            }
                        )
                        .offset(y: 48)
                        .transition(.scale(scale: 0.82, anchor: .topTrailing).combined(with: .opacity))
                        .zIndex(30)
                    }
                }
                .zIndex(30)
                if let error = viewModel.errorMessage {
                    Text(error).font(.footnote).foregroundStyle(.red)
                }
                if let detail = viewModel.detail {
                    ForEach(options(from: detail)) { option in
                        Button {
                            guard detail.voteStatus == "투표 진행중" else { return }
                            pendingPlaceID = option.placeID
                        } label: {
                            VoteOptionCard(
                                option: option,
                                total: max(1, totalVotes(in: detail)),
                                isAnonymous: detail.isAnonymous,
                                isPending: pendingPlaceID == option.placeID
                            )
                        }
                        .buttonStyle(.plain)
                        .disabled(detail.voteStatus != "투표 진행중")
                    }

                    if options(from: detail).isEmpty {
                        TripLogEmptyView(icon: "mappin.slash", title: "투표 후보가 없습니다", message: "여행방의 장소 탭에서 후보 장소를 추가해주세요.")
                    }

                } else {
                    TripLogLoadingIndicator(size: 24)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 60)
                }
            }
            .padding(TripLogDesign.horizontalPadding)
        }
        .toolbar(.hidden, for: .navigationBar)
        .safeAreaInset(edge: .top, spacing: 0) {
            VoteDetailHeader(
                title: title,
                notificationText: localNoticeText ?? eventCenter.bannerText,
                noticePhase: localNoticeText == nil ? eventCenter.noticePhase : localNoticePhase,
                canSynchronize: eventCenter.hasPendingChanges(for: voteSyncScope),
                unreadChatCount: chatUnreadStore.count(for: trip.id),
                onBack: { dismiss() },
                onSynchronize: {
                    Task {
                        await viewModel.load()
                        if viewModel.errorMessage == nil {
                            eventCenter.markSynchronized(voteSyncScope)
                        }
                    }
                },
                onChat: {
                    chatUnreadStore.markRoomOpened(tripID: trip.id)
                    isChatPresented = true
                }
            )
        }
        .task {
            await viewModel.load()
            await chatUnreadStore.refresh()
            if viewModel.errorMessage == nil {
                eventCenter.markSynchronized(voteSyncScope)
            }
        }
        .tripLogRefreshable { await viewModel.load() }
        .sheet(isPresented: $isChatPresented, onDismiss: {
            Task { await chatUnreadStore.refresh() }
        }) {
            AdaptiveChatSheet(trip: trip, memberID: memberID)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if let detail = viewModel.detail {
                if detail.voteStatus == "투표 진행중" {
                    HStack(spacing: 10) {
                        Button {
                            guard let random = options(from: detail).randomElement() else { return }
                            Task {
                                await viewModel.vote(placeID: random.placeID)
                                pendingPlaceID = nil
                            }
                        } label: {
                            Text("랜덤 투표")
                                .font(.body.bold())
                                .frame(maxWidth: .infinity)
                                .frame(minHeight: 44)
                        }
                        .buttonStyle(.glass)
                        .buttonBorderShape(.roundedRectangle(radius: 16))
                        .tint(.purple)
                        .disabled(options(from: detail).isEmpty || detail.updateCount >= 2)

                        Button {
                            guard let pendingPlaceID else { return }
                            Task {
                                await viewModel.vote(placeID: pendingPlaceID)
                                self.pendingPlaceID = nil
                            }
                        } label: {
                            Text("투표하기")
                                .font(.body.bold())
                                .frame(maxWidth: .infinity)
                                .frame(minHeight: 44)
                        }
                        .buttonStyle(.glassProminent)
                        .buttonBorderShape(.roundedRectangle(radius: 16))
                        .tint(TripLogPalette.blue)
                        .disabled(pendingPlaceID == nil || detail.updateCount >= 2)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(TripLogPalette.background)
                    .overlay(alignment: .top) { Divider() }
                } else {
                    Text(detail.voteStatus == "투표 확정" ? "투표가 확정되었습니다" : "투표 기한이 만료되었습니다")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(TripLogPalette.surfaceSoft, in: RoundedRectangle(cornerRadius: 16))
                        .padding(16)
                        .background(TripLogPalette.background)
                }
            }
        }
        .alert("투표 결과를 확정할까요?", isPresented: $confirmPresented) {
            Button("확정", role: .destructive) { Task { await viewModel.confirm() } }
            Button("취소", role: .cancel) {}
        }
        .onChange(of: viewModel.toastMessage) { _, message in
            guard let message else { return }
            showLocalNotice(message)
        }
        .onDisappear {
            localNoticeTask?.cancel()
            localNoticeTask = nil
        }
        .sensoryFeedback(.selection, trigger: pendingPlaceID)
    }

    private func showLocalNotice(_ message: String) {
        localNoticeTask?.cancel()
        localNoticeText = message
        localNoticePhase = .titleVisible

        localNoticeTask = Task { @MainActor in
            await Task.yield()
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut(duration: 0.32)) {
                localNoticePhase = .titleHidden
            }

            do { try await Task.sleep(for: .seconds(0.36)) } catch { return }
            withAnimation(.timingCurve(0.22, 1, 0.36, 1, duration: 0.42)) {
                localNoticePhase = .messageVisible
            }

            do { try await Task.sleep(for: .seconds(4.04)) } catch { return }
            withAnimation(.easeInOut(duration: 0.32)) {
                localNoticePhase = .messageExiting
            }

            do { try await Task.sleep(for: .seconds(0.3)) } catch { return }
            withAnimation(.easeInOut(duration: 0.32)) {
                localNoticePhase = .titleReturning
            }

            do { try await Task.sleep(for: .seconds(0.3)) } catch { return }
            localNoticeText = nil
            localNoticePhase = .titleVisible
            viewModel.toastMessage = nil
            localNoticeTask = nil
        }
    }

    private func totalVotes(in detail: VoteDetail) -> Int {
        detail.voteResults.reduce(0) { $0 + $1.count }
    }

    private func options(from detail: VoteDetail) -> [VoteOption] {
        let resultsByID = Dictionary(uniqueKeysWithValues: detail.voteResults.compactMap { result in
            result.tripPlaceId.map { ($0, result) }
        })
        return detail.wishPlaceFindResponses.map { place in
            let result = resultsByID[place.id]
            return VoteOption(
                placeID: place.id,
                name: place.name,
                address: place.address,
                category: place.category,
                createdBy: place.createdBy,
                count: result?.count ?? 0,
                isVoted: result?.isVoted ?? false,
                voters: result?.voters ?? []
            )
        }
    }

    private var voteSyncScope: TripEventSyncScope {
        .voteDetail(voteID: viewModel.voteID)
    }
}

private struct VoteDetailHeader: View {
    let title: String
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
                Text(title)
                    .font(.subheadline.bold())
                    .lineLimit(1)
                    .minimumScaleFactor(0.76)
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
                .foregroundStyle(highlighted ? Color.white : tint)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .controlSize(.regular)
        .tint(highlighted ? TripLogPalette.blue : tint)
        .disabled(disabled)
        .animation(.easeInOut(duration: 0.24), value: highlighted)
        .accessibilityLabel(label)
    }
}

private struct VoteDetailHostMenu: View {
    let isAnonymous: Bool
    let locked: Bool
    let canConfirm: Bool
    let onAnonymousChange: (Bool) -> Void
    let onConfirm: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("익명 투표").font(.subheadline.bold())
                    Text("현재 투표 설정").font(.system(size: 11)).foregroundStyle(.secondary)
                }
                Spacer()
                Toggle("", isOn: Binding(get: { isAnonymous }, set: onAnonymousChange))
                    .labelsHidden()
                    .disabled(locked)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(TripLogPalette.surfaceMuted, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(TripLogPalette.border))

            Button(action: onConfirm) {
                Label("투표 확정", systemImage: "chart.bar.fill")
                    .font(.subheadline.bold())
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .frame(height: 42)
            }
            .buttonStyle(.glassProminent)
            .buttonBorderShape(.roundedRectangle(radius: 12))
            .tint(.green)
            .disabled(!canConfirm)
        }
        .padding(8)
        .frame(width: 208)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(TripLogPalette.border))
        .shadow(color: .black.opacity(0.24), radius: 16, y: 8)
    }
}

private struct VoteOption: Identifiable {
    let placeID: Int64
    let name: String
    let address: String
    let category: String
    let createdBy: String
    let count: Int
    let isVoted: Bool
    let voters: [VoteVoter]
    var id: Int64 { placeID }
}

private struct VoteOptionCard: View {
    let option: VoteOption
    let total: Int
    let isAnonymous: Bool
    let isPending: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(option.name).font(.headline)
                        if !option.category.isEmpty {
                            Text(option.category)
                                .font(.caption2.bold())
                                .foregroundStyle(TripLogPalette.blue)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(TripLogPalette.blue.opacity(0.10), in: Capsule())
                        }
                    }
                    Text(option.address).font(.caption).foregroundStyle(.secondary)
                    Text("등록자 \(option.createdBy)")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
                Spacer()
                if option.isVoted { Image(systemName: "checkmark.circle.fill").foregroundStyle(.blue) }
                Text("\(option.count)표").font(.headline)
            }
            ProgressView(value: Double(option.count), total: Double(total)).tint(.blue)
            if !isAnonymous && !option.voters.isEmpty {
                Text(option.voters.map(\.name).joined(separator: ", "))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .background(isPending ? TripLogPalette.blue.opacity(0.12) : TripLogPalette.surfaceMuted)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(isPending ? TripLogPalette.blue : TripLogPalette.border, lineWidth: isPending ? 1.5 : 1))
    }
}
