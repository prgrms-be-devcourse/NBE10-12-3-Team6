import SwiftUI

struct MainTabView: View {
    let member: Member?
    let onSignOut: () -> Void
    @State private var selection = MainSection.home
    @State private var hidesBottomNavigation = false
    @StateObject private var chatNotificationCoordinator = ChatNotificationCoordinator()

    var body: some View {
        ZStack {
            HomeView(member: member, hidesBottomNavigation: $hidesBottomNavigation)
                .tabLayer(isSelected: selection == .home)

            SettingsView()
                .tabLayer(isSelected: selection == .settings)

            AccountView(
                member: member,
                onSignOut: onSignOut,
                hidesBottomNavigation: $hidesBottomNavigation
            )
            .tabLayer(isSelected: selection == .account)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if !hidesBottomNavigation {
                MainBottomNavigation(selection: $selection)
                    .padding(.horizontal, 20)
                    .padding(.top, 6)
                    .padding(.bottom, 4)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.24), value: hidesBottomNavigation)
        .tint(TripLogPalette.blue)
        .environmentObject(chatNotificationCoordinator)
        .sensoryFeedback(.selection, trigger: selection)
        .task(id: member?.id) {
            ChatUnreadStore.shared.configure(memberID: member?.id)
            await chatNotificationCoordinator.start(memberID: member?.id)
            await ChatUnreadStore.shared.refresh()
        }
        .onReceive(NotificationCenter.default.publisher(for: .openTripChatFromNotification)) { _ in
            selection = .home
        }
        .onDisappear {
            chatNotificationCoordinator.stop()
            ChatUnreadStore.shared.configure(memberID: nil)
        }
    }
}

private extension View {
    func tabLayer(isSelected: Bool) -> some View {
        self
            .opacity(isSelected ? 1 : 0)
            .allowsHitTesting(isSelected)
            .accessibilityHidden(!isSelected)
            .zIndex(isSelected ? 1 : 0)
    }
}

private struct MainBottomNavigation: View {
    @Binding var selection: MainSection

    var body: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 12) {
                HStack(spacing: 2) {
                    tabButton(for: .home)
                    tabButton(for: .settings)
                }
                .padding(4)
                .glassEffect(.regular, in: Capsule())

                accountButton
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func tabButton(for section: MainSection) -> some View {
        Button {
            select(section)
        } label: {
            VStack(spacing: 2) {
                Image(systemName: section.icon)
                    .font(.system(size: 19, weight: .semibold))

                Text(section.title)
                    .font(.caption2.weight(.semibold))
            }
            .foregroundStyle(selection == section ? TripLogPalette.blue : .secondary)
            .frame(width: 70, height: 48)
            .background {
                if selection == section {
                    Capsule()
                        .fill(TripLogPalette.blue.opacity(0.14))
                }
            }
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(section.title)
        .accessibilityAddTraits(selection == section ? .isSelected : [])
    }

    private var accountButton: some View {
        Button {
            select(.account)
        } label: {
            VStack(spacing: 2) {
                Image(systemName: MainSection.account.icon)
                    .font(.system(size: 19, weight: .semibold))

                Text(MainSection.account.title)
                    .font(.caption2.weight(.semibold))
            }
            .foregroundStyle(selection == .account ? TripLogPalette.blue : .secondary)
            .frame(width: 56, height: 56)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .glassEffect(
            .regular
                .tint(selection == .account ? TripLogPalette.blue.opacity(0.14) : .clear)
                .interactive(),
            in: Circle()
        )
        .accessibilityLabel(MainSection.account.title)
        .accessibilityAddTraits(selection == .account ? .isSelected : [])
    }

    private func select(_ section: MainSection) {
        guard selection != section else { return }
        withAnimation(.easeInOut(duration: 0.2)) {
            selection = section
        }
    }
}

private enum MainSection: Hashable {
    case home, settings, account

    var title: String {
        switch self {
        case .home: "홈"
        case .settings: "설정"
        case .account: "계정"
        }
    }

    var icon: String {
        switch self {
        case .home: "house.fill"
        case .settings: "gearshape.fill"
        case .account: "person.fill"
        }
    }
}
