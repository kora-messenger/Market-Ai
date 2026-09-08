import SwiftUI

struct CommunityView: View {
    @EnvironmentObject var session: SessionStore
    @State private var members = 0
    @State private var posts: [[String: Any]] = []
    @State private var loading = true
    @State private var errorText: String?

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Community")
                        .font(.title.bold()).foregroundColor(Theme.textPrimary)
                    if loading {
                        ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                    } else if let errorText {
                        Text(errorText).font(.footnote).foregroundColor(Theme.textMuted)
                            .multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(.top, 20)
                    } else {
                        HStack(spacing: 10) {
                            StatBox(label: "Members", value: "\(members)")
                            StatBox(label: "Posts", value: "\(posts.count)")
                        }
                        if posts.isEmpty {
                            Text("No posts yet — be the first to share an insight or chart.")
                                .font(.footnote).foregroundColor(Theme.textMuted)
                                .frame(maxWidth: .infinity).padding(.top, 16)
                        }
                        ForEach(Array(posts.enumerated()), id: \.offset) { _, r in
                            PostRow(post: r)
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
            if let stats = try? await Api.communityStats() {
                members = stats["totalMembers"] as? Int ?? 0
            }
            if let token = session.token, let feed = try? await Api.communityFeed(token: token) {
                posts = feed["posts"] as? [[String: Any]] ?? []
            }
        } catch {
            errorText = error.localizedDescription
        }
        loading = false
    }
}

struct PostRow: View {
    let post: [String: Any]
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Circle().fill(Theme.brandGradient).frame(width: 34, height: 34)
                    .overlay(Text(initials).foregroundColor(.white).font(.system(size: 12, weight: .bold)))
                VStack(alignment: .leading, spacing: 1) {
                    Text(post["authorName"] as? String ?? "Trader")
                        .font(.system(size: 14, weight: .semibold)).foregroundColor(Theme.textPrimary)
                    Text(relativeTime(post["createdAt"] as? String))
                        .font(.caption2).foregroundColor(Theme.textMuted)
                }
                Spacer()
            }
            Text(post["body"] as? String ?? "")
                .font(.system(size: 14)).foregroundColor(Theme.textPrimary)
        }
        .cardStyle()
    }

    private var initials: String {
        let name = post["authorName"] as? String ?? "T"
        return String(name.prefix(1)).uppercased()
    }

    private func relativeTime(_ iso: String?) -> String {
        guard let iso, let d = ISO8601DateFormatter().date(from: iso) else { return "" }
        let f = RelativeDateTimeFormatter()
        return f.localizedString(for: d, relativeTo: Date())
    }
}
