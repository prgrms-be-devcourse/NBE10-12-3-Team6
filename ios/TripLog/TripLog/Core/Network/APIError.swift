import Foundation

enum APIError: LocalizedError {
    case invalidResponse
    case invalidAuthorizationHeader
    case server(
        statusCode: Int,
        message: String,
        remainingAttempts: Int?,
        retryAfterSeconds: Int?
    )

    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "서버 응답을 확인할 수 없습니다."
        case .invalidAuthorizationHeader:
            return "로그인 토큰을 확인할 수 없습니다."
        case let .server(_, message, _, _):
            return message
        }
    }
}

struct APIErrorResponse: Decodable {
    let statusCode: Int?
    let message: String?
    let remainingAttempts: Int?
    let retryAfterSeconds: Int?
}

extension Error {
    var isRequestCancellation: Bool {
        if self is CancellationError || Task.isCancelled {
            return true
        }

        let error = self as NSError
        return error.domain == NSURLErrorDomain && error.code == NSURLErrorCancelled
    }
}
