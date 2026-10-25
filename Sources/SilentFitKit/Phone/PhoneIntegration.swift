import Foundation
#if !SKIP && canImport(AudioToolbox)
import AudioToolbox
#endif

/// A notification captured on the phone, already classified for the watch.
public struct PhoneNotification {
    public enum Kind {
        case app
        case incomingCall
        case missedCall
        case sms
    }

    public let kind: Kind
    public let packageName: String
    public let appName: String
    public let title: String
    public let text: String
    public let key: String
}

/// Phone-side services the watch can trigger, and phone events the watch should hear about.
///
/// Android provides the full set; Apple platforms provide what their sandbox allows.
public final class PhoneIntegration {
    public var onNotification: ((PhoneNotification) -> Void)? = nil
    public var onCallEnded: (() -> Void)? = nil
    public var onNowPlaying: ((NowPlaying) -> Void)? = nil
    private var findPhoneTask: Task<Void, Never>? = nil

    public init() {
    }

    public var phoneName: String {
        #if SKIP
        return PhoneBridge.phoneName()
        #elseif os(macOS)
        return Host.current().localizedName ?? "Mac"
        #else
        return "iPhone"
        #endif
    }

    public var systemVersion: String {
        #if SKIP
        return PhoneBridge.systemVersion()
        #else
        return "\(ProcessInfo.processInfo.operatingSystemVersion.majorVersion)"
        #endif
    }

    public var supportsNotificationForwarding: Bool {
        #if SKIP
        return true
        #else
        return false
        #endif
    }

    public var supportsMediaControl: Bool {
        #if SKIP
        return true
        #else
        return false
        #endif
    }

    public var notificationAccessGranted: Bool {
        #if SKIP
        return PhoneBridge.isNotificationAccessGranted(ProcessInfo.processInfo.androidContext)
        #else
        return false
        #endif
    }

    public func start() {
        #if SKIP
        PhoneBridge.notificationPosted = { pkg, app, title, text, key, category in
            self.onNotification?(self.classify(pkg: pkg, app: app, title: title, text: text, key: key, category: category))
        }
        PhoneBridge.notificationRemoved = { pkg, key, category in
            if category == "call" {
                self.onCallEnded?()
            }
        }
        PhoneBridge.mediaChanged = { state, title, artist, player, volume, maxVolume, duration, position in
            let info = NowPlaying(state: PlaybackState(rawValue: state) ?? PlaybackState.nothingPlaying, title: title, artist: artist, player: player, volume: volume, maxVolume: maxVolume, durationSeconds: duration, positionSeconds: position)
            self.onNowPlaying?(info)
        }
        SilentFitNotificationListener.publishMedia(ProcessInfo.processInfo.androidContext)
        #endif
    }

    #if SKIP
    private func classify(pkg: String, app: String, title: String, text: String, key: String, category: String) -> PhoneNotification {
        var kind = PhoneNotification.Kind.app
        let dialer = pkg.contains("dialer") || pkg.contains("incallui") || pkg.contains("telecom") || pkg == "com.android.phone"
        let smsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(ProcessInfo.processInfo.androidContext) ?? ""
        if category == "call" {
            kind = PhoneNotification.Kind.incomingCall
        } else if category == "missed_call" || (dialer && text.lowercased().contains("missed")) || (dialer && title.lowercased().contains("missed")) {
            kind = PhoneNotification.Kind.missedCall
        } else if pkg == smsPackage || (category == "msg" && pkg.contains("messaging")) {
            kind = PhoneNotification.Kind.sms
        }
        return PhoneNotification(kind: kind, packageName: pkg, appName: app, title: title, text: text, key: key)
    }
    #endif

    public func openNotificationAccessSettings() {
        #if SKIP
        PhoneBridge.openNotificationAccessSettings(ProcessInfo.processInfo.androidContext)
        #endif
    }

    public func openAppSettings() {
        #if SKIP
        PhoneBridge.openAppSettings(ProcessInfo.processInfo.androidContext)
        #endif
    }

    public func openBluetoothSettings() {
        #if SKIP
        PhoneBridge.openBluetoothSettings(ProcessInfo.processInfo.androidContext)
        #endif
    }

    // MARK: Watch-initiated actions

    public func startFindPhone() {
        #if SKIP
        PhoneBridge.startFindPhone(ProcessInfo.processInfo.androidContext)
        #else
        findPhoneTask?.cancel()
        findPhoneTask = Task {
            var count = 0
            while !Task.isCancelled && count < 60 {
                PhoneIntegration.beep()
                await Delay.ms(1000)
                count += 1
            }
        }
        #endif
    }

    public func stopFindPhone() {
        #if SKIP
        PhoneBridge.stopFindPhone(ProcessInfo.processInfo.androidContext)
        #else
        findPhoneTask?.cancel()
        findPhoneTask = nil
        #endif
    }

    #if !SKIP
    static func beep() {
        #if canImport(AudioToolbox) && os(iOS)
        AudioServicesPlayAlertSound(SystemSoundID(1005))
        #elseif canImport(AudioToolbox)
        AudioServicesPlayAlertSound(SystemSoundID(kSystemSoundID_UserPreferredAlert))
        #endif
    }
    #endif

    public func mediaCommand(_ command: MediaCommand) {
        #if SKIP
        PhoneBridge.mediaCommand(ProcessInfo.processInfo.androidContext, command.rawValue)
        #endif
    }

    /// `state` from the watch: 0 answer, 1 hang up, 2 mute.
    public func callAction(_ state: Int) {
        #if SKIP
        let context = ProcessInfo.processInfo.androidContext
        if state == 0 {
            PhoneBridge.answerCall(context)
        } else if state == 1 {
            PhoneBridge.endCall(context)
        } else {
            PhoneBridge.silenceRinger(context)
        }
        #endif
    }

    public func sendSMS(to number: String, text: String) -> Bool {
        #if SKIP
        return PhoneBridge.sendSms(ProcessInfo.processInfo.androidContext, number, text)
        #else
        return false
        #endif
    }

    public func linkUp(watchName: String) {
        #if SKIP
        PhoneBridge.startLinkService(ProcessInfo.processInfo.androidContext, "Connected to \(watchName)")
        #endif
    }

    public func linkDown() {
        #if SKIP
        PhoneBridge.stopLinkService(ProcessInfo.processInfo.androidContext)
        #endif
    }

    public func missingPermissions() -> [String] {
        #if SKIP
        var out: [String] = []
        for p in PhoneBridge.missingPermissions(ProcessInfo.processInfo.androidContext) {
            out.append(p)
        }
        return out
        #else
        return []
        #endif
    }
}
