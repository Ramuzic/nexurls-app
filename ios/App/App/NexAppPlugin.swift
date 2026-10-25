import UIKit
import Capacitor
import StoreKit
import AuthenticationServices
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
        CAPPluginMethod(name: "info", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "tabBar", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "iapProducts", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "iapBuy", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "iapPending", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "iapRestore", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "iapFinish", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "iapManage", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "appleSignIn", returnType: CAPPluginReturnPromise)
    ]

    private var updatesTask: Task<Void, Never>?
    private var appleSignIn: AppleSignInHelper?

    /// Purchases that finish while the app is closed (renewals, Ask to Buy, a purchase
    /// interrupted by a crash) arrive here. They stay unfinished until the site has
    /// granted them; the site collects them with iapPending on its next page load.
    public override func load() {
        updatesTask = Task.detached { [weak self] in
            for await result in Transaction.updates {
                self?.notifyListeners("iapUpdate", data: ["jws": result.jwsRepresentation])
            }
        }
    }

    deinit { updatesTask?.cancel() }

    // MARK: - In-app purchases (StoreKit 2)

    private func typeName(_ t: Product.ProductType) -> String {
        if t == .autoRenewable { return "subscription" }
        if t == .consumable { return "consumable" }
        if t == .nonConsumable { return "nonConsumable" }
        return "other"
    }

    /// { ids: [...] } -> { products: [{ id, title, description, price, type }] } with the
    /// price in the buyer's own currency, as the App Store shows it.
    @objc public func iapProducts(_ call: CAPPluginCall) {
        let ids = (call.getArray("ids") as? [String]) ?? []
        Task {
            do {
                let products = try await Product.products(for: ids)
                call.resolve(["products": products.map { p in
                    ["id": p.id, "title": p.displayName, "description": p.description, "price": p.displayPrice, "type": self.typeName(p.type)]
                }])
            } catch {
                call.reject(error.localizedDescription)
            }
        }
    }

    /// { id, token } where token is the member's purchase id from the site (a UUID), so the
    /// site can tie the Apple purchase to the right account. Resolves with
    /// { status: "purchased" | "pending" | "cancelled", jws, transactionId, productId }.
    /// The purchase is left unfinished until the site has granted it (iapFinish).
    @objc public func iapBuy(_ call: CAPPluginCall) {
        guard let id = call.getString("id") else { call.reject("No product"); return }
        let token = call.getString("token").flatMap { UUID(uuidString: $0) }
        Task {
            do {
                guard let product = try await Product.products(for: [id]).first else {
                    call.reject("This product is not available right now.")
                    return
                }
                var options: Set<Product.PurchaseOption> = []
                if let token = token { options.insert(.appAccountToken(token)) }
                let result = try await product.purchase(options: options)
                switch result {
                case .success(let verification):
                    switch verification {
                    case .verified(let tx):
                        call.resolve(["status": "purchased", "jws": verification.jwsRepresentation, "transactionId": String(tx.id), "productId": tx.productID])
                    case .unverified(_, let error):
                        call.reject("The App Store could not confirm this purchase: " + error.localizedDescription)
                    }
                case .pending:
                    call.resolve(["status": "pending"])
                case .userCancelled:
                    call.resolve(["status": "cancelled"])
                @unknown default:
                    call.resolve(["status": "cancelled"])
                }
            } catch {
                call.reject(error.localizedDescription)
            }
        }
    }

    /// Purchases not yet granted by the site: { items: [jws, ...] }.
    @objc public func iapPending(_ call: CAPPluginCall) {
        Task {
            var items: [String] = []
            for await result in Transaction.unfinished {
                if case .verified = result { items.append(result.jwsRepresentation) }
            }
            call.resolve(["items": items])
        }
    }

    /// Restore purchases: asks the App Store to sync, then returns every active
    /// subscription and every purchase not yet granted: { items: [jws, ...] }.
    @objc public func iapRestore(_ call: CAPPluginCall) {
        Task {
            try? await AppStore.sync()
            var items: [String] = []
            for await result in Transaction.currentEntitlements {
                if case .verified = result { items.append(result.jwsRepresentation) }
            }
            for await result in Transaction.unfinished {
                if case .verified = result { items.append(result.jwsRepresentation) }
            }
            call.resolve(["items": items])
        }
    }

    /// The site has granted this purchase: { transactionId }.
    @objc public func iapFinish(_ call: CAPPluginCall) {
        guard let raw = call.getString("transactionId"), let id = UInt64(raw) else { call.reject("No transaction"); return }
        Task {
            for await result in Transaction.unfinished {
                if case .verified(let tx) = result, tx.id == id { await tx.finish() }
            }
            call.resolve()
        }
    }

    /// Opens Apple's own subscription management sheet (cancel or switch plans).
    @objc public func iapManage(_ call: CAPPluginCall) {
        Task { @MainActor in
            if let scene = self.bridge?.viewController?.view.window?.windowScene {
                try? await AppStore.showManageSubscriptions(in: scene)
            }
            call.resolve()
        }
    }

    // MARK: - Sign in with Apple

    /// Resolves with { identityToken, user, email, givenName, familyName }, or
    /// { cancelled: true } when the person closes the sheet.
    @objc public func appleSignIn(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let helper = AppleSignInHelper(window: self.bridge?.viewController?.view.window) { [weak self] result in
                call.resolve(result)
                self?.appleSignIn = nil
            }
            self.appleSignIn = helper
            helper.start()
        }
    }

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

    /// A tap you can feel. { style: "light" (default), "medium", "heavy", "soft", "rigid",
    /// "selection", "success", "warning" or "error" }.
    @objc public func haptic(_ call: CAPPluginCall) {
        let style = call.getString("style") ?? "light"
        DispatchQueue.main.async {
            switch style {
            case "medium": UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            case "heavy": UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            case "soft": UIImpactFeedbackGenerator(style: .soft).impactOccurred()
            case "rigid": UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
            case "selection": UISelectionFeedbackGenerator().selectionChanged()
            case "success": UINotificationFeedbackGenerator().notificationOccurred(.success)
            case "warning": UINotificationFeedbackGenerator().notificationOccurred(.warning)
            case "error": UINotificationFeedbackGenerator().notificationOccurred(.error)
            default: UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
            call.resolve()
        }
    }

    /// The site hides the tabs while a website is open full screen: { hidden: true }.
    @objc public func tabBar(_ call: CAPPluginCall) {
        let hidden = call.getBool("hidden") ?? false
        DispatchQueue.main.async {
            (self.bridge?.viewController?.parent as? RootViewController)?.setTabBarHidden(hidden)
            call.resolve()
        }
    }

    @objc public func info(_ call: CAPPluginCall) {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        call.resolve(["platform": "ios", "version": v])
    }
}

/// Runs Apple's own sign-in sheet and hands back what the site needs to verify it.
final class AppleSignInHelper: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private let window: UIWindow?
    private let done: ([String: Any]) -> Void

    init(window: UIWindow?, done: @escaping ([String: Any]) -> Void) {
        self.window = window
        self.done = done
    }

    func start() {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        return window ?? ASPresentationAnchor()
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let cred = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = cred.identityToken,
              let token = String(data: tokenData, encoding: .utf8) else {
            done(["error": "Apple did not return a sign-in token."])
            return
        }
        done([
            "identityToken": token,
            "user": cred.user,
            "email": cred.email ?? "",
            "givenName": cred.fullName?.givenName ?? "",
            "familyName": cred.fullName?.familyName ?? ""
        ])
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        if let e = error as? ASAuthorizationError, e.code == .canceled {
            done(["cancelled": true])
        } else {
            done(["error": error.localizedDescription])
        }
    }
}
