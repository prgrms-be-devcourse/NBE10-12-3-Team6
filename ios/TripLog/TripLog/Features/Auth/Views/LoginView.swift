import SwiftUI

struct LoginView: View {
    @ObservedObject var viewModel: AuthViewModel
    let onForgotPassword: () -> Void
    let onSignup: () -> Void
    @FocusState private var focusedField: Field?

    private enum Field {
        case email
        case password
    }

    var body: some View {
        VStack(spacing: 0) {
            header

            ScrollView {
                VStack(spacing: 22) {
                    inputFields
                    statusMessage
                    loginButton

                    Button("비밀번호를 잊으셨나요?", action: onForgotPassword)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .underline()

                    Button("계정이 없으신가요? 회원가입", action: onSignup)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .underline()
                }
                .padding(.horizontal, 24)
                .padding(.top, 30)
                .padding(.bottom, 40)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea(edges: .bottom))
        }
        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
        .onAppear { focusedField = .email }
    }

    private var header: some View {
        TripLogFormHeader("로그인", onBack: viewModel.showLanding)
    }

    private var inputFields: some View {
        VStack(spacing: 20) {
            VStack(alignment: .leading, spacing: 8) {
                Text("이메일")
                    .font(.subheadline.weight(.semibold))

                TextField("이메일을 입력해주세요", text: $viewModel.email)
                    .keyboardType(.emailAddress)
                    .textContentType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.next)
                    .focused($focusedField, equals: .email)
                    .onSubmit { focusedField = .password }
                    .tripLogInputStyle(isFocused: focusedField == .email)
            }

            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("비밀번호")
                        .font(.subheadline.weight(.semibold))

                    Spacer()

                    if let attempts = viewModel.remainingAttempts,
                       attempts > 0,
                       viewModel.lockoutSeconds == 0 {
                        Text("남은 시도 \(attempts)회 / 총 5회")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                SecureField("비밀번호를 입력해주세요", text: $viewModel.password)
                    .textContentType(.password)
                    .submitLabel(.go)
                    .focused($focusedField, equals: .password)
                    .onSubmit {
                        guard viewModel.canLogin else { return }
                        Task { await viewModel.login() }
                    }
                    .tripLogInputStyle(isFocused: focusedField == .password)
            }
        }
    }

    @ViewBuilder
    private var statusMessage: some View {
        if viewModel.lockoutSeconds > 0 {
            Text("계정이 잠겼어요. \(viewModel.lockoutSeconds)초 후 다시 시도해 주세요.")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.red)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else if let errorMessage = viewModel.errorMessage {
            Text(errorMessage)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.red)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var loginButton: some View {
        Button {
            focusedField = nil
            Task { await viewModel.login() }
        } label: {
            Group {
                if viewModel.isLoading {
                    TripLogLoadingIndicator(color: .white)
                } else {
                    Text("로그인하기")
                        .fontWeight(.semibold)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 54)
        }
        .buttonStyle(.glassProminent)
        .buttonBorderShape(.roundedRectangle(radius: 16))
        .tint(TripLogPalette.blue)
        .disabled(!viewModel.canLogin)
    }
}

#Preview {
    LoginView(viewModel: AuthViewModel(), onForgotPassword: {}, onSignup: {})
}
