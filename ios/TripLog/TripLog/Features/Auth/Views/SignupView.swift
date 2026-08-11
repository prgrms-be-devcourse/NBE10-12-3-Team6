import SwiftUI

struct SignupView: View {
    @StateObject private var viewModel = SignupViewModel()
    let onBack: () -> Void
    let onCompleted: (String) -> Void

    var body: some View {
        VStack(spacing: 0) {
            TripLogFormHeader("회원가입", onBack: onBack)

            ScrollView {
                VStack(spacing: 18) {
                    emailSection

                    labeledField("이름") {
                        TextField("사용할 이름을 입력해주세요", text: $viewModel.name)
                            .textContentType(.name)
                            .tripLogAuthInputStyle()
                    }

                    labeledField("비밀번호") {
                        SecureField("8자 이상 입력해주세요", text: $viewModel.password)
                            .textContentType(.newPassword)
                            .tripLogAuthInputStyle()
                    }

                    labeledField("비밀번호 확인") {
                        SecureField("비밀번호를 다시 입력해주세요", text: $viewModel.passwordConfirmation)
                            .textContentType(.newPassword)
                            .tripLogAuthInputStyle()
                    }

                    if let errorMessage = viewModel.errorMessage {
                        Text(errorMessage)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.red)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    Button {
                        Task {
                            if let code = await viewModel.signup() {
                                onCompleted(code)
                            }
                        }
                    } label: {
                        if viewModel.isLoading {
                            TripLogLoadingIndicator(color: .white)
                        } else {
                            Text("회원가입")
                        }
                    }
                    .buttonStyle(TripLogPrimaryButtonStyle())
                    .disabled(!viewModel.canSignup)
                    .opacity(viewModel.canSignup ? 1 : 0.4)
                }
                .padding(.horizontal, 24)
                .padding(.top, 28)
                .padding(.bottom, 40)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea(edges: .bottom))
        }
        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
    }

    private var emailSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("이메일").font(.subheadline.weight(.semibold))

            HStack(spacing: 8) {
                TextField("이메일을 입력해주세요", text: $viewModel.email)
                    .keyboardType(.emailAddress)
                    .textContentType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .tripLogAuthInputStyle()
                    .disabled(viewModel.isEmailVerified)

                Button(viewModel.isCodeSent ? "재전송" : "인증") {
                    Task { await viewModel.sendCode() }
                }
                .buttonStyle(.glassProminent)
                .tint(TripLogPalette.blue)
                .controlSize(.large)
                .disabled(!viewModel.canSendCode)
            }

            if viewModel.isCodeSent && !viewModel.isEmailVerified {
                HStack(spacing: 8) {
                    TextField("인증 코드 6자리", text: $viewModel.verificationCode)
                        .keyboardType(.numberPad)
                        .textContentType(.oneTimeCode)
                        .tripLogAuthInputStyle()

                    Button("확인") {
                        Task { await viewModel.verifyCode() }
                    }
                    .buttonStyle(.glassProminent)
                    .tint(TripLogPalette.blue)
                    .controlSize(.large)
                    .disabled(!viewModel.canVerify)
                }

                Text("남은 시간 \(timeText(viewModel.secondsRemaining))")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }

            if viewModel.isEmailVerified {
                Label("이메일 인증 완료", systemImage: "checkmark.circle.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.green)
            }
        }
    }

    private func labeledField<Content: View>(
        _ title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.subheadline.weight(.semibold))
            content()
        }
    }

    private func timeText(_ seconds: Int) -> String {
        String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }
}
