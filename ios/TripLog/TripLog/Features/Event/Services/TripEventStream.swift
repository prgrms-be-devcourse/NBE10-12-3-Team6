import Foundation
import OSLog

@MainActor
final class TripEventStream {
    var onEvent: ((TripEventPayload) -> Void)?
    var onStateChange: ((Bool) -> Void)?
    var onTerminalError: ((String) -> Void)?

    private var streamTask: Task<Void, Never>?
    private var activeTripID: Int64?
    private let decoder = JSONDecoder()
    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "TripLog",
        category: "TripSSE"
    )
    private lazy var session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.timeoutIntervalForRequest = 3_700
        configuration.timeoutIntervalForResource = 3_700
        configuration.waitsForConnectivity = true
        configuration.httpShouldSetCookies = true
        configuration.httpCookieStorage = .shared
        return URLSession(configuration: configuration)
    }()

    deinit {
        streamTask?.cancel()
    }

    func connect(tripID: Int64, forceReconnect: Bool = false) {
        guard forceReconnect || activeTripID != tripID || streamTask == nil else { return }
        disconnect()
        activeTripID = tripID
        logger.info("SSE 구독 시작: tripID=\(tripID, privacy: .public)")
        streamTask = Task { [weak self] in
            await self?.run(tripID: tripID)
        }
    }

    func disconnect() {
        if let activeTripID {
            logger.info("SSE 구독 종료: tripID=\(activeTripID, privacy: .public)")
        }
        activeTripID = nil
        streamTask?.cancel()
        streamTask = nil
        onStateChange?(false)
    }

    private func run(tripID: Int64) async {
        var retryDelay: UInt64 = 1
        while !Task.isCancelled {
            do {
                try await open(tripID: tripID)
                retryDelay = 1
            } catch APIError.server(let status, let message, _, _) where status == 403 || status == 404 {
                logger.error("SSE 구독 종료 응답: status=\(status, privacy: .public)")
                onTerminalError?(message)
                return
            } catch is CancellationError {
                return
            } catch {
                logger.error("SSE 재연결 대기: \(String(describing: error), privacy: .public)")
                onStateChange?(false)
            }

            guard !Task.isCancelled else { return }
            try? await Task.sleep(for: .seconds(retryDelay))
            retryDelay = min(30, retryDelay * 2)
        }
    }

    private func open(tripID: Int64) async throws {
        guard let url = URL(
            string: "/api/v1/trips/\(tripID)/events/subscribe",
            relativeTo: AppConfiguration.apiBaseURL
        )?.absoluteURL else { throw APIError.invalidResponse }
        var request = URLRequest(url: url)
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        request.timeoutInterval = 3_700
        if let refresh = KeychainStore.read(.refreshToken),
           let access = KeychainStore.read(.accessToken) {
            request.setValue("Bearer \(refresh) \(access)", forHTTPHeaderField: "Authorization")
        }

        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
        persistRotatedTokens(from: http)
        guard (200..<300).contains(http.statusCode) else {
            throw APIError.server(
                statusCode: http.statusCode,
                message: http.statusCode == 404 ? "존재하지 않는 여행방입니다." : "여행방 알림에 접근할 수 없습니다.",
                remainingAttempts: nil,
                retryAfterSeconds: nil
            )
        }
        logger.info("SSE 연결 완료: tripID=\(tripID, privacy: .public), status=\(http.statusCode, privacy: .public)")
        onStateChange?(true)

        var eventName = ""
        var dataLines: [String] = []
        var lineBytes: [UInt8] = []

        for try await byte in bytes {
            try Task.checkCancellation()
            guard byte == 0x0A else {
                lineBytes.append(byte)
                continue
            }

            consume(
                lineBytes: &lineBytes,
                eventName: &eventName,
                dataLines: &dataLines
            )
        }

        if !lineBytes.isEmpty {
            consume(
                lineBytes: &lineBytes,
                eventName: &eventName,
                dataLines: &dataLines
            )
        }
        if !eventName.isEmpty || !dataLines.isEmpty {
            dispatch(eventName: eventName, data: dataLines.joined(separator: "\n"))
        }
        onStateChange?(false)
    }

    private func consume(
        lineBytes: inout [UInt8],
        eventName: inout String,
        dataLines: inout [String]
    ) {
        if lineBytes.last == 0x0D {
            lineBytes.removeLast()
        }

        let line = String(decoding: lineBytes, as: UTF8.self)
        lineBytes.removeAll(keepingCapacity: true)

        if line.isEmpty {
            dispatch(eventName: eventName, data: dataLines.joined(separator: "\n"))
            eventName = ""
            dataLines.removeAll(keepingCapacity: true)
            return
        }

        if line.hasPrefix("event:") {
            eventName = sseFieldValue(String(line.dropFirst(6)))
        } else if line.hasPrefix("data:") {
            dataLines.append(sseFieldValue(String(line.dropFirst(5))))
        }
    }

    private func sseFieldValue(_ value: String) -> String {
        value.first == " " ? String(value.dropFirst()) : value
    }

    private func persistRotatedTokens(from response: HTTPURLResponse) {
        guard let authorization = response.value(forHTTPHeaderField: "Authorization") else { return }
        let parts = authorization.split(separator: " ").map(String.init)
        guard parts.count == 3, parts[0].lowercased() == "bearer" else { return }
        KeychainStore.save(parts[1], for: .refreshToken)
        KeychainStore.save(parts[2], for: .accessToken)
    }

    private func dispatch(eventName: String, data: String) {
        let normalizedEventName = eventName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedEventName.isEmpty else { return }

        logger.debug(
            "SSE 프레임 수신: event=\(normalizedEventName, privacy: .public), bytes=\(data.utf8.count, privacy: .public)"
        )

        guard normalizedEventName == "TRIP_EVENT", let payload = data.data(using: .utf8) else { return }
        do {
            let event = try decoder.decode(TripEventPayload.self, from: payload)
            logger.info(
                "SSE 변경 이벤트 전달: type=\(event.eventType, privacy: .public), tripID=\(event.tripGroupId, privacy: .public)"
            )
            onEvent?(event)
        } catch {
            logger.error("SSE 이벤트 해석 실패: \(String(describing: error), privacy: .public)")
        }
    }
}
