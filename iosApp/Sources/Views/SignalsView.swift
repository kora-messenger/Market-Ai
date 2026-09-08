import SwiftUI

struct SignalCard: Identifiable {
    let id = UUID()
    let display: String
    let direction: String
    let strength: String
    let entry: Double
    let stopLoss: Double
    let status: String
    let thesis: String
    let dateText: String
}

struct SignalsView: View {
    @EnvironmentObject var session: SessionStore
    @State private var stats: [String: Any]?
    @State private var signals: [SignalCard] = []
    @State private var loading = true
    @State private var locked = false
    @State private var errorText: String?

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Daily Signals")
                        .font(.title.bold()).foregroundColor(Theme.textPrimary)
                    if loading {
                        ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                    } else if locked {
                        VStack(spacing: 10) {
                            Image(systemName: "lock.fill").font(.system(size: 30)).foregroundColor(Theme.accentViolet)
                            Text("The full signals history is part of MarketScope Premium.\nThe latest signal stays free every day.")
                                .font(.footnote).foregroundColor(Theme.textMuted)
                                .multilineTextAlignment(.center)
                        }
                        .cardStyle().frame(maxWidth: .infinity)
                    } else if let errorText {
                        Text(errorText).font(.footnote).foregroundColor(Theme.textMuted)
                            .frame(maxWidth: .infinity).padding(.top, 20)
                    } else {
                        if let stats {
                            HStack(spacing: 10) {
                                StatBox(label: "This month", value: "\(stats["total"] as? Int ?? 0)")
                                StatBox(label: "In progress", value: "\(stats["inProgress"] as? Int ?? 0)")
                                StatBox(label: "Avg R:R", value: String(format: "%.2f", stats["avgRR"] as? Double ?? 0))
                            }
                        }
                        if signals.isEmpty {
                            Text("No signals published yet — the AI publishes one each morning, and the team posts curated calls.")
                                .font(.footnote).foregroundColor(Theme.textMuted)
                                .multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(.top, 20)
                        }
                        ForEach(signals) { s in
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text(s.display).font(.system(size: 16, weight: .bold)).foregroundColor(Theme.textPrimary)
                                    Text(s.direction.uppercased())
                                        .font(.system(size: 10, weight: .heavy))
                                        .padding(.horizontal, 8).padding(.vertical, 3)
                                        .background(Capsule().fill(s.direction == "long" ? Theme.bullGreen.opacity(0.15) : Theme.bearRed.opacity(0.15)))
                                        .foregroundColor(s.direction == "long" ? Theme.bullGreen : Theme.bearRed)
                                    Spacer()
                                    Text(s.status.replacingOccurrences(of: "_", with: " ").capitalized)
                                        .font(.system(size: 11, weight: .semibold)).foregroundColor(Theme.textMuted)
                                }
                                Text(s.dateText).font(.caption).foregroundColor(Theme.textMuted)
                                HStack(spacing: 8) {
                                    LevelBox(label: "Entry", value: String(format: "%.4f", s.entry), color: Theme.accentViolet)
                                    LevelBox(label: "SL", value: String(format: "%.4f", s.stopLoss), color: Theme.bearRed)
                                    LevelBox(label: "Conviction", value: s.strength.capitalized, color: Theme.goldAmber)
                                }
                                if !s.thesis.isEmpty {
                                    Text(s.thesis).font(.footnote).foregroundColor(Theme.textMuted).lineLimit(4)
                                }
                            }
                            .cardStyle()
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
        loading = true; errorText = nil; locked = false
        do {
            stats = try? await Api.signalStats(range: "month")
            if let token = session.token {
                let feed = try await Api.signalsFeed(token: token)
                if let arr = feed["signals"] as? [[String: Any]] {
                    signals = arr.prefix(10).compactMap(makeCard)
                }
            }
        } catch let error as NSError {
            if error.userInfo["KotlinException"] != nil,
               String(describing: error).contains("trialExpired") || error.code == 402 {
                locked = true
            } else {
                errorText = error.localizedDescription
            }
        } catch {
            errorText = error.localizedDescription
        }
        loading = false
    }

    private func makeCard(_ r: [String: Any]) -> SignalCard? {
        guard let display = r["instrumentDisplay"] as? String,
              let entry = r["entry"] as? Double else { return nil }
        let iso = r["publishedAt"] as? String ?? ""
        let df = ISO8601DateFormatter()
        let text: String
        if let d = df.date(from: iso) {
            let f = DateFormatter(); f.dateStyle = .medium; f.timeStyle = .short
            text = f.string(from: d)
        } else { text = iso }
        return SignalCard(
            display: display,
            direction: r["direction"] as? String ?? "long",
            strength: r["strength"] as? String ?? "moderate",
            entry: entry,
            stopLoss: r["stopLoss"] as? Double ?? 0,
            status: r["status"] as? String ?? "pending",
            thesis: r["thesis"] as? String ?? "",
            dateText: text
        )
    }
}

struct StatBox: View {
    let label: String
    let value: String
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label.uppercased()).font(.system(size: 10, weight: .bold)).foregroundColor(Theme.textMuted)
            Text(value).font(.system(size: 17, weight: .bold)).foregroundColor(Theme.textPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardStyle()
    }
}

struct LevelBox: View {
    let label: String
    let value: String
    let color: Color
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased()).font(.system(size: 9, weight: .bold)).foregroundColor(Theme.textMuted)
            Text(value).font(.system(size: 13, weight: .semibold)).foregroundColor(color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color.white))
    }
}
