import SwiftUI
import SilentFitKit

enum AppTab: String, Hashable {
    case today
    case trends
    case watch
    case settings
}

struct ContentView: View {
    @Environment(WatchSession.self) var session
    @Environment(PreferencesStore.self) var preferences
    @State private var tab = AppTab.today

    var body: some View {
        TabView(selection: $tab) {
            TodayView(selectedTab: $tab)
                .tabItem { Label("Today", systemImage: "heart.text.square.fill") }
                .tag(AppTab.today)
            TrendsView()
                .tabItem { Label("Trends", systemImage: "chart.bar.xaxis") }
                .tag(AppTab.trends)
            WatchView()
                .tabItem { Label("Watch", systemImage: "applewatch") }
                .tag(AppTab.watch)
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
                .tag(AppTab.settings)
        }
        .sheet(isPresented: onboardingBinding) {
            OnboardingView()
        }
        .alert("Finding Your Phone", isPresented: findPhoneBinding) {
            Button("I Found It") {
                session.stopFindingPhone()
            }
        } message: {
            Text("Your watch asked this phone to ring.")
        }
    }

    private var onboardingBinding: Binding<Bool> {
        Binding(get: { !preferences.value.onboarded }, set: { shown in
            if !shown {
                preferences.value.onboarded = true
            }
        })
    }

    private var findPhoneBinding: Binding<Bool> {
        Binding(get: { session.isFindingPhone }, set: { shown in
            if !shown && session.isFindingPhone {
                session.stopFindingPhone()
            }
        })
    }
}
