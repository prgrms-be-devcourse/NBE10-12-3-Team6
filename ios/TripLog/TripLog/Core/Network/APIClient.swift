import Foundation

@MainActor
final class APIClient {
    static let shared = APIClient()

    private let session: URLSession
    private let decoder = JSONDecoder()

    private init() {
        let configuration = URLSessionConfiguration.default
        configuration.httpShouldSetCookies = true
        configuration.httpCookieStorage = .shared
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 30
        session = URLSession(configuration: configuration)
    }

    func send<Response: Decodable>(
        path: String,
        method: String = "GET",
        body: Data? = nil,
        authenticated: Bool = false
    ) async throws -> (Response, HTTPURLResponse) {
        let (data, httpResponse) = try await sendRaw(
            path: path,
            method: method,
            body: body,
            contentType: body == nil ? nil : "application/json",
            authenticated: authenticated
        )

        return (try decoder.decode(Response.self, from: data), httpResponse)
    }

    func sendDirect<Response: Decodable>(
        path: String,
        method: String = "GET",
        body: Data? = nil,
        contentType: String? = nil,
        authenticated: Bool = true
    ) async throws -> (Response, HTTPURLResponse) {
        let (data, response) = try await sendRaw(
            path: path,
            method: method,
            body: body,
            contentType: contentType ?? (body == nil ? nil : "application/json"),
            authenticated: authenticated
        )
        return (try decoder.decode(Response.self, from: data), response)
    }

    func sendDirectWithUploadProgress<Response: Decodable>(
        path: String,
        method: String = "POST",
        body: Data,
        contentType: String,
        authenticated: Bool = true,
        onProgress: @escaping @MainActor @Sendable (Double) -> Void
    ) async throws -> (Response, HTTPURLResponse) {
        let (data, response) = try await sendRawWithUploadProgress(
            path: path,
            method: method,
            body: body,
            contentType: contentType,
            authenticated: authenticated,
            onProgress: onProgress
        )
        return (try decoder.decode(Response.self, from: data), response)
    }

    func sendRaw(
        path: String,
        method: String = "GET",
        body: Data? = nil,
        contentType: String? = nil,
        authenticated: Bool = true
    ) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: path, relativeTo: AppConfiguration.apiBaseURL)?.absoluteURL else {
            throw APIError.invalidResponse
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if let contentType {
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        }

        if authenticated,
           let refreshToken = KeychainStore.read(.refreshToken),
           let accessToken = KeychainStore.read(.accessToken) {
            request.setValue(
                "Bearer \(refreshToken) \(accessToken)",
                forHTTPHeaderField: "Authorization"
            )
        }

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        persistRotatedTokens(from: httpResponse)

        guard (200..<300).contains(httpResponse.statusCode) else {
            let error = try? decoder.decode(APIErrorResponse.self, from: data)
            throw APIError.server(
                statusCode: httpResponse.statusCode,
                message: error?.message ?? "요청을 처리하지 못했습니다.",
                remainingAttempts: error?.remainingAttempts,
                retryAfterSeconds: error?.retryAfterSeconds
            )
        }

        return (data, httpResponse)
    }

    private func sendRawWithUploadProgress(
        path: String,
        method: String,
        body: Data,
        contentType: String,
        authenticated: Bool,
        onProgress: @escaping @MainActor @Sendable (Double) -> Void
    ) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: path, relativeTo: AppConfiguration.apiBaseURL)?.absoluteURL else {
            throw APIError.invalidResponse
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")

        if authenticated,
           let refreshToken = KeychainStore.read(.refreshToken),
           let accessToken = KeychainStore.read(.accessToken) {
            request.setValue(
                "Bearer \(refreshToken) \(accessToken)",
                forHTTPHeaderField: "Authorization"
            )
        }

        let progressDelegate = UploadProgressDelegate(onProgress: onProgress)
        let (data, response) = try await session.upload(
            for: request,
            from: body,
            delegate: progressDelegate
        )

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        persistRotatedTokens(from: httpResponse)

        guard (200..<300).contains(httpResponse.statusCode) else {
            let error = try? decoder.decode(APIErrorResponse.self, from: data)
            throw APIError.server(
                statusCode: httpResponse.statusCode,
                message: error?.message ?? "요청을 처리하지 못했습니다.",
                remainingAttempts: error?.remainingAttempts,
                retryAfterSeconds: error?.retryAfterSeconds
            )
        }

        onProgress(1)
        return (data, httpResponse)
    }

    private func persistRotatedTokens(from response: HTTPURLResponse) {
        guard let authorization = response.value(forHTTPHeaderField: "Authorization") else { return }
        let parts = authorization.split(separator: " ").map(String.init)
        guard parts.count == 3, parts[0].lowercased() == "bearer" else { return }
        KeychainStore.save(parts[1], for: .refreshToken)
        KeychainStore.save(parts[2], for: .accessToken)
    }
}

private final class UploadProgressDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    private let onProgress: @MainActor @Sendable (Double) -> Void

    init(onProgress: @escaping @MainActor @Sendable (Double) -> Void) {
        self.onProgress = onProgress
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didSendBodyData bytesSent: Int64,
        totalBytesSent: Int64,
        totalBytesExpectedToSend: Int64
    ) {
        guard totalBytesExpectedToSend > 0 else { return }
        let progress = min(max(Double(totalBytesSent) / Double(totalBytesExpectedToSend), 0), 1)
        Task { @MainActor in
            onProgress(progress)
        }
    }
}
