import UIKit
import Capacitor
#if canImport(OneSignalFramework)
import OneSignalFramework

/// A tapped notification opens its page inside the app (not Safari).
final class NexAppNotificationClicks: NSObject, OSNotificationClickListener {
    func onClick(event: OSNotificationClickEvent) {
        let raw = event.result.url ?? event.notification.launchURL ?? (event.notification.additionalData?["url"] as? String)
        guard let s = raw, let url = URL(string: s) else { return }
        DispatchQueue.main.async {
            RootViewController.pendingURL = url
            NotificationCenter.default.post(name: .nexAppOpenURL, object: url)
        }
    }
}
#endif

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?
    #if canImport(OneSignalFramework)
    private let clicks = NexAppNotificationClicks()
    #endif

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        #if canImport(OneSignalFramework)
        let appId = AppConfig.shared.oneSignalAppId
        if !appId.isEmpty {
            OneSignal.initialize(appId, withLaunchOptions: launchOptions)
            OneSignal.Notifications.addClickListener(clicks)
        }
        #endif
        return true
    }

    func applicationWillResignActive(_ application: UIApplication) {
        // Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
        // Use this method to pause ongoing tasks, disable timers, and invalidate graphics rendering callbacks. Games should use this method to pause the game.
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        // Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
        // If your application supports background execution, this method is called instead of applicationWillTerminate: when the user quits.
    }

    func applicationWillEnterForeground(_ application: UIApplication) {
        // Called as part of the transition from the background to the active state; here you can undo many of the changes made on entering the background.
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        // Restart any tasks that were paused (or not yet started) while the application was inactive. If the application was previously in the background, optionally refresh the user interface.
    }

    func applicationWillTerminate(_ application: UIApplication) {
        // Called when the application is about to terminate. Save data if appropriate. See also applicationDidEnterBackground:.
    }

    func application(_ application: UIApplication,
                     configurationForConnecting connectingSceneSession: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: "Default Configuration",
                                          sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }
}
