import SwiftUI

struct SettingsView: View {
    @AppStorage("themePreference") private var themePreference = "system"
    @AppStorage("photoDataPreference") private var photoDataPreference = "balanced"
    @AppStorage("dailyReminderEnabled") private var dailyReminderEnabled = false
    @State private var notificationError: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                settingsHeader

                ScrollView {
                    VStack(spacing: 28) {
                        NavigationLink {
                            ThemeSelectionView(selection: $themePreference)
                        } label: {
                            SettingsNavigationCard(
                                title: "화면 모드",
                                value: themeName,
                                systemImage: "iphone",
                                color: .blue
                            )
                        }
                        .buttonStyle(.plain)

                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 14) {
                                SettingsIcon(
                                    systemImage: dailyReminderEnabled ? "bell.badge.fill" : "bell.slash.fill",
                                    color: dailyReminderEnabled ? TripLogPalette.blue : .red
                                )

                                VStack(alignment: .leading, spacing: 3) {
                                    Text("리마인드 알림")
                                        .font(.body.weight(.semibold))
                                    Text(dailyReminderEnabled ? "알림 받는 중" : "알림 꺼짐")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }

                                Spacer(minLength: 12)

                                Toggle(
                                    "리마인드 알림",
                                    isOn: Binding(
                                        get: { dailyReminderEnabled },
                                        set: { value in Task { await setReminder(value) } }
                                    )
                                )
                                .labelsHidden()
                            }
                            .padding(16)
                            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color(uiColor: .separator).opacity(0.35))
                            )

                            if let notificationError {
                                Text(notificationError)
                                    .font(.caption)
                                    .foregroundStyle(.red)
                                    .padding(.horizontal, 4)
                            }
                        }

                        NavigationLink {
                            PhotoDataSelectionView(selection: $photoDataPreference)
                        } label: {
                            SettingsNavigationCard(
                                title: "데이터 절약",
                                value: photoDataModeName,
                                systemImage: "cellularbars",
                                color: .green
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, TripLogDesign.horizontalPadding)
                    .padding(.top, 20)
                    .padding(.bottom, 112)
                }
                .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea(edges: .bottom))
            }
            .tint(TripLogPalette.blue)
            .sensoryFeedback(.selection, trigger: themePreference)
            .sensoryFeedback(.selection, trigger: photoDataPreference)
            .sensoryFeedback(.selection, trigger: dailyReminderEnabled)
        }
    }

    private var settingsHeader: some View {
        HStack {
            Text("설정")
                .font(.system(size: 30, weight: .bold))
            Spacer()
        }
        .padding(.horizontal, TripLogDesign.horizontalPadding)
        .padding(.top, 8)
        .padding(.bottom, 20)
        .background(TripLogPalette.background.ignoresSafeArea(edges: .top))
    }

    private var themeName: String {
        switch themePreference {
        case "light": "라이트 모드"
        case "dark": "다크 모드"
        default: "자동"
        }
    }

    private var photoDataModeName: String {
        switch photoDataPreference {
        case "quality": "품질 우선"
        case "saver": "절약 우선"
        default: "균형 모드"
        }
    }

    private func setReminder(_ enabled: Bool) async {
        notificationError = nil
        do {
            try await ReminderService.setEnabled(enabled)
            dailyReminderEnabled = enabled
        } catch {
            dailyReminderEnabled = false
            notificationError = error.localizedDescription
        }
    }
}

private struct SettingsNavigationCard: View {
    let title: String
    let value: String
    let systemImage: String
    let color: Color

    var body: some View {
        HStack(spacing: 14) {
            SettingsIcon(systemImage: systemImage, color: color)

            Text(title)
                .font(.body.weight(.semibold))

            Spacer(minLength: 12)

            Text(value)
                .foregroundStyle(.secondary)
                .lineLimit(1)

            Image(systemName: "chevron.right")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .padding(16)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(uiColor: .separator).opacity(0.35))
        )
    }
}

private struct SettingsIcon: View {
    let systemImage: String
    let color: Color

    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: 20, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 44, height: 44)
            .background(color.gradient, in: Circle())
            .accessibilityHidden(true)
    }
}

private struct ThemeSelectionView: View {
    @Binding var selection: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            SettingsDetailHeader(title: "화면 모드") { dismiss() }

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                Text("이 기기에서 사용할 테마 선택")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 4)

                SelectionRow(
                    title: "자동",
                    description: "기기의 화면 모드 설정을 따릅니다.",
                    systemImage: "circle.lefthalf.filled",
                    iconColor: .blue,
                    value: "system",
                    selection: $selection
                )

                SelectionRow(
                    title: "라이트 모드",
                    description: "항상 밝은 화면으로 표시합니다.",
                    systemImage: "sun.max.fill",
                    iconColor: .orange,
                    value: "light",
                    selection: $selection
                )

                SelectionRow(
                    title: "다크 모드",
                    description: "항상 어두운 화면으로 표시합니다.",
                    systemImage: "moon.fill",
                    iconColor: .indigo,
                    value: "dark",
                    selection: $selection
                )

                Text("TripLog에서 사용할 화면 모드를 선택합니다.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 4)
                }
                .padding(.horizontal, TripLogDesign.horizontalPadding)
                .padding(.top, 20)
                .padding(.bottom, 32)
            }
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea(edges: .bottom))
        }
        .toolbar(.hidden, for: .navigationBar)
        .tint(TripLogPalette.blue)
    }
}

private struct PhotoDataSelectionView: View {
    @Binding var selection: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            SettingsDetailHeader(title: "데이터 절약") { dismiss() }

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("사진을 불러올 때 사용하는 데이터 사용량 조절")
                    Text("원본 사진 1장당 약 5MB / 일차당 최대 5개 기준")
                    Text("동시에 불러오는 일차 수에 따라 데이터 사용량이 합산됩니다.")
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 4)

                SelectionRow(
                    title: "품질 우선",
                    description: "예상 데이터 사용량 약 4~10MB",
                    systemImage: "sparkles",
                    iconColor: .blue,
                    value: "quality",
                    selection: $selection
                )

                SelectionRow(
                    title: "균형 모드",
                    description: "예상 데이터 사용량 약 0.5~2.5MB",
                    systemImage: "slider.horizontal.3",
                    iconColor: .green,
                    value: "balanced",
                    selection: $selection
                )

                SelectionRow(
                    title: "절약 우선",
                    description: "약 0.5~2.5MB · 확대 시 압축본 사용",
                    systemImage: "leaf.fill",
                    iconColor: savingYellow,
                    value: "saver",
                    selection: $selection
                )

                Text("사진을 터치하여 확장할 때 사용되는 데이터는 포함하지 않습니다.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 4)
                }
                .padding(.horizontal, TripLogDesign.horizontalPadding)
                .padding(.top, 20)
                .padding(.bottom, 32)
            }
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea(edges: .bottom))
        }
        .toolbar(.hidden, for: .navigationBar)
        .tint(TripLogPalette.blue)
    }

    private var savingYellow: Color {
        Color(red: 250 / 255, green: 204 / 255, blue: 21 / 255)
    }
}

private struct SettingsDetailHeader: View {
    let title: String
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            TripLogBackButton(action: onBack)
            Text(title)
                .font(.headline.bold())
            Spacer()
        }
        .padding(.horizontal, TripLogDesign.horizontalPadding)
        .padding(.top, 8)
        .padding(.bottom, 14)
        .background(TripLogPalette.background.ignoresSafeArea(edges: .top))
        .overlay(alignment: .bottom) {
            Divider()
        }
    }
}

private struct SelectionRow: View {
    let title: String
    let description: String
    let systemImage: String
    let iconColor: Color
    let value: String
    @Binding var selection: String

    var body: some View {
        let isSelected = selection == value

        Button {
            selection = value
        } label: {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(isSelected ? selectedIconForeground : Color.secondary)
                    .frame(width: 44, height: 44)
                    .background(
                        isSelected ? iconColor : Color(uiColor: .tertiarySystemFill),
                        in: Circle()
                    )

                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.subheadline.bold())
                        .foregroundStyle(isSelected ? selectedTitleColor : Color.primary)

                    Text(description)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer(minLength: 12)

                ZStack {
                    Circle()
                        .fill(isSelected ? iconColor : Color.clear)
                    Circle()
                        .stroke(isSelected ? iconColor : Color(uiColor: .separator), lineWidth: 1.5)

                    if isSelected {
                        Image(systemName: "checkmark")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(selectedIconForeground)
                    }
                }
                .frame(width: 20, height: 20)
            }
            .padding(16)
            .background(
                isSelected ? iconColor.opacity(0.10) : Color(uiColor: .secondarySystemGroupedBackground),
                in: RoundedRectangle(cornerRadius: 16)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(
                        isSelected ? iconColor.opacity(0.45) : Color(uiColor: .separator).opacity(0.35),
                        lineWidth: isSelected ? 1.5 : 1
                    )
            )
            .contentShape(RoundedRectangle(cornerRadius: 16))
            .glassEffect(
                .regular.interactive(),
                in: RoundedRectangle(cornerRadius: 16, style: .continuous)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var selectedIconForeground: Color {
        value == "saver" ? .black : .white
    }

    private var selectedTitleColor: Color {
        value == "saver" ? .orange : iconColor
    }
}
