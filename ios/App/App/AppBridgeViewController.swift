import UIKit
import Capacitor
import WebKit

/// Capacitor's web view, set up to sit between the status bar and the native tab bar.
class AppBridgeViewController: CAPBridgeViewController {

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
