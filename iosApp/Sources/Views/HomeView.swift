import SwiftUI

struct WatchRow: Identifiable {
    let id = UUID()
    let section: String
    let display: String
    let subtitle: String
    let price: Double
    let changePct: Double?
}

struct HomeView: View {
    @EnvironmentObject var session: SessionStore
    @State private var rows: [WatchRow] = []
    @State private var trialDays: Int?
    @State private var isPremium = false
    @State private var loading = true
    @State private var errorText: String?

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Hello, \(session.name?.split(separator: " ").first.map(String.init) ?? "Trader")")
                                .font(.title2.bold())
                                .foregroundColor(Theme.textPrimary)
                            Text(Theme.dateLine())
                                .font(.subheadline)
                                .foregroundColor(Theme.textMuted)
                        }
                        Spacer()
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Theme.brandGradient)
                            .frame(width: 40, height: 40)
                            .overlay(
                                Image(systemName: "chart.line.uptrend.xyaxis")
                                    .foregroundColor(.white).font(.system(size: 17, weight: .bold))
                            )
                    }

                    if let days = trialDays, !isPremium {
                        HStack {
                            Image(systemName: "crown.fill").foregroundColor(Theme.goldAmber)
                            Text(days > 0
                                 ? "Free trial — \(days) day\(days == 1 ? "" : "s") remaining"
                                 : "Free trial ended — subscribe for unlimited analyses")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(Theme.textPrimary)
                        }
                        .cardStyle().frame(maxWidth: .infinity, alignment: .leading)
                    } else if isPremium {
                        HStack {
                            Image(systemName: "crown.fill").foregroundColor(Theme.goldAmber)
                            Text("Premium member — unlimited access")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(Theme.textPrimary)
                        }
                        .cardStyle().frame(maxWidth: .infinity, alignment: .leading)
                    }

                    if loading {
                        ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                    } else if let errorText {
                        VStack(spacing: 10) {
                            Text(errorText).font(.footnote).foregroundColor(Theme.textMuted)
                                .multilineTextAlignment(.center)
                            Button("Retry") { Task { await load() } }
                                .font(.system(size: 14, weight: .semibold)).foregroundColor(Theme.accentViolet)
                        }
                        .frame(maxWidth: .infinity).padding(.top, 30)
                    } else {
                        ForEach(groupSections(), id: \.0) { section, sectionRows in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(section.uppercased())
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(Theme.textMuted)
                                ForEach(sectionRows) { row in
                                    HStack {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(row.display).font(.system(size: 15, weight: .semibold))
                                                .foregroundColor(Theme.textPrimary)
                                            Text(row.subtitle).font(.caption)
                                                .foregroundColor(Theme.textMuted).lineLimit(1)
                                        }
                                        Spacer()
                                        VStack(alignment: .trailing, spacing: 2) {
                                            Text(row.price.formatted(.number.precision(.fractionLength(2...4))))
                                                .font(.system(size: 15, weight: .bold))
                                                .foregroundColor(Theme.textPrimary)
                                            if let pct = row.changePct {
                                                Text(String(format: "%+.2f%%", pct))
                                                    .font(.system(size: 12, weight: .semibold))
                                                    .foregroundColor(pct >= 0 ? Theme.bullGreen : Theme.bearRed)
                                            }
                                        }
                                    }
                                    .cardStyle()
                                }
                            }
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

    private func groupSections() -> [(String, [WatchRow])] {
        var order: [String] = []
        var groups: [String: [WatchRow]] = [:]
        for row in rows {
            if groups[row.section] == nil { order.append(row.section) }
            groups[row.section, default: []].append(row)
        }
        return order.compactMap { s in groups[s].map { (s, $0) } }
    }

    private func load() async {
        loading = true; errorText = nil
        do {
            let arr = try await Api.watchlist()
            rows = arr.compactMap { r in
                guard let display = r["display"] as? String,
                      let price = r["price"] as? Double else { return nil }
                return WatchRow(
                    section: r["section"] as? String ?? "Markets",
                    display: display,
                    subtitle: r["subtitle"] as? String ?? "",
                    price: price,
                    changePct: r["changePct"] as? Double
                )
            }
            if let token = session.token {
                let trial = try? await Api.trialStatus(token: token)
                trialDays = trial?["trialDaysRemaining"] as? Int
                isPremium = (trial?["isPremium"] as? Bool) ?? false
            }
        } catch {
            errorText = error.localizedDescription
        }
        loading = false
    }
}

extension Theme {
    static func dateLine() -> String {
        let f = DateFormatter()
        f.dateStyle = .full
        return f.string(from: Date())
    }
}
