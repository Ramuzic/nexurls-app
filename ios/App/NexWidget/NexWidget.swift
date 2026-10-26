import WidgetKit
import SwiftUI
import UIKit

/// The NexURLs home screen widget: a site worth seeing today (from nexurls.com, refreshed every hour)
/// and one tap to Next. Small: Take me somewhere. Medium: today's pick with its picture.

private let paper = Color(red: 0.957, green: 0.941, blue: 0.910)
private let ink = Color(red: 0.071, green: 0.071, blue: 0.071)
private let sign = Color(red: 1.0, green: 0.353, blue: 0.122)
private let muted = Color(red: 0.42, green: 0.40, blue: 0.37)

struct NexPick: Decodable {
    let title: String
    let domain: String
    let image: String
    let url: String
    let likes: Int
}

struct NexFeed: Decodable {
    let total: Int
    let pick: NexPick?
}

struct NexEntry: TimelineEntry {
    let date: Date
    let total: Int
    let pick: NexPick?
    let image: UIImage?
}

struct NexProvider: TimelineProvider {
    func placeholder(in context: Context) -> NexEntry {
        NexEntry(date: Date(), total: 1800,
                 pick: NexPick(title: "A site worth a look", domain: "nexurls.com", image: "", url: "https://nexurls.com/app/", likes: 12),
                 image: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (NexEntry) -> Void) {
        if context.isPreview { completion(placeholder(in: context)); return }
        load(completion)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NexEntry>) -> Void) {
        load { entry in
            completion(Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(3600))))
        }
    }

    private func load(_ done: @escaping (NexEntry) -> Void) {
        guard let url = URL(string: "https://nexurls.com/wp-json/nxsf/v1/widget") else {
            done(NexEntry(date: Date(), total: 0, pick: nil, image: nil)); return
        }
        var req = URLRequest(url: url)
        req.timeoutInterval = 12
        URLSession.shared.dataTask(with: req) { data, _, _ in
            let feed = data.flatMap { try? JSONDecoder().decode(NexFeed.self, from: $0) }
            var picture: UIImage?
            if let s = feed?.pick?.image, !s.isEmpty, let u = URL(string: s),
               let d = try? Data(contentsOf: u), let full = UIImage(data: d) {
                // Widgets have little memory: keep the picture small.
                let w: CGFloat = 500
                let h = w * full.size.height / max(1, full.size.width)
                picture = full.preparingThumbnail(of: CGSize(width: w, height: h)) ?? full
            }
            done(NexEntry(date: Date(), total: feed?.total ?? 0, pick: feed?.pick, image: picture))
        }.resume()
    }
}

private extension View {
    @ViewBuilder func nexBackground(_ color: Color) -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            self.containerBackground(color, for: .widget)
        } else {
            self.background(color)
        }
    }
}

struct NexMark: View {
    var size: CGFloat = 15
    var body: some View {
        (Text("Nex").foregroundColor(ink) + Text("URLs").foregroundColor(sign))
            .font(.system(size: size, weight: .heavy))
    }
}

struct NexSmallView: View {
    let entry: NexEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            NexMark()
            Spacer(minLength: 0)
            Text(entry.total > 0 ? "\(entry.total.formatted()) places waiting" : "Somewhere new")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(muted)
            Text("Take me somewhere")
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(ink)
                .lineLimit(2)
            HStack(spacing: 4) {
                Text("Next").font(.system(size: 14, weight: .heavy))
                Image(systemName: "arrow.right").font(.system(size: 13, weight: .heavy))
            }
            .foregroundColor(ink)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Capsule().fill(sign))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .widgetURL(URL(string: "https://nexurls.com/?go=1"))
    }
}

struct NexMediumView: View {
    let entry: NexEntry
    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 14).fill(ink.opacity(0.08))
                if let img = entry.image {
                    Image(uiImage: img).resizable().scaledToFill()
                } else {
                    Image(systemName: "safari.fill").font(.system(size: 34)).foregroundColor(sign)
                }
            }
            .frame(width: 112)
            .clipShape(RoundedRectangle(cornerRadius: 14))

            VStack(alignment: .leading, spacing: 4) {
                NexMark(size: 13)
                Text("Worth a look today")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(muted)
                Text(entry.pick?.title ?? "Something new on the internet")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(ink)
                    .lineLimit(3)
                if let d = entry.pick?.domain, !d.isEmpty {
                    Text(d).font(.system(size: 11)).foregroundColor(muted).lineLimit(1)
                }
                Spacer(minLength: 0)
                Link(destination: URL(string: "https://nexurls.com/?go=1")!) {
                    HStack(spacing: 4) {
                        Text("Next").font(.system(size: 13, weight: .heavy))
                        Image(systemName: "arrow.right").font(.system(size: 12, weight: .heavy))
                    }
                    .foregroundColor(ink)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 5)
                    .background(Capsule().fill(sign))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .widgetURL(URL(string: entry.pick?.url ?? "https://nexurls.com/app/"))
    }
}

struct NexWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: NexEntry
    var body: some View {
        Group {
            if family == .systemMedium {
                NexMediumView(entry: entry)
            } else {
                NexSmallView(entry: entry)
            }
        }
        .nexBackground(paper)
    }
}

struct NexWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NexWidget", provider: NexProvider()) { entry in
            NexWidgetView(entry: entry)
        }
        .configurationDisplayName("NexURLs")
        .description("A site worth seeing today, and one tap to Next.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct NexWidgetBundle: WidgetBundle {
    var body: some Widget {
        NexWidget()
    }
}
