import UIKit
import Capacitor
import WebKit

/// Capacitor's web view, set up to sit between the status bar and the native tab bar.
class AppBridgeViewController: CAPBridgeViewController {

    /// Kept here because a web view only holds its navigation delegate weakly.
    private var navGuard: NavGuard?

    override func capacitorDidLoad() {
        bridge?.registerPluginInstance(NexAppPlugin())
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        guard let web = webView else { return }
        // The root view already keeps the page below the status bar and above the
        // tab bar, so the page itself should not add any extra spacing.
        web.scrollView.contentInsetAdjustmentBehavior = .never
        // Swipe from the left edge to go back, like any iPhone app.
        web.allowsBackForwardNavigationGestures = true
        // Pages may not take over the screen with element full screen: inside this app it crashed (the galaxy's full screen button).
        // They use their own full-window view instead.
        if #available(iOS 15.4, *) { web.configuration.preferences.isElementFullscreenEnabled = false }
        // NavGuard (1.1.2): sits in front of Capacitor's navigation delegate. See the class below.
        if let capacitorDelegate = web.navigationDelegate {
            navGuard = NavGuard(wrapping: capacitorDelegate)
            web.navigationDelegate = navGuard
        }
        web.isOpaque = false
        web.backgroundColor = AppConfig.shared.paperColor
        web.scrollView.backgroundColor = AppConfig.shared.paperColor

        // Screenshot mode (launch argument -NexShot, used only by the screenshot
        // workflow): skip the web welcome screen unless -NexShowGate is also given.
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-NexShot") && !args.contains("-NexShowGate") {
            let js = "try{localStorage.setItem('nxsf_gate','1')}catch(e){}"
            web.configuration.userContentController.addUserScript(WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        }
        guard args.contains("-NexShot") else { return }
        // -NexCookies "a=1; b=2": a signed-in member for the screenshots (cookies made by the workflow).
        if let i = args.firstIndex(of: "-NexCookies"), i + 1 < args.count {
            let store = web.configuration.websiteDataStore.httpCookieStore
            for pair in args[i + 1].components(separatedBy: ";") {
                let kv = pair.trimmingCharacters(in: .whitespaces)
                guard let eq = kv.firstIndex(of: "=") else { continue }
                let name = String(kv[..<eq]), value = String(kv[kv.index(after: eq)...])
                if name.isEmpty { continue }
                if let c = HTTPCookie(properties: [.domain: "nexurls.com", .path: "/", .name: name, .value: value, .secure: "TRUE", .expires: Date().addingTimeInterval(86400)]) {
                    store.setCookie(c)
                }
            }
        }
        // -NexJS "code": runs on each page once it has loaded (for example to open full screen browsing).
        if let i = args.firstIndex(of: "-NexJS"), i + 1 < args.count {
            web.configuration.userContentController.addUserScript(WKUserScript(source: args[i + 1], injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        }
    }
}

/// NavGuard: stops the "You're offline" page from showing when nothing is wrong.
///
/// Capacitor shows its error page (offline.html) for EVERY failed page load, including the
/// ones it cancels itself. Tapping Visit on a link goes to nexurls.com, which redirects to the
/// outside site; Capacitor hands that site to Safari and cancels the load in the app, and the
/// cancel used to count as a failure, so the member came back to the offline page.
/// (The site has worked around it since NexURLs Core 0.79.12; this fixes it in the app itself.)
///
/// NavGuard passes everything to Capacitor unchanged, except failures that are really a
/// cancel or an interrupted load, which it simply ignores. Real network errors still show
/// the offline page.
final class NavGuard: NSObject, WKNavigationDelegate {

    private weak var inner: WKNavigationDelegate?

    init(wrapping inner: WKNavigationDelegate) {
        self.inner = inner
        super.init()
    }

    /// Not a real failure: the load was cancelled or replaced on purpose.
    static func isHarmless(_ error: Error) -> Bool {
        let e = error as NSError
        if e.domain == NSURLErrorDomain && e.code == NSURLErrorCancelled { return true }  // -999
        if e.domain == "WebKitErrorDomain" && (e.code == 102 || e.code == 204) { return true }  // load interrupted / handled elsewhere
        return false
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if NavGuard.isHarmless(error) { return }
        inner?.webView?(webView, didFailProvisionalNavigation: navigation, withError: error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        if NavGuard.isHarmless(error) { return }
        inner?.webView?(webView, didFail: navigation, withError: error)
    }

    // Everything else goes straight to Capacitor.
    override func responds(to aSelector: Selector!) -> Bool {
        if super.responds(to: aSelector) { return true }
        return (inner as AnyObject?)?.responds(to: aSelector) ?? false
    }

    override func forwardingTarget(for aSelector: Selector!) -> Any? {
        if let inner = inner, (inner as AnyObject).responds(to: aSelector) { return inner }
        return super.forwardingTarget(for: aSelector)
    }
}
