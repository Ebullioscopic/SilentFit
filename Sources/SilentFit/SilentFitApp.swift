import Foundation
import OSLog
import SwiftUI
import SilentFitKit

/// A logger for the SilentFit module.
let logger: Logger = Logger(subsystem: "com.hariharan.silentfit", category: "SilentFit")

/// Long-lived app services shared by the UI and the platform app delegates.
final class AppEnvironment {
    static let shared = AppEnvironment()

    let health: HealthStore
    let preferences: PreferencesStore
    let phone: PhoneIntegration
    let session: WatchSession

    private init() {
        health = HealthStore()
        preferences = PreferencesStore()
        phone = PhoneIntegration()
        session = WatchSession(health: health, preferences: preferences, phone: phone)
    }
}

/// The shared top-level view for the app, loaded from the platform-specific App delegates.
public struct SilentFitRootView: View {
    @State private var session = AppEnvironment.shared.session
    @State private var preferences = AppEnvironment.shared.preferences

    public init() {
    }

    public var body: some View {
        ContentView()
            .environment(session)
            .environment(preferences)
            .tint(Color.accentColor)
            .task {
                session.start()
            }
    }
}

/// Global application delegate functions, invoked from Darwin/Sources/Main.swift and Android Main.kt.
public final class SilentFitAppDelegate: Sendable {
    public static let shared = SilentFitAppDelegate()

    private init() {
    }

    public func onInit() {
        logger.debug("onInit")
    }

    public func onLaunch() {
        logger.debug("onLaunch")
    }

    public func onResume() {
        let session = AppEnvironment.shared.session
        if session.state == .disconnected || session.state == .failed {
            session.start()
        } else if session.isConnected {
            session.syncHealth()
        }
    }

    public func onPause() {
    }

    public func onStop() {
    }

    public func onDestroy() {
    }

    public func onLowMemory() {
    }
}
