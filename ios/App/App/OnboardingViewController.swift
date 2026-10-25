import UIKit
import UserNotifications
#if canImport(OneSignalFramework)
import OneSignalFramework
#endif

/// Native first-launch screens, swiped like any app intro. Text lives in app.json.
class OnboardingViewController: UIViewController, UIScrollViewDelegate {

    var onDone: (() -> Void)?

    private let config = AppConfig.shared
    private let scroll = UIScrollView()
    private let dots = UIPageControl()
    private let primary = UIButton(type: .system)
    private let secondary = UIButton(type: .system)
    private var page = 0

    override var preferredStatusBarStyle: UIStatusBarStyle { .darkContent }
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .portrait }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = config.paperColor

        scroll.isPagingEnabled = true
        scroll.showsHorizontalScrollIndicator = false
        scroll.delegate = self
        scroll.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scroll)

        let row = UIStackView()
        row.axis = .horizontal
        row.distribution = .fillEqually
        row.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(row)

        for p in config.onboarding {
            let v = pageView(p)
            row.addArrangedSubview(v)
            v.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor).isActive = true
        }

        dots.numberOfPages = config.onboarding.count
        dots.currentPageIndicatorTintColor = config.activeColor
        dots.pageIndicatorTintColor = UIColor(white: 0, alpha: 0.15)
        dots.isUserInteractionEnabled = false
        dots.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(dots)

        primary.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        primary.backgroundColor = config.activeColor
        primary.setTitleColor(config.inkColor, for: .normal)
        primary.layer.cornerRadius = 26
        primary.addTarget(self, action: #selector(primaryTap), for: .touchUpInside)
        primary.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(primary)

        secondary.titleLabel?.font = .systemFont(ofSize: 16, weight: .medium)
        secondary.setTitleColor(config.mutedColor, for: .normal)
        secondary.addTarget(self, action: #selector(secondaryTap), for: .touchUpInside)
        secondary.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(secondary)

        let g = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: g.topAnchor),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: dots.topAnchor, constant: -12),

            row.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            row.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            row.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            row.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            row.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),

            dots.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            dots.bottomAnchor.constraint(equalTo: primary.topAnchor, constant: -20),

            primary.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 28),
            primary.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -28),
            primary.heightAnchor.constraint(equalToConstant: 52),
            primary.bottomAnchor.constraint(equalTo: secondary.topAnchor, constant: -6),

            secondary.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            secondary.heightAnchor.constraint(equalToConstant: 44),
            secondary.bottomAnchor.constraint(equalTo: g.bottomAnchor, constant: -10)
        ])
        update()
    }

    private func pageView(_ p: OnboardingPage) -> UIView {
        let v = UIView()

        let circle = UIView()
        circle.backgroundColor = config.activeColor
        circle.layer.cornerRadius = 64
        circle.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: AppConfig.symbol(p.icon)))
        icon.tintColor = config.inkColor
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        circle.addSubview(icon)

        let title = UILabel()
        title.text = p.title
        title.font = .systemFont(ofSize: 28, weight: .bold)
        title.textColor = config.inkColor
        title.textAlignment = .center
        title.numberOfLines = 0

        let text = UILabel()
        text.text = p.text
        text.font = .systemFont(ofSize: 17)
        text.textColor = config.mutedColor
        text.textAlignment = .center
        text.numberOfLines = 0

        let stack = UIStackView(arrangedSubviews: [circle, title, text])
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 18
        stack.setCustomSpacing(32, after: circle)
        stack.translatesAutoresizingMaskIntoConstraints = false
        v.addSubview(stack)

        NSLayoutConstraint.activate([
            circle.widthAnchor.constraint(equalToConstant: 128),
            circle.heightAnchor.constraint(equalToConstant: 128),
            icon.centerXAnchor.constraint(equalTo: circle.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: circle.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 60),
            icon.heightAnchor.constraint(equalToConstant: 60),
            stack.centerYAnchor.constraint(equalTo: v.centerYAnchor),
            stack.leadingAnchor.constraint(equalTo: v.leadingAnchor, constant: 32),
            stack.trailingAnchor.constraint(equalTo: v.trailingAnchor, constant: -32)
        ])
        return v
    }

    private var isLast: Bool { page >= config.onboarding.count - 1 }
    private var current: OnboardingPage? { config.onboarding.indices.contains(page) ? config.onboarding[page] : nil }

    private func update() {
        dots.currentPage = page
        if isLast && current?.push == true {
            primary.setTitle("Turn on notifications", for: .normal)
            secondary.setTitle("Not now", for: .normal)
        } else if isLast {
            primary.setTitle("Get started", for: .normal)
            secondary.setTitle(" ", for: .normal)
        } else {
            primary.setTitle("Next", for: .normal)
            secondary.setTitle("Skip", for: .normal)
        }
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        let w = max(scrollView.frame.width, 1)
        page = Int(round(scrollView.contentOffset.x / w))
        update()
    }

    private func go(to i: Int) {
        page = i
        scroll.setContentOffset(CGPoint(x: CGFloat(i) * scroll.frame.width, y: 0), animated: true)
        update()
    }

    @objc private func primaryTap() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if !isLast { go(to: page + 1); return }
        if current?.push == true { askForPush() } else { finish() }
    }

    @objc private func secondaryTap() {
        if isLast { finish() } else { go(to: config.onboarding.count - 1) }
    }

    private func askForPush() {
        #if canImport(OneSignalFramework)
        OneSignal.Notifications.requestPermission({ _ in
            DispatchQueue.main.async { self.finish() }
        }, fallbackToSettings: false)
        #else
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in
            DispatchQueue.main.async { self.finish() }
        }
        #endif
    }

    private func finish() {
        onDone?()
        dismiss(animated: true)
    }
}
