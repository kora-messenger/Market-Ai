import SwiftUI

struct CalendarView: View {
    @State private var mode = 0 // 0 = News, 1 = Economic events
    @State private var news: [[String: Any]] = []
    @State private var events: [[String: Any]] = []
    @State private var loading = true
    @State private var errorText: String?

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Calendar")
                        .font(.title.bold()).foregroundColor(Theme.textPrimary)

                    Picker("", selection: $mode) {
                        Text("News").tag(0)
                        Text("Economic Calendar").tag(1)
                    }
                    .pickerStyle(.segmented)

                    if loading {
                        ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                    } else if let errorText {
                        VStack(spacing: 8) {
                            Text(errorText).font(.footnote).foregroundColor(Theme.textMuted)
                            Button("Retry") { Task { await load() } }
                                .font(.system(size: 14, weight: .semibold)).foregroundColor(Theme.accentViolet)
                        }
                        .frame(maxWidth: .infinity).padding(.top, 24)
                    } else if mode == 0 {
                        ForEach(Array(news.enumerated()), id: \.offset) { _, item in
                            NewsRow(item: item)
                        }
                    } else {
                        ForEach(Array(events.enumerated()), id: \.offset) { _, ev in
                            EventRow(event: ev)
                        }
                    }
                }
                .padding(16)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationBarHidden(true)
        }
        .navigationViewStyle(.stack)
        .task { await load() }
    }

    private func load() async {
        loading = true; errorText = nil
        do {
            if news.isEmpty {
                news = try await Api.marketNews(category: "all")
            }
            if events.isEmpty {
                events = try await Api.economicCalendar()
            }
        } catch {
            errorText = error.localizedDescription
        }
        loading = false
    }
}

struct NewsRow: View {
    let item: [String: Any]
    var body: some View {
        Button {
            if let link = item["link"] as? String, let url = URL(string: link) {
                UIApplication.shared.open(url)
            }
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(item["source"] as? String ?? "Market News")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(Capsule().fill(Theme.accentCyan))
                    Spacer()
                    if let time = item["publishedAt"] as? String,
                       let d = ISO8601DateFormatter().date(from: time) {
                        Text(RelativeDateTimeFormatter().localizedString(for: d, relativeTo: Date()))
                            .font(.caption2).foregroundColor(Theme.textMuted)
                    }
                }
                Text(item["title"] as? String ?? "")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                    .multilineTextAlignment(.leading)
                if let cat = item["category"] as? String {
                    Text(cat.capitalized).font(.caption2).foregroundColor(Theme.textMuted)
                }
            }
            .cardStyle()
        }
        .buttonStyle(.plain)
    }
}

struct EventRow: View {
    let event: [String: Any]
    private var timeText: String? {
        guard let time = event["timestamp"] as? String,
              let d = ISO8601DateFormatter().date(from: time) else { return nil }
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .short
        return f.string(from: d)
    }

    private var impactColor: Color {
        switch event["impact"] as? String ?? "" {
        case "High": return Theme.bearRed
        case "Medium": return Theme.goldAmber
        default: return Theme.textMuted
        }
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Circle().fill(impactColor).frame(width: 8, height: 8)
                Text((event["country"] as? String ?? "").uppercased())
                    .font(.system(size: 11, weight: .heavy)).foregroundColor(Theme.textMuted)
                Spacer()
                if let text = timeText {
                    Text(text).font(.caption).foregroundColor(Theme.textMuted)
                }
            }
            Text(event["title"] as? String ?? "")
                .font(.system(size: 14, weight: .semibold)).foregroundColor(Theme.textPrimary)
            HStack(spacing: 16) {
                valuePair("Forecast", event["forecast"] as? String)
                valuePair("Previous", event["previous"] as? String)
                valuePair("Actual", event["actual"] as? String)
            }
        }
        .cardStyle()
    }

    private func valuePair(_ label: String, _ value: String?) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label.uppercased()).font(.system(size: 9, weight: .bold)).foregroundColor(Theme.textMuted)
            Text(value ?? "—").font(.system(size: 12, weight: .semibold)).foregroundColor(Theme.textPrimary)
        }
    }
}
