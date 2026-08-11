import Foundation

enum AppConfiguration {
    // iOS Simulator에서는 Mac에서 실행 중인 Spring 서버의 localhost에 접근할 수 있습니다.
    // 실제 iPhone에서 테스트할 때는 Mac의 같은 Wi-Fi 내부 IP로 변경하면 됩니다.
    static let apiBaseURL = URL(string: "https://triplog6.duckdns.org")!
}
