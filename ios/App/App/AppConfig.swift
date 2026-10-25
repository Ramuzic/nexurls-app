import UIKit

/// Everything the native shell needs to know, read from www/app.json
/// (copied into the app as public/app.json by `npx cap copy`).
/// Edit app.json to change tabs, colors or onboarding text without touching code.
struct AppTab {
    let label: String
    let url: URL
    let icon: String
    let action: String
    /// Extra page paths that also light this tab (Home lights on the site root too).
    let match: [String]
}

struct OnboardingPage {
    let icon: String
    let title: String
    let text: String
    let push: Bool
}

final class AppConfig {
    static let shared = AppConfig()

    let start: URL
    let host: String
    let oneSignalAppId: String
    let tabs: [AppTab]
    let onboarding: [OnboardingPage]
    let topColor: UIColor
    let barColor: UIColor
    let activeColor: UIColor
    let inactiveColor: UIColor
    let paperColor: UIColor
    let inkColor: UIColor
    let mutedColor: UIColor
    let offlineTitle: String
    let offlineText: String
    let offlineButton: String

    private init() {
        var json: [String: Any] = [:]
        if let url = Bundle.main.url(forResource: "app", withExtension: "json", subdirectory: "public"),
           let data = try? Data(contentsOf: url),
           let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            json = obj
        }
        let fallbackStart = URL(string: "https://nexurls.com/app/")!
        start = URL(string: json["start"] as? String ?? "") ?? fallbackStart
        host = json["host"] as? String ?? "nexurls.com"
        oneSignalAppId = json["onesignal_app_id"] as? String ?? ""

        let colors = json["colors"] as? [String: String] ?? [:]
        topColor = UIColor(hex: colors["top"] ?? "#F4F0E8")
        barColor = UIColor(hex: colors["bar"] ?? "#121212")
        activeColor = UIColor(hex: colors["active"] ?? "#FF5A1F")
        inactiveColor = UIColor(hex: colors["inactive"] ?? "#B9B2A6")
        paperColor = UIColor(hex: colors["paper"] ?? "#F4F0E8")
        inkColor = UIColor(hex: colors["ink"] ?? "#121212")
        mutedColor = UIColor(hex: colors["muted"] ?? "#6B665E")

        var t: [AppTab] = []
        for item in json["tabs"] as? [[String: Any]] ?? [] {
            guard let label = item["label"] as? String,
                  let u = URL(string: item["url"] as? String ?? "") else { continue }
            t.append(AppTab(label: label, url: u, icon: item["icon"] as? String ?? "home", action: item["action"] as? String ?? "navigate", match: item["match"] as? [String] ?? []))
        }
        if t.isEmpty {
            t = [AppTab(label: "Home", url: start, icon: "home", action: "navigate", match: ["/"])]
        }
        tabs = t

        var o: [OnboardingPage] = []
        for item in json["onboarding"] as? [[String: Any]] ?? [] {
            o.append(OnboardingPage(icon: item["icon"] as? String ?? "next",
                                    title: item["title"] as? String ?? "",
                                    text: item["text"] as? String ?? "",
                                    push: item["push"] as? Bool ?? false))
        }
        onboarding = o

        let off = json["offline"] as? [String: String] ?? [:]
        offlineTitle = off["title"] ?? "You're offline"
        offlineText = off["text"] ?? "Check your connection and try again."
        offlineButton = off["button"] ?? "Try again"
    }

    /// SF Symbol for each icon name used in app.json.
    static func symbol(_ name: String) -> String {
        switch name {
        case "home": return "house.fill"
        case "next": return "safari.fill"
        case "reels": return "play.rectangle.fill"
        case "community": return "person.2.fill"
        case "chat": return "bubble.left.and.bubble.right.fill"
        case "share": return "square.and.arrow.up.fill"
        case "bell": return "bell.badge.fill"
        default: return "circle.fill"
        }
    }

    /// True when a link belongs to the site and should stay inside the app.
    func isOwn(_ url: URL?) -> Bool {
        guard let h = url?.host?.lowercased() else { return false }
        return h == host || h.hasSuffix("." + host)
    }
}

extension UIColor {
    convenience init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        var v: UInt64 = 0
        Scanner(string: s).scanHexInt64(&v)
        if s.count == 6 {
            self.init(red: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255, blue: CGFloat(v & 0xFF) / 255, alpha: 1)
        } else {
            self.init(white: 0, alpha: 1)
        }
    }
}
