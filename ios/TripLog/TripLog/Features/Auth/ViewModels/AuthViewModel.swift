import Combine
import Foundation

@MainActor
final class AuthViewModel: ObservableObject {
    @Published var route: AuthRoute = .landing
    @Published var member: Member?
    @Published var email = ""
    @Published var password = ""
    @Published var errorMessage: String?
    @Published var remainingAttempts: Int?
    @Published var lockoutSeconds = 0
    @Published var isLoading = false
    @Published var isRestoringSession = true
    @Published var signupRecoveryCode: String?

    private let authService: AuthService
    private var lockoutTask: Task<Void, Never>?

    init() {
        self.authService = AuthService()
    }

    init(authService: AuthService) {
        self.authService = authService
    }

    var canLogin: Bool {
        !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !password.isEmpty
            && !isLoading
            && lockoutSeconds == 0
    }

    func restoreSession() async {
        defer { isRestoringSession = false }
        guard authService.hasStoredSession else {
            route = .landing
            return
        }

        do {
            member = try await authService.currentMember()
            route = .authenticated
        } catch APIError.server(let statusCode, _, _, _) where statusCode == 401 {
            authService.clearSession()
            route = .landing
        } catch {
            // 네트워크가 잠시 끊긴 경우 저장된 로그인 정보를 임의로 삭제하지 않습니다.
            route = .landing
            errorMessage = "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."
        }
    }

    func showLogin() {
        errorMessage = nil
        route = .login
    }

    func showSignup() {
        errorMessage = nil
        route = .signup
    }

    func showForgotPassword() {
        errorMessage = nil
        route = .forgotPassword
    }

    func showRecoveryCode(_ code: String) {
        signupRecoveryCode = code
        route = .recoveryCode
    }

    func finishRecoveryCode() {
        signupRecoveryCode = nil
        route = .login
    }

    func showLanding() {
        errorMessage = nil
        password = ""
        route = .landing
    }

    func login() async {
        guard canLogin else { return }

        isLoading = true
        errorMessage = nil
        remainingAttempts = nil

        do {
            member = try await authService.login(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
            password = ""
            route = .welcome
            try? await Task.sleep(for: .seconds(1.8))
            route = .authenticated
        } catch APIError.server(_, let message, let attempts, let retryAfterSeconds) {
            remainingAttempts = attempts
            errorMessage = message
            if let retryAfterSeconds, retryAfterSeconds > 0 {
                beginLockout(seconds: retryAfterSeconds)
            }
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    func signOut() async {
        await authService.logout()
        member = nil
        email = ""
        password = ""
        route = .landing
    }

    private func beginLockout(seconds: Int) {
        lockoutTask?.cancel()
        lockoutSeconds = seconds
        errorMessage = nil

        lockoutTask = Task { [weak self] in
            while let self, self.lockoutSeconds > 0, !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                guard !Task.isCancelled else { return }
                self.lockoutSeconds -= 1
            }
        }
    }
}
