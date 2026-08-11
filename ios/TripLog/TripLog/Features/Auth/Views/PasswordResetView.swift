import SwiftUI

struct PasswordResetView: View {
    @StateObject private var viewModel = PasswordResetViewModel()
    let onBackToLogin: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            TripLogFormHeader(title, onBack: onBackToLogin)

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    switch viewModel.stage {
                    case .verify:
                        verifyStage
                    case .reset:
                        resetStage
                    case .done:
                        doneStage
                    }
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

    private var title: String {
        switch viewModel.stage {
        case .verify: "비밀번호 재설정"
        case .reset: "새 비밀번호 설정"
        case .done: "비밀번호 변경 완료"
        }
    }

    private var verifyStage: some View {
        Group {
            Text("가입 시 사용한 이메일과 발급받은 본인 확인 코드를 입력해 주세요.")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            labeled("이메일") {
                TextField("이메일을 입력해주세요", text: $viewModel.email)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .tripLogAuthInputStyle()
            }

            labeled("본인 확인 코드") {
                TextField("회원가입 시 발급받은 코드", text: $viewModel.recoveryCode)
                    .textInputAutocapitalization(.characters)
                    .tripLogAuthInputStyle()
            }

            errorText

            Button("다음") { Task { await viewModel.verify() } }
                .buttonStyle(TripLogPrimaryButtonStyle())
                .disabled(viewModel.isLoading)

            Button("로그인으로 돌아가기", action: onBackToLogin)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .underline()
                .frame(maxWidth: .infinity)
        }
    }

    private var resetStage: some View {
        Group {
            Text("사용할 새 비밀번호를 입력해 주세요.")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            labeled("새 비밀번호") {
                SecureField("8자 이상 입력해주세요", text: $viewModel.newPassword)
                    .tripLogAuthInputStyle()
            }

            labeled("새 비밀번호 확인") {
                SecureField("다시 한 번 입력해주세요", text: $viewModel.passwordConfirmation)
                    .tripLogAuthInputStyle()
            }

            errorText

            Button("비밀번호 변경") { Task { await viewModel.apply() } }
                .buttonStyle(TripLogPrimaryButtonStyle())
                .disabled(viewModel.isLoading)
        }
    }

    private var doneStage: some View {
        VStack(alignment: .leading, spacing: 22) {
            Label("비밀번호가 변경됐어요", systemImage: "checkmark.circle.fill")
                .font(.title2.bold())
                .foregroundStyle(.green)

            Text("새 비밀번호로 다시 로그인해 주세요. 보안을 위해 기존 로그인 정보가 모두 만료됩니다.")
                .foregroundStyle(.secondary)

            Button("로그인하러 가기", action: onBackToLogin)
                .buttonStyle(TripLogPrimaryButtonStyle())
        }
    }

    @ViewBuilder
    private var errorText: some View {
        if let error = viewModel.errorMessage {
            Text(error)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.red)
        }
    }

    private func labeled<Content: View>(
        _ title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.subheadline.weight(.semibold))
            content()
        }
    }
}
