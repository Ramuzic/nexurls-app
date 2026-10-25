import UIKit
import Capacitor
#if canImport(OneSignalFramework)
import OneSignalFramework
#endif

/// The small native bridge the website talks to when it runs inside the app.
/// From a page on nexurls.com:
///   Capacitor.nativePromise('NexApp', 'registerPush', { externalId: 'nexurls_com_12' })
///   Capacitor.nativePromise('NexApp', 'haptic', {})
@objc(NexAppPlugin)
public class NexAppPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "NexAppPlugin"
    public let jsName = "NexApp"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "registerPush", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "haptic", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "info", returnType: CAPPluginReturnPromise)
    ]

    /// Links this phone to the signed-in member, so the site's notifications reach it.
    /// Resolves with the OneSignal subscription id (empty until the phone has registered).
    @objc public func registerPush(_ call: CAPPluginCall) {
        let ext = call.getString("externalId") ?? ""
        DispatchQueue.main.async {
            #if canImport(OneSignalFramework)
            if !ext.isEmpty { OneSignal.login(ext) }
            call.resolve([
                "id": OneSignal.User.pushSubscription.id ?? "",
                "optedIn": OneSignal.User.pushSubscription.optedIn
            ])
            #else
            call.resolve(["id": "", "optedIn": false])
            #endif
        }
    }

    @objc public func haptic(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            call.resolve()
        }
    }

    @objc public func info(_ call: CAPPluginCall) {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        call.resolve(["platform": "ios", "version": v])
    }
}
