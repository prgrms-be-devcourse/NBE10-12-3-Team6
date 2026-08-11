import Combine
import Foundation

@MainActor
final class SignupViewModel: ObservableObject {
    @Published var email = ""
    @Published var verificationCode = ""
    @Published var name = ""
    @Published var password = ""
    @Published var passwordConfirmation = ""
    @Published var isCodeSent = false
    @Published var isEmailVerified = false
    @Published var secondsRemaining = 0
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let authService = AuthService()
    private var timerTask: Task<Void, Never>?

    var canSendCode: Bool {
        email.contains("@") && !isLoading && !isEmailVerified
    }

    var canVerify: Bool {
        verificationCode.count == 6 && secondsRemaining > 0 && !isLoading
    }

    var canSignup: Bool {
        isEmailVerified
            && !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && password.count >= 8
            && password == passwordConfirmation
            && !isLoading
    }

    func sendCode() async {
        guard canSendCode else { return }
        await perform {
            try await authService.sendEmailVerificationCode(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            isCodeSent = true
            verificationCode = ""
            startTimer(seconds: 300)
        }
    }

    func verifyCode() async {
        guard canVerify else { return }
        await perform {
            try await authService.verifyEmail(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                code: verificationCode
            )
            isEmailVerified = true
            timerTask?.cancel()
            secondsRemaining = 0
        }
    }

    func signup() async -> String? {
        guard canSignup else {
            if password != passwordConfirmation {
                errorMessage = "비밀번호가 일치하지 않아요."
            }
            return nil
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let member = try await authService.signup(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password,
                name: name.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            return member.recoveryCode
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    private func perform(_ operation: () async throws -> Void) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            try await operation()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func startTimer(seconds: Int) {
        timerTask?.cancel()
        secondsRemaining = seconds
        timerTask = Task { [weak self] in
            while let self, self.secondsRemaining > 0, !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                guard !Task.isCancelled else { return }
                self.secondsRemaining -= 1
            }
        }
    }
}
