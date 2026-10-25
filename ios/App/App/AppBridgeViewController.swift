import UIKit
import Capacitor

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
    }
}
