//
//  TripLogApp.swift
//  TripLog
//
//  Created by Programmers on 8/4/26.
//

import SwiftUI
import UIKit
import UserNotifications

final class TripLogAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let userInfo = response.notification.request.content.userInfo
        guard let tripGroupID = tripGroupID(from: userInfo) else { return }

        await MainActor.run {
            NotificationCenter.default.post(
                name: .openTripChatFromNotification,
                object: nil,
                userInfo: [ChatNotificationNavigation.tripGroupIDKey: tripGroupID]
            )
        }
    }

    private func tripGroupID(from userInfo: [AnyHashable: Any]) -> Int64? {
        let value = userInfo[ChatNotificationNavigation.tripGroupIDKey]

        if let number = value as? NSNumber {
            return number.int64Value
        }
        if let integer = value as? Int64 {
            return integer
        }
        if let integer = value as? Int {
            return Int64(integer)
        }
        if let text = value as? String {
            return Int64(text)
        }
        return nil
    }
}

@main
struct TripLogApp: App {
    @UIApplicationDelegateAdaptor(TripLogAppDelegate.self) private var appDelegate

    init() {
        UIRefreshControl.appearance().tintColor = .clear
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .tint(TripLogPalette.blue)
        }
    }
}
