import Foundation

@MainActor
final class AuthService {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init() {
        self.apiClient = .shared
    }

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    var hasStoredSession: Bool {
        KeychainStore.read(.accessToken) != nil && KeychainStore.read(.refreshToken) != nil
    }

    func login(email: String, password: String) async throws -> Member {
        let payload = LoginRequest(
            email: email,
            password: password,
            joinCode: nil
        )
        let body = try encoder.encode(payload)
        let (response, httpResponse): (APIResponse<Member>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/auth/login",
            method: "POST",
            body: body
        )

        guard let authorization = httpResponse.value(forHTTPHeaderField: "Authorization") else {
            throw APIError.invalidAuthorizationHeader
        }

        let tokens = try parseTokens(from: authorization)
        KeychainStore.save(tokens.refreshToken, for: .refreshToken)
        KeychainStore.save(tokens.accessToken, for: .accessToken)
        return response.data
    }

    func sendEmailVerificationCode(email: String) async throws {
        let body = try encoder.encode(EmailRequest(email: email))
        let result: (APIResponse<EmptyPayload?>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/auth/check_email",
            method: "POST",
            body: body
        )
        _ = result
    }

    func verifyEmail(email: String, code: String) async throws {
        let body = try encoder.encode(VerifyEmailRequest(email: email, code: code))
        let result: (APIResponse<EmptyPayload?>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/auth/verify_email",
            method: "POST",
            body: body
        )
        _ = result
    }

    func signup(email: String, password: String, name: String) async throws -> Member {
        let body = try encoder.encode(SignupRequest(email: email, password: password, name: name))
        let (response, _): (APIResponse<Member>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/auth/signup",
            method: "POST",
            body: body
        )
        return response.data
    }

    func verifyResetCode(email: String, code: String) async throws -> String {
        let body = try encoder.encode(VerifyResetCodeRequest(email: email, code: code))
        let (response, _): (APIResponse<VerifyResetCodeResponse>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/auth/password-reset/verify-code",
            method: "POST",
            body: body
        )
        return response.data.verificationToken
    }

    func resetPassword(verificationToken: String, newPassword: String) async throws {
        let body = try encoder.encode(
            ApplyPasswordResetRequest(
                verificationToken: verificationToken,
                newPassword: newPassword
            )
        )
        let result: (APIResponse<EmptyPayload?>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/auth/password-reset/apply",
            method: "POST",
            body: body
        )
        _ = result
    }

    func changePassword(currentPassword: String, newPassword: String) async throws {
        let body = try encoder.encode(
            ChangePasswordRequest(currentPassword: currentPassword, newPassword: newPassword)
        )
        let result: (APIResponse<EmptyPayload?>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/auth/password",
            method: "PATCH",
            body: body,
            authenticated: true
        )
        _ = result
    }

    func currentMember() async throws -> Member {
        let (response, _): (APIResponse<Member>, HTTPURLResponse) = try await apiClient.send(
            path: "/api/v1/auth/me",
            authenticated: true
        )
        return response.data
    }

    func logout() async {
        let result: (APIResponse<EmptyPayload?>, HTTPURLResponse)? = try? await apiClient.send(
            path: "/api/v1/auth/logout",
            method: "POST",
            authenticated: true
        )
        _ = result
        clearSession()
    }

    func clearSession() {
        KeychainStore.deleteAllTokens()
    }

    private func parseTokens(from authorization: String) throws -> AuthTokens {
        let parts = authorization.split(separator: " ").map(String.init)
        guard parts.count == 3, parts[0].lowercased() == "bearer" else {
            throw APIError.invalidAuthorizationHeader
        }

        return AuthTokens(refreshToken: parts[1], accessToken: parts[2])
    }
}
