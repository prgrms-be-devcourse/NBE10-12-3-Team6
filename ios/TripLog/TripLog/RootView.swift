import SwiftUI

struct RootView: View {
    @StateObject private var authViewModel = AuthViewModel()

    var body: some View {
        Group {
            if authViewModel.isRestoringSession {
                TripLogLoadingIndicator("로그인 상태 확인 중", size: 24)
            } else {
                switch authViewModel.route {
                case .landing:
                    AuthLandingView(
                        errorMessage: authViewModel.errorMessage,
                        onLogin: authViewModel.showLogin,
                        onSignup: authViewModel.showSignup
                    )
                    .transition(.move(edge: .leading).combined(with: .opacity))

                case .login:
                    LoginView(
                        viewModel: authViewModel,
                        onForgotPassword: authViewModel.showForgotPassword,
                        onSignup: authViewModel.showSignup
                    )
                        .transition(.move(edge: .trailing).combined(with: .opacity))

                case .signup:
                    SignupView(
                        onBack: authViewModel.showLanding,
                        onCompleted: authViewModel.showRecoveryCode
                    )
                    .transition(.move(edge: .trailing).combined(with: .opacity))

                case .recoveryCode:
                    RecoveryCodeView(
                        code: authViewModel.signupRecoveryCode ?? "",
                        onDone: authViewModel.finishRecoveryCode
                    )
                    .transition(.opacity)

                case .forgotPassword:
                    PasswordResetView(onBackToLogin: authViewModel.showLogin)
                        .transition(.move(edge: .trailing).combined(with: .opacity))

                case .welcome:
                    WelcomeView()
                        .transition(.opacity)

                case .authenticated:
                    MainTabView(
                        member: authViewModel.member,
                        onSignOut: {
                            Task { await authViewModel.signOut() }
                        }
                    )
                    .transition(.opacity)
                }
            }
        }
        .animation(.easeInOut(duration: 0.3), value: authViewModel.route)
        .preferredColorScheme(preferredColorScheme)
        .task {
            await authViewModel.restoreSession()
        }
    }

    @AppStorage("themePreference") private var themePreference = "system"

    private var preferredColorScheme: ColorScheme? {
        switch themePreference {
        case "light": .light
        case "dark": .dark
        default: nil
        }
    }
}

#Preview {
    RootView()
}
