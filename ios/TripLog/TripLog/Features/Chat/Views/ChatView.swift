import SwiftUI

struct ChatUnreadCountBadge: View {
    let count: Int

    var body: some View {
        if count > 0 {
            Text(count > 99 ? "99+" : String(count))
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(.white)
                .padding(.horizontal, count > 9 ? 4 : 0)
                .frame(minWidth: 16, minHeight: 16)
                .background(Color.red, in: Capsule())
                .overlay(Capsule().stroke(TripLogPalette.background, lineWidth: 1.5))
                .accessibilityLabel("읽지 않은 채팅 \(count)개")
        }
    }
}

struct AdaptiveChatSheet: View {
    let trip: TripDetail
    let memberID: Int64?

    @State private var preferredHeight = Self.minimumHeight

    var body: some View {
        ChatView(trip: trip, memberID: memberID) { contentHeight in
            let nextHeight = min(
                Self.maximumHeight,
                max(Self.minimumHeight, contentHeight + 112)
            )
            guard abs(preferredHeight - nextHeight) > 4 else { return }
            withAnimation(.easeInOut(duration: 0.22)) {
                preferredHeight = nextHeight
            }
        }
        .presentationDetents([.height(preferredHeight)])
        .presentationDragIndicator(.visible)
    }

    private static var minimumHeight: CGFloat {
        max(300, screenHeight * 0.36)
    }

    private static var maximumHeight: CGFloat {
        screenHeight * 0.72
    }

    private static var screenHeight: CGFloat {
        UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.screen.bounds.height }
            .max() ?? 844
    }
}

struct ChatView: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var viewModel: ChatViewModel
    @State private var expandedSystemGroups: Set<String> = []
    @FocusState private var isInputFocused: Bool
    private let onContentHeightChange: (CGFloat) -> Void

    init(
        trip: TripDetail,
        memberID: Int64?,
        onContentHeightChange: @escaping (CGFloat) -> Void = { _ in }
    ) {
        _viewModel = StateObject(wrappedValue: ChatViewModel(trip: trip, memberID: memberID))
        self.onContentHeightChange = onContentHeightChange
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 10) {
                        if viewModel.hasOlder {
                            Button("이전 메시지 불러오기") { Task { await viewModel.loadOlder() } }
                                .font(.caption)
                        }
                        ForEach(chatRenderItems) { item in
                            chatRenderItem(item)
                                .id(item.scrollTargetID)
                        }
                    }
                    .padding()
                    .background {
                        GeometryReader { proxy in
                            Color.clear.preference(
                                key: ChatContentHeightPreferenceKey.self,
                                value: proxy.size.height
                            )
                        }
                    }
                }
                .onPreferenceChange(ChatContentHeightPreferenceKey.self, perform: onContentHeightChange)
                .onChange(of: viewModel.messages.count) { _, _ in
                    if let id = viewModel.messages.last?.id {
                        withAnimation { proxy.scrollTo(id, anchor: .bottom) }
                    }
                }
            }
            .overlay {
                if viewModel.isLoading && viewModel.messages.isEmpty {
                    TripLogLoadingIndicator(size: 24)
                }
                else if viewModel.messages.isEmpty {
                    ContentUnavailableView("아직 메시지가 없습니다", systemImage: "bubble.left.and.bubble.right", description: Text("첫 메시지를 남겨보세요."))
                }
            }

            if viewModel.pendingContent != nil {
                Text(
                    viewModel.sendFailed
                        ? (viewModel.sendStatusMessage ?? "전송 실패 · 다시 시도해주세요.")
                        : "전송 중..."
                )
                    .font(.caption2)
                    .foregroundStyle(viewModel.sendFailed ? Color.red : TripLogPalette.textMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                    .padding(.bottom, 2)
            }

            HStack(spacing: 7) {
                TextField(
                    "",
                    text: $viewModel.text,
                    prompt: Text("메시지 입력").foregroundStyle(TripLogPalette.textMuted),
                    axis: .vertical
                )
                    .lineLimit(1...4)
                    .foregroundStyle(.primary)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 11)
                    .frame(minHeight: 44)
                    .glassEffect(
                        .regular
                            .tint(TripLogPalette.surfaceSoft.opacity(0.72))
                            .interactive(),
                        in: Capsule()
                    )
                    .overlay(
                        Capsule()
                            .stroke(
                                isInputFocused ? TripLogPalette.blue.opacity(0.9) : TripLogPalette.border,
                                lineWidth: isInputFocused ? 1.5 : 1
                            )
                    )
                    .shadow(color: isInputFocused ? TripLogPalette.blue.opacity(0.18) : .clear, radius: 8)
                    .animation(.easeInOut(duration: 0.2), value: isInputFocused)
                    .focused($isInputFocused)
                    .submitLabel(.send)
                    .onSubmit { Task { await viewModel.send() } }
                    .layoutPriority(1)
                Button { Task { await viewModel.send() } } label: {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 13, weight: .bold))
                        .frame(width: 30, height: 30)
                }
                .buttonStyle(.glassProminent)
                .buttonBorderShape(.circle)
                .controlSize(.small)
                .tint(TripLogPalette.blue)
                .disabled(!canSend)
                .accessibilityLabel("메시지 전송")
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
            .background(Color.clear)
        }
        .padding(.top, 20)
        .background(Color.clear)
        .task { await viewModel.start() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                viewModel.resumeConnection()
            }
        }
        .onDisappear { viewModel.stop() }
        .alert("채팅 안내", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) { Button("확인") { viewModel.errorMessage = nil } } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }

    private var canSend: Bool {
        viewModel.isConnected && !viewModel.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var chatRenderItems: [ChatRenderItem] {
        buildChatRenderItems(from: viewModel.messages)
    }

    @ViewBuilder
    private func chatRenderItem(_ item: ChatRenderItem) -> some View {
        switch item {
        case let .message(message):
            ChatBubble(
                message: message,
                mine: message.senderId == viewModel.memberID,
                unreadCount: viewModel.unreadCount(for: message)
            )

        case let .systemGroup(key, messages):
            SystemMessageGroupView(
                messages: messages,
                isExpanded: expandedSystemGroups.contains(key),
                onToggle: {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        if expandedSystemGroups.contains(key) {
                            expandedSystemGroups.remove(key)
                        } else {
                            expandedSystemGroups.insert(key)
                        }
                    }
                }
            )
        }
    }
}

private let systemMessageGroupGap: TimeInterval = 5
private let systemMessageGroupMinimumSize = 3

private enum ChatRenderItem: Identifiable {
    case message(ChatMessage)
    case systemGroup(key: String, messages: [ChatMessage])

    var id: String {
        switch self {
        case let .message(message):
            "message-\(message.id)"
        case let .systemGroup(key, _):
            key
        }
    }

    var scrollTargetID: Int64 {
        switch self {
        case let .message(message):
            message.id
        case let .systemGroup(_, messages):
            messages.last?.id ?? 0
        }
    }
}

private func buildChatRenderItems(from messages: [ChatMessage]) -> [ChatRenderItem] {
    let systemMessages = messages.filter(\.isSystem)
    var clusters: [[ChatMessage]] = []

    for message in systemMessages {
        guard let previous = clusters.last?.last,
              let previousDate = LocalDateTimeFormatter.date(from: previous.createdAt),
              let currentDate = LocalDateTimeFormatter.date(from: message.createdAt),
              currentDate.timeIntervalSince(previousDate) <= systemMessageGroupGap else {
            clusters.append([message])
            continue
        }
        clusters[clusters.count - 1].append(message)
    }

    var clusterByMessageID: [Int64: [ChatMessage]] = [:]
    for cluster in clusters {
        for message in cluster {
            clusterByMessageID[message.id] = cluster
        }
    }

    return messages.compactMap { message in
        guard message.isSystem,
              let cluster = clusterByMessageID[message.id] else {
            return .message(message)
        }
        guard cluster.count >= systemMessageGroupMinimumSize else {
            return .message(message)
        }
        guard cluster.last?.id == message.id,
              let firstID = cluster.first?.id,
              let lastID = cluster.last?.id else {
            return nil
        }
        return .systemGroup(key: "system-group-\(firstID)-\(lastID)", messages: cluster)
    }
}

private struct SystemMessageGroupView: View {
    let messages: [ChatMessage]
    let isExpanded: Bool
    let onToggle: () -> Void

    var body: some View {
        VStack(spacing: 5) {
            if isExpanded {
                VStack(spacing: 5) {
                    ForEach(messages) { message in
                        Text(message.content)
                            .frame(maxWidth: .infinity)
                    }
                    Button("접기", action: onToggle)
                        .foregroundStyle(.tertiary)
                        .underline()
                }
                .transition(
                    .asymmetric(
                        insertion: .move(edge: .top).combined(with: .opacity),
                        removal: .move(edge: .top).combined(with: .opacity)
                    )
                )
            } else {
                Button("이벤트 \(messages.count)건 · 자세히 보기", action: onToggle)
                    .foregroundStyle(.secondary)
                    .underline()
                    .transition(.opacity)
            }
        }
        .font(.caption)
        .foregroundStyle(.secondary)
        .multilineTextAlignment(.center)
        .padding(.vertical, 2)
        .clipped()
        .animation(.spring(response: 0.34, dampingFraction: 0.88), value: isExpanded)
    }
}

private struct ChatContentHeightPreferenceKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

private struct ChatBubble: View {
    let message: ChatMessage
    let mine: Bool
    let unreadCount: Int

    var body: some View {
        if message.isSystem {
            Text(message.content)
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
        } else {
            HStack(alignment: .bottom, spacing: 7) {
                if mine { Spacer(minLength: 52) }
                VStack(alignment: mine ? .trailing : .leading, spacing: 4) {
                    if !mine { Text(message.senderName ?? "사용자").font(.caption2).foregroundStyle(.secondary) }
                    Text(message.content)
                        .padding(.horizontal, 13)
                        .padding(.vertical, 9)
                        .foregroundStyle(mine ? .white : .primary)
                        .background(mine ? Color.blue : Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
                    HStack(spacing: 4) {
                        Text(message.timeLabel)
                        if mine, unreadCount > 0 {
                            Text("·")
                            Text("안읽음 \(unreadCount)")
                        }
                    }
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                }
                if !mine { Spacer(minLength: 52) }
            }
        }
    }
}
