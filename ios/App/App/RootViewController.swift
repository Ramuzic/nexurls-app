import UIKit
import WebKit
import Network

extension Notification.Name {
    /// Posted (with a URL as object) when a notification is tapped.
    static let nexAppOpenURL = Notification.Name("nexAppOpenURL")
}

/// The app's main screen: the site in a web view, a native tab bar under it,
/// a native offline screen, pull to refresh and the first-launch onboarding.
class RootViewController: UIViewController, UITabBarDelegate {

    /// A notification tapped before this screen existed (cold start) waits here.
    static var pendingURL: URL?

    private let config = AppConfig.shared
    private let bridgeVC = AppBridgeViewController()
    private let tabBar = UITabBar()
    private let offlineView = UIView()
    private let refresh = UIRefreshControl()
    private let monitor = NWPathMonitor()
    private var observers: [NSKeyValueObservation] = []
    private var lastTag = 0
    private var pendingChat = false
    private var isOffline = false
    private var shownOnboarding = false
    private var tabHidden = false
    /// The tabs stay out of sight until the first page has loaded and the splash screen has gone.
    private var booting = true
    private var wantHidden = false
    private var launchedAt = Date()
    private var webToTab: NSLayoutConstraint!
    private var webToBottom: NSLayoutConstraint!

    /// iOS 26 draws the tab bar as a floating glass pill, so the page runs underneath it.
    private var floating: Bool {
        if #available(iOS 26.0, *) { return true }
        return false
    }

    private let chatJS = "(function(){try{if(window.MobileApp&&typeof MobileApp.open==='function'){MobileApp.open();return 'ok';}if(window.Widget&&typeof Widget.open==='function'){Widget.open();return 'ok';}var b=document.getElementById('bmc-mobile-btn')||document.getElementById('bmc-pill');if(b){b.click();return 'ok';}}catch(e){}return 'no';})()"

    override var preferredStatusBarStyle: UIStatusBarStyle { .darkContent }
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .portrait }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = config.topColor

        // The site.
        addChild(bridgeVC)
        let web = bridgeVC.view!
        web.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(web)
        bridgeVC.didMove(toParent: self)

        // The native tab bar.
        buildTabBar()
        view.addSubview(tabBar)
        tabBar.translatesAutoresizingMaskIntoConstraints = false

        webToTab = web.bottomAnchor.constraint(equalTo: tabBar.topAnchor)
        webToBottom = web.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        (floating ? webToBottom : webToTab).isActive = true
        NSLayoutConstraint.activate([
            web.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            web.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            web.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tabBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tabBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tabBar.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            tabBar.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -49)
        ])

        // Hidden while the splash screen shows; finishBoot() brings it in.
        launchedAt = Date()
        applyTabBar(true, animated: false)
        // Never leave the tabs away if the first page is slow or fails.
        DispatchQueue.main.asyncAfter(deadline: .now() + 6) { [weak self] in self?.finishBoot() }

        buildOfflineView()
        watchWebView()
        watchNetwork()

        NotificationCenter.default.addObserver(forName: .nexAppOpenURL, object: nil, queue: .main) { [weak self] note in
            if let u = note.object as? URL { self?.open(u) }
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateObscured()
    }

    /// With the floating tab bar, tell the page how much of its bottom is covered,
    /// so fixed buttons and the end of the page sit above the glass pill.
    private func updateObscured() {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *), let web = bridgeVC.webView {
            let inset = tabHidden ? 0 : max(0, view.bounds.maxY - tabBar.frame.minY)
            if web.obscuredContentInsets.bottom != inset {
                web.obscuredContentInsets = UIEdgeInsets(top: 0, left: 0, bottom: inset, right: 0)
            }
        }
        #endif
    }

    /// Slides the tab bar away (full screen browsing, scrolling down, pop-ups) and back.
    /// While the app is starting this only remembers the wish; finishBoot() applies it.
    func setTabBarHidden(_ hidden: Bool) {
        wantHidden = hidden
        if booting { return }
        applyTabBar(hidden)
    }

    /// First page loaded: wait for the splash screen to finish, then fade the tabs in.
    private func finishBoot() {
        if !booting { return }
        let splash = 1.3 // the splash screen shows for 1.2 seconds (capacitor.config.json)
        let wait = max(0, splash - Date().timeIntervalSince(launchedAt))
        DispatchQueue.main.asyncAfter(deadline: .now() + wait) { [weak self] in
            guard let self = self, self.booting else { return }
            self.booting = false
            self.applyTabBar(self.wantHidden)
        }
    }

    private func applyTabBar(_ hidden: Bool, animated: Bool = true) {
        if hidden == tabHidden && animated { return }
        tabHidden = hidden
        if !floating {
            if hidden {
                webToTab.isActive = false
                webToBottom.isActive = true
            } else {
                webToBottom.isActive = false
                webToTab.isActive = true
            }
        }
        tabBar.isUserInteractionEnabled = !hidden
        let change = {
            self.tabBar.alpha = hidden ? 0 : 1
            self.tabBar.transform = hidden ? CGAffineTransform(translationX: 0, y: max(self.tabBar.bounds.height, 90)) : .identity
            self.view.layoutIfNeeded()
        }
        if animated {
            UIView.animate(withDuration: 0.25, delay: 0, options: [.curveEaseInOut, .beginFromCurrentState], animations: change)
        } else {
            change()
        }
        updateObscured()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if let u = RootViewController.pendingURL {
            RootViewController.pendingURL = nil
            open(u)
        }
        if !shownOnboarding && !UserDefaults.standard.bool(forKey: "nexapp_onboarded") && !config.onboarding.isEmpty {
            shownOnboarding = true
            let ob = OnboardingViewController()
            ob.modalPresentationStyle = .fullScreen
            ob.onDone = { UserDefaults.standard.set(true, forKey: "nexapp_onboarded") }
            present(ob, animated: false)
        }
    }

    // MARK: - Tab bar

    private func buildTabBar() {
        tabBar.delegate = self
        tabBar.tintColor = config.activeColor
        if !floating {
            // Before iOS 26: a solid bar in the site's dark colour.
            classicAppearance()
        } else {
            // iOS 26: only the floating glass pill. A tab bar that is not inside a
            // tab bar controller still paints a full-width band behind the pill by
            // default; a clear background removes that band so the page shows through.
            let a = UITabBarAppearance()
            a.configureWithTransparentBackground()
            a.backgroundColor = .clear
            a.backgroundEffect = nil
            a.shadowColor = .clear
            tabBar.standardAppearance = a
            tabBar.scrollEdgeAppearance = a
            tabBar.backgroundColor = .clear
            tabBar.isTranslucent = true
        }
        var items: [UITabBarItem] = []
        for (i, t) in config.tabs.enumerated() {
            let img = UIImage(systemName: AppConfig.symbol(t.icon))
            items.append(UITabBarItem(title: t.label, image: img, tag: i))
        }
        tabBar.items = items
        tabBar.selectedItem = items.first
    }

    private func classicAppearance() {
        let a = UITabBarAppearance()
        a.configureWithOpaqueBackground()
        a.backgroundColor = config.barColor
        a.shadowColor = UIColor(white: 1, alpha: 0.08)
        for layout in [a.stackedLayoutAppearance, a.inlineLayoutAppearance, a.compactInlineLayoutAppearance] {
            layout.normal.iconColor = config.inactiveColor
            layout.normal.titleTextAttributes = [.foregroundColor: config.inactiveColor, .font: UIFont.systemFont(ofSize: 10, weight: .medium)]
            layout.selected.iconColor = config.activeColor
            layout.selected.titleTextAttributes = [.foregroundColor: config.activeColor, .font: UIFont.systemFont(ofSize: 10, weight: .semibold)]
        }
        tabBar.standardAppearance = a
        tabBar.scrollEdgeAppearance = a
    }

    func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        let tab = config.tabs[item.tag]
        let same = item.tag == lastTag
        lastTag = item.tag

        if tab.action == "chat" {
            openChat()
            return
        }
        // Tapping the tab you are already on scrolls back to the top, like other apps.
        if same, let cur = bridgeVC.webView?.url, score(tab, cur) > 0 {
            bridgeVC.webView?.scrollView.setContentOffset(.zero, animated: true)
            return
        }
        bridgeVC.webView?.load(URLRequest(url: tab.url))
    }

    /// How well a tab matches a page: same path, and every query value on the tab present on the page.
    private func score(_ tab: AppTab, _ url: URL) -> Int {
        if tab.action == "chat" || !config.isOwn(url) { return 0 }
        func clean(_ p: String) -> String {
            var s = p
            while s.count > 1 && s.hasSuffix("/") { s.removeLast() }
            return s.isEmpty ? "/" : s
        }
        if clean(tab.url.path) != clean(url.path) {
            // Extra paths for this tab (Home also lights on the site root).
            let q = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
            return q.isEmpty && tab.match.map(clean).contains(clean(url.path)) ? 1 : 0
        }
        let want = URLComponents(url: tab.url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let have = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        var s = 1
        for q in want {
            if !have.contains(where: { $0.name == q.name && $0.value == q.value }) { return 0 }
            s += 1
        }
        return s
    }

    private func syncTab(_ url: URL?) {
        guard let url = url, let items = tabBar.items else { return }
        var best = -1, bestScore = 0
        for (i, t) in config.tabs.enumerated() {
            let s = score(t, url)
            if s > bestScore { bestScore = s; best = i }
        }
        // A page no tab points at (a single link, a profile) keeps the last tab lit.
        if best >= 0 && best < items.count {
            tabBar.selectedItem = items[best]
            lastTag = best
        }
    }

    // MARK: - Chat

    private func openChat() {
        guard let web = bridgeVC.webView else { return }
        web.evaluateJavaScript(chatJS) { [weak self] result, _ in
            guard let self = self else { return }
            if (result as? String) != "ok" {
                // Chat lives on the site's pages; open home, then the chat once it has loaded.
                self.pendingChat = true
                web.load(URLRequest(url: self.config.start))
            }
        }
    }

    // MARK: - Web view state

    private func watchWebView() {
        guard let web = bridgeVC.webView else { return }
        refresh.tintColor = config.activeColor
        refresh.addTarget(self, action: #selector(pullToRefresh), for: .valueChanged)
        web.scrollView.refreshControl = refresh

        observers.append(web.observe(\.url, options: [.new]) { [weak self] w, _ in
            DispatchQueue.main.async { self?.syncTab(w.url) }
        })
        observers.append(web.observe(\.isLoading, options: [.new]) { [weak self] w, _ in
            DispatchQueue.main.async {
                guard let self = self else { return }
                // A new page always starts with the tabs showing.
                if w.isLoading { self.setTabBarHidden(false); return }
                self.refresh.endRefreshing()
                self.syncTab(w.url)
                self.finishBoot()
                if self.pendingChat {
                    self.pendingChat = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                        w.evaluateJavaScript(self.chatJS, completionHandler: nil)
                    }
                }
            }
        })
    }

    @objc private func pullToRefresh() {
        bridgeVC.webView?.reload()
        DispatchQueue.main.asyncAfter(deadline: .now() + 8) { [weak self] in self?.refresh.endRefreshing() }
    }

    /// Opens a link from a notification. Links to the site stay in the app; others go to Safari.
    func open(_ url: URL) {
        if config.isOwn(url) {
            bridgeVC.webView?.load(URLRequest(url: url))
        } else {
            UIApplication.shared.open(url)
        }
    }

    // MARK: - Offline

    private func buildOfflineView() {
        offlineView.backgroundColor = config.paperColor
        offlineView.isHidden = true
        offlineView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(offlineView)
        NSLayoutConstraint.activate([
            offlineView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            offlineView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            offlineView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            offlineView.bottomAnchor.constraint(equalTo: tabBar.topAnchor)
        ])

        let icon = UIImageView(image: UIImage(systemName: "wifi.slash"))
        icon.tintColor = config.activeColor
        icon.contentMode = .scaleAspectFit
        icon.heightAnchor.constraint(equalToConstant: 56).isActive = true

        let title = UILabel()
        title.text = config.offlineTitle
        title.font = .systemFont(ofSize: 22, weight: .bold)
        title.textColor = config.inkColor
        title.textAlignment = .center

        let text = UILabel()
        text.text = config.offlineText
        text.font = .systemFont(ofSize: 16)
        text.textColor = config.mutedColor
        text.textAlignment = .center
        text.numberOfLines = 0

        let button = UIButton(type: .system)
        button.setTitle(config.offlineButton, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        button.backgroundColor = config.activeColor
        button.setTitleColor(config.inkColor, for: .normal)
        button.layer.cornerRadius = 24
        button.heightAnchor.constraint(equalToConstant: 48).isActive = true
        button.widthAnchor.constraint(equalToConstant: 200).isActive = true
        button.addTarget(self, action: #selector(retry), for: .touchUpInside)

        let stack = UIStackView(arrangedSubviews: [icon, title, text, button])
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        offlineView.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.centerYAnchor.constraint(equalTo: offlineView.centerYAnchor),
            stack.leadingAnchor.constraint(equalTo: offlineView.leadingAnchor, constant: 32),
            stack.trailingAnchor.constraint(equalTo: offlineView.trailingAnchor, constant: -32)
        ])
    }

    private func watchNetwork() {
        monitor.pathUpdateHandler = { [weak self] path in
            DispatchQueue.main.async { self?.setOffline(path.status != .satisfied) }
        }
        monitor.start(queue: DispatchQueue(label: "nexapp.network"))
    }

    private func setOffline(_ off: Bool) {
        if off == isOffline { return }
        isOffline = off
        offlineView.isHidden = !off
        if !off { bridgeVC.webView?.reload() }
    }

    @objc private func retry() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if monitor.currentPath.status == .satisfied {
            setOffline(false)
            bridgeVC.webView?.reload()
        }
    }
}
