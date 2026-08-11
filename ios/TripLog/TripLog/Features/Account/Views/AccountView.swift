import SwiftUI

struct AccountView: View {
    let member: Member?
    let onSignOut: () -> Void
    @Binding var hidesBottomNavigation: Bool
    @State private var confirmLogout = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack {
                    Text("계정")
                        .font(.system(size: 30, weight: .bold))
                    Spacer()
                }
                .padding(.horizontal, TripLogDesign.horizontalPadding)
                .padding(.top, 8)
                .padding(.bottom, 20)
                .background(TripLogPalette.background)

                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("현재 계정")
                            .font(.body.bold())
                            .padding(.bottom, 12)

                        HStack(spacing: 14) {
                            Image(systemName: "person.fill")
                                .font(.title3)
                                .foregroundStyle(TripLogPalette.blue)
                                .frame(width: 48, height: 48)
                                .background(TripLogPalette.blue.opacity(0.12), in: Circle())

                            VStack(alignment: .leading, spacing: 4) {
                                Text(member?.name ?? "사용자").font(.headline)
                                Text(member?.email ?? "").font(.subheadline).foregroundStyle(.secondary)
                            }
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color(uiColor: .separator).opacity(0.35))
                        )

                        NavigationLink {
                            ChangePasswordView()
                                .onAppear { hidesBottomNavigation = true }
                                .onDisappear { hidesBottomNavigation = false }
                        } label: {
                            HStack(spacing: 14) {
                                Image(systemName: "lock")
                                    .frame(width: 40, height: 40)
                                    .background(Color(uiColor: .tertiarySystemFill), in: Circle())
                                Text("비밀번호 변경")
                                    .font(.body.weight(.semibold))
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundStyle(.secondary)
                            }
                            .padding(16)
                            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color(uiColor: .separator).opacity(0.35))
                            )
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 24)

                        VStack(spacing: 0) {
                            Button {
                                guard !confirmLogout else { return }
                                withAnimation(.spring(response: 0.34, dampingFraction: 0.86)) {
                                    confirmLogout = true
                                }
                            } label: {
                                HStack(spacing: 14) {
                                    Image(systemName: "rectangle.portrait.and.arrow.right")
                                        .frame(width: 40, height: 40)
                                        .background(Color.red.opacity(0.10), in: Circle())
                                    Text(confirmLogout ? "정말 로그아웃하시겠습니까?" : "로그아웃")
                                        .font(.body.weight(confirmLogout ? .bold : .semibold))
                                        .transaction { transaction in
                                            transaction.animation = nil
                                        }
                                    Spacer()
                                }
                                .foregroundStyle(.red)
                                .padding(16)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .frame(maxWidth: .infinity)

                            HStack(spacing: 12) {
                                Button {
                                    withAnimation(.spring(response: 0.32, dampingFraction: 0.88)) {
                                        confirmLogout = false
                                    }
                                } label: {
                                    Text("취소")
                                        .font(.body.bold())
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(.glass)
                                .buttonBorderShape(.roundedRectangle(radius: 12))
                                .controlSize(.large)
                                .tint(TripLogPalette.textSoft)
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .overlay {
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(Color.primary.opacity(0.22), lineWidth: 1)
                                }

                                Button(action: onSignOut) {
                                    Text("로그아웃")
                                        .font(.body.bold())
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(.glassProminent)
                                .buttonBorderShape(.roundedRectangle(radius: 12))
                                .controlSize(.large)
                                .tint(.red)
                            }
                            .padding(.horizontal, 16)
                            .frame(height: confirmLogout ? 54 : 0)
                            .padding(.top, confirmLogout ? 8 : 0)
                            .padding(.bottom, confirmLogout ? 16 : 0)
                            .opacity(confirmLogout ? 1 : 0)
                            .offset(y: confirmLogout ? 0 : -8)
                            .clipped()
                            .allowsHitTesting(confirmLogout)
                            .accessibilityHidden(!confirmLogout)
                        }
                        .frame(maxWidth: .infinity)
                        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                        .glassEffect(
                            .regular
                                .tint(Color.red.opacity(0.05))
                                .interactive(),
                            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                        )
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.red.opacity(0.22)))
                        .animation(.spring(response: 0.34, dampingFraction: 0.86), value: confirmLogout)
                        .padding(.top, 12)
                    }
                    .padding(.horizontal, TripLogDesign.horizontalPadding)
                    .padding(.top, 20)
                    .padding(.bottom, 112)
                }
                .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea(edges: .bottom))
            }
            .toolbar(.hidden, for: .navigationBar)
            .tripLogScreenBackground()
        }
    }
}

private struct ChangePasswordView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var currentPassword = ""
    @State private var newPassword = ""
    @State private var confirmation = ""
    @State private var errorMessage: String?
    @State private var isLoading = false
    private let authService = AuthService()

    var body: some View {
        VStack(spacing: 0) {
            TripLogFormHeader("비밀번호 변경") { dismiss() }

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    passwordField("현재 비밀번호", text: $currentPassword, prompt: "현재 비밀번호를 입력해주세요")
                    passwordField("새로운 비밀번호", text: $newPassword, prompt: "새로운 비밀번호를 입력해주세요")

                    if !newPassword.isEmpty && newPassword.count < 8 {
                        Text("비밀번호는 8자 이상이어야 합니다.")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.red)
                            .padding(.top, -16)
                    }

                    passwordField("새로운 비밀번호 확인", text: $confirmation, prompt: "새로운 비밀번호를 다시 입력해주세요")

                    if !confirmation.isEmpty && confirmation != newPassword {
                        Text("새로운 비밀번호가 일치하지 않습니다.")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.red)
                            .padding(.top, -16)
                    }

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.red)
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.red.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                    }

                    Text("비밀번호 변경 시 다른 기기에서 로그아웃됩니다.")
                        .font(.footnote)
                        .foregroundStyle(TripLogPalette.textMuted)
                }
                .padding(.horizontal, TripLogDesign.horizontalPadding)
                .padding(.top, 28)
            }
            .background(Color(uiColor: .systemGroupedBackground))

            Button(isLoading ? "변경 중..." : "비밀번호 변경") {
                Task { await changePassword() }
            }
            .buttonStyle(TripLogPrimaryButtonStyle(color: TripLogPalette.blue))
            .disabled(!canSubmit || isLoading)
            .opacity(canSubmit ? 1 : 0.4)
            .padding(.horizontal, TripLogDesign.horizontalPadding)
            .padding(.top, 12)
            .padding(.bottom, 16)
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea(edges: .bottom))
        }
        .toolbar(.hidden, for: .navigationBar)
        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
    }

    private var canSubmit: Bool {
        !currentPassword.isEmpty && newPassword.count >= 8 && newPassword == confirmation
    }

    private func passwordField(_ title: String, text: Binding<String>, prompt: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.subheadline.bold())
            SecureField(prompt, text: text)
                .textContentType(.password)
                .tripLogAuthInputStyle()
        }
    }

    private func changePassword() async {
        guard newPassword == confirmation else {
            errorMessage = "새 비밀번호가 일치하지 않습니다."
            return
        }
        guard newPassword.count >= 8 else {
            errorMessage = "새 비밀번호는 8자 이상이어야 합니다."
            return
        }
        isLoading = true
        errorMessage = nil
        do {
            try await authService.changePassword(currentPassword: currentPassword, newPassword: newPassword)
            dismiss()
        } catch { errorMessage = error.localizedDescription }
        isLoading = false
    }
}
