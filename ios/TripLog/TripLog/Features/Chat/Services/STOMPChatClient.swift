import Foundation

@MainActor
final class STOMPChatClient {
    var onMessage: ((ChatMessage) -> Void)?
    var onReadStatus: ((ChatReadStatus) -> Void)?
    var onSendError: ((String) -> Void)?
    var onConnectionChange: ((Bool, String?) -> Void)?

    private var socket: URLSessionWebSocketTask?
    private var receiveTask: Task<Void, Never>?
    private var heartbeatTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()
    private var tripID: Int64?
    private var connected = false
    private var isConnecting = false
    private var shouldReconnect = false

    func connect(tripID: Int64) {
        shouldReconnect = true
        reconnectTask?.cancel()
        reconnectTask = nil
        closeCurrentConnection(sendDisconnect: true)
        self.tripID = tripID
        openConnection()
    }

    func reconnectImmediatelyIfNeeded() {
        guard shouldReconnect, !connected, !isConnecting else { return }
        reconnectTask?.cancel()
        reconnectTask = nil
        openConnection()
    }

    private func openConnection() {
        guard shouldReconnect, tripID != nil, !connected, !isConnecting else { return }
        guard let url = websocketURL else {
            onConnectionChange?(false, "채팅 주소가 올바르지 않습니다.")
            return
        }
        isConnecting = true

        var request = URLRequest(url: url)
        request.setValue("v12.stomp", forHTTPHeaderField: "Sec-WebSocket-Protocol")
        if let refresh = KeychainStore.read(.refreshToken),
           let access = KeychainStore.read(.accessToken) {
            request.setValue("Bearer \(refresh) \(access)", forHTTPHeaderField: "Authorization")
        }
        let session = URLSession(configuration: .default)
        let task = session.webSocketTask(with: request)
        socket = task
        task.resume()

        receiveTask = Task { [weak self] in await self?.receiveLoop(socket: task) }
        Task { [weak self] in
            do {
                try await self?.sendFrame(
                    "CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\u{0}",
                    over: task
                )
            } catch {
                self?.connectionEnded(socket: task, message: nil)
            }
        }
    }

    func send(content: String) async throws {
        guard connected, let tripID else { throw ChatSocketError.notConnected }
        let payload = try encoder.encode(ChatSendPayload(content: content))
        let body = String(data: payload, encoding: .utf8) ?? "{}"
        let length = payload.count
        let frame = "SEND\ndestination:/pub/trips/\(tripID)/chat\ncontent-type:application/json\ncontent-length:\(length)\n\n\(body)\u{0}"
        try await sendFrame(frame)
    }

    func disconnect() {
        shouldReconnect = false
        reconnectTask?.cancel()
        reconnectTask = nil
        closeCurrentConnection(sendDisconnect: true)
        tripID = nil
    }

    private var websocketURL: URL? {
        guard var components = URLComponents(url: AppConfiguration.apiBaseURL, resolvingAgainstBaseURL: false) else { return nil }
        components.scheme = components.scheme == "https" ? "wss" : "ws"
        components.path = "/ws"
        components.query = nil
        return components.url
    }

    private func receiveLoop(socket: URLSessionWebSocketTask) async {
        do {
            while !Task.isCancelled {
                let message = try await socket.receive()
                let text: String
                switch message {
                case .string(let value): text = value
                case .data(let data): text = String(data: data, encoding: .utf8) ?? ""
                @unknown default: text = ""
                }
                handleFrames(text, from: socket)
            }
        } catch {
            guard !Task.isCancelled else { return }
            connectionEnded(socket: socket, message: nil)
        }
    }

    private func handleFrames(_ payload: String, from sourceSocket: URLSessionWebSocketTask) {
        guard socket === sourceSocket else { return }

        for raw in payload.split(separator: "\u{0}", omittingEmptySubsequences: true) {
            let frame = String(raw)
            if frame.hasPrefix("CONNECTED") {
                isConnecting = false
                connected = true
                subscribe(over: sourceSocket)
                startHeartbeat(over: sourceSocket)
                onConnectionChange?(true, nil)
            } else if frame.hasPrefix("MESSAGE"),
                      let separator = frame.range(of: "\n\n") {
                let header = String(frame[..<separator.lowerBound])
                let body = String(frame[separator.upperBound...])
                let frameDestination = destination(in: header)

                if frameDestination == "/user/queue/errors" ||
                    frameDestination?.hasSuffix("/queue/errors") == true {
                    let message = body.trimmingCharacters(in: .whitespacesAndNewlines)
                    onSendError?(message.isEmpty ? "메시지를 전송하지 못했습니다." : message)
                    continue
                }

                guard let data = body.data(using: .utf8) else { continue }

                if frameDestination?.hasSuffix("/chat/read") == true,
                   let status = try? decoder.decode(ChatReadStatus.self, from: data) {
                    onReadStatus?(status)
                } else if let message = try? decoder.decode(ChatMessage.self, from: data) {
                    onMessage?(message)
                }
            } else if frame.hasPrefix("ERROR") {
                connectionEnded(socket: sourceSocket, message: String(frame.suffix(300)))
            }
        }
    }

    private func subscribe(over socket: URLSessionWebSocketTask) {
        guard let tripID else { return }
        let messageFrame = "SUBSCRIBE\nid:trip-chat-\(tripID)\ndestination:/sub/trips/\(tripID)/chat\nack:auto\n\n\u{0}"
        let readFrame = "SUBSCRIBE\nid:trip-chat-read-\(tripID)\ndestination:/sub/trips/\(tripID)/chat/read\nack:auto\n\n\u{0}"
        let errorFrame = "SUBSCRIBE\nid:trip-chat-errors-\(tripID)\ndestination:/user/queue/errors\nack:auto\n\n\u{0}"
        Task { [weak self] in
            try? await self?.sendFrame(messageFrame, over: socket)
            try? await self?.sendFrame(readFrame, over: socket)
            try? await self?.sendFrame(errorFrame, over: socket)
        }
    }

    private func destination(in header: String) -> String? {
        header
            .split(separator: "\n")
            .first { $0.hasPrefix("destination:") }
            .map { String($0.dropFirst("destination:".count)) }
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
    }

    private func startHeartbeat(over socket: URLSessionWebSocketTask) {
        heartbeatTask?.cancel()
        heartbeatTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(10))
                guard !Task.isCancelled else { return }
                try? await self?.sendFrame("\n", over: socket)
            }
        }
    }

    private func sendFrame(
        _ frame: String,
        over targetSocket: URLSessionWebSocketTask? = nil
    ) async throws {
        guard let socket = targetSocket ?? socket else { throw ChatSocketError.notConnected }
        try await socket.send(.string(frame))
    }

    private func connectionEnded(
        socket endedSocket: URLSessionWebSocketTask,
        message: String?
    ) {
        guard socket === endedSocket else { return }

        receiveTask?.cancel()
        heartbeatTask?.cancel()
        receiveTask = nil
        heartbeatTask = nil
        endedSocket.cancel(with: .goingAway, reason: nil)
        socket = nil
        connected = false
        isConnecting = false
        onConnectionChange?(false, message)
        scheduleReconnect()
    }

    private func scheduleReconnect() {
        guard shouldReconnect, reconnectTask == nil else { return }
        let delayMilliseconds = 3_000 + Int.random(in: 0...2_000)

        reconnectTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(delayMilliseconds))
            guard !Task.isCancelled else { return }
            self?.reconnectTask = nil
            self?.openConnection()
        }
    }

    private func closeCurrentConnection(sendDisconnect: Bool) {
        receiveTask?.cancel()
        heartbeatTask?.cancel()
        receiveTask = nil
        heartbeatTask = nil

        if sendDisconnect, connected {
            Task { [socket] in
                try? await socket?.send(.string("DISCONNECT\nreceipt:bye\n\n\u{0}"))
            }
        }
        socket?.cancel(with: .goingAway, reason: nil)
        socket = nil
        connected = false
        isConnecting = false
    }
}

private struct ChatSendPayload: Encodable { let content: String }

private enum ChatSocketError: LocalizedError {
    case notConnected
    var errorDescription: String? { "실시간 채팅에 연결되지 않았습니다." }
}
