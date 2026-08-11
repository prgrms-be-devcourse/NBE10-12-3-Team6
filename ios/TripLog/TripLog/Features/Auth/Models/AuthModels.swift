import Foundation

struct APIResponse<DataType: Decodable>: Decodable {
    let statusCode: Int
    let data: DataType
}

struct LoginRequest: Encodable {
    let email: String
    let password: String
    let joinCode: String?
}

struct Member: Codable, Equatable {
    let id: Int64
    let email: String
    let name: String
    let recoveryCode: String?

    init(id: Int64, email: String, name: String, recoveryCode: String? = nil) {
        self.id = id
        self.email = email
        self.name = name
        self.recoveryCode = recoveryCode
    }
}

struct AuthTokens {
    let refreshToken: String
    let accessToken: String
}

struct EmptyPayload: Decodable {}

enum AuthRoute: Equatable {
    case landing
    case login
    case signup
    case recoveryCode
    case forgotPassword
    case welcome
    case authenticated
}

struct EmailRequest: Encodable {
    let email: String
}

struct VerifyEmailRequest: Encodable {
    let email: String
    let code: String
}

struct SignupRequest: Encodable {
    let email: String
    let password: String
    let name: String
}

struct VerifyResetCodeRequest: Encodable {
    let email: String
    let code: String
}

struct VerifyResetCodeResponse: Decodable {
    let verificationToken: String
}

struct ApplyPasswordResetRequest: Encodable {
    let verificationToken: String
    let newPassword: String
}

struct ChangePasswordRequest: Encodable {
    let currentPassword: String
    let newPassword: String
}
