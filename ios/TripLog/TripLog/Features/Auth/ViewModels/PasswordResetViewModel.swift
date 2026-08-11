import Combine
import Foundation

enum PasswordResetStage {
    case verify
    case reset
    case done
}

@MainActor
final class PasswordResetViewModel: ObservableObject {
    @Published var stage: PasswordResetStage = .verify
    @Published var email = ""
    @Published var recoveryCode = ""
    @Published var newPassword = ""
    @Published var passwordConfirmation = ""
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let authService = AuthService()
    private var verificationToken = ""

    func verify() async {
        guard !email.isEmpty, !recoveryCode.isEmpty else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        let normalizedCode = recoveryCode
            .uppercased()
            .filter { $0.isLetter || $0.isNumber }

        do {
            verificationToken = try await authService.verifyResetCode(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                code: normalizedCode
            )
            stage = .reset
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func apply() async {
        guard newPassword.count >= 8 else {
            errorMessage = "비밀번호는 8자 이상이어야 합니다."
            return
        }
        guard newPassword == passwordConfirmation else {
            errorMessage = "비밀번호가 일치하지 않아요."
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            try await authService.resetPassword(
                verificationToken: verificationToken,
                newPassword: newPassword
            )
            stage = .done
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
