# MarketScope AI — iOS / Compose Multiplatform Migration Plan

Status: **scaffolded** (branch `kmp-scaffold`). Nothing on `main` is affected —
the Android app builds exactly as before. This branch adds a Kotlin
Multiplatform `:shared` module and a CI job that proves it compiles for iOS.

## What's already done on this branch

- **`:shared` Gradle module** (`shared/build.gradle.kts`) — targets: Android,
  iOS arm64 (device), iOS simulator arm64. Produces a static
  `MarketScopeShared.framework`.
- **`SharedApiClient`** (`shared/src/commonMain/.../SharedApiClient.kt`) — the
  full API surface of the Android `ApiClient.kt`, ported to Ktor +
  kotlinx-serialization (OkHttp engine on Android, Darwin engine on iOS).
  Every real endpoint is ported: auth, community (posts/polls/votes/pins/
  reactions/comments/views), analyses, chart analyze, trade plans, trial +
  subscription, daily signals (feed/stats/access/reactions/saves/comments/
  publish/close), markets/watchlist, trending + quotes, calendar (news +
  economic), push register + notifications.
  - Responses are `JsonElement` (kotlinx) instead of `org.json` types, because
    org.json is Android-only. Error semantics are preserved:
    `TrialExpiredException` (402 + trialExpired), `DailyLimitException`
    (429 + dailyLimitReached), `MarketAiException` (everything else, with
    `error` + `reason` merged exactly like the Android client).
- **`ProjectionEngine`** copied into shared (it was already pure Kotlin) —
  same math powers the iOS projection screen later.
- **CI proof** (`.github/workflows/ios-framework.yml`) — macOS-14 runner links
  release frameworks for device + simulator and packages
  `MarketScopeShared.xcframework` as an artifact. Green run = the shared code
  genuinely compiles for iOS via Kotlin/Native.

## Phase 2 — iOS app shell (needs the Apple Developer account)

1. **Xcode project** (`iosApp/`): SwiftUI app shell consuming
   `MarketScopeShared.xcframework`. Skeleton screens: Welcome (Google
   Sign-In via `GIDSignIn`), Home, Signals, Community, Calendar, Profile.
2. **Google Sign-In on iOS**: the same OAuth web client ID works — add an
   iOS URL scheme in the Xcode project + register the reversed client ID
   scheme. The backend `/api/auth/google` endpoint is already
   platform-agnostic.
3. **Push**: FCM has an iOS SDK, so the existing Firebase project
   (marketscope-ai-acf47) works — but iOS needs APNs auth key (p8) uploaded
   to Firebase + the app's push entitlements. Backend stays unchanged
   (same FCM v1 pipeline).
4. **Image pipeline**: `prepareChartImage` (photo picker + downscale to
   1600px JPEG-85) gets a Swift counterpart; the base64 payload contract is
   unchanged.
5. **Signing**: Apple Developer account ($99/yr) → App Store Connect app
   entry → TestFlight for the first installs.

## Phase 3 — Compose Multiplatform UI (optional, later)

Instead of writing all UI twice, migrate the Android Compose screens into a
`composeApp` shared source set (CMP supports iOS since Kotlin 2.0). This repo
is already on Kotlin 2.0 + Compose compiler plugin, so the migration is
incremental: screens move into commonMain one at a time, Android-specific bits
(Google Sign-In, FCM, photo picker, coil downloads) behind expect/actual.
Decision point: SwiftUI (Phase 2) vs CMP (Phase 3) — they can coexist: ship
SwiftUI first with the shared data layer, migrate UI later if desired.

## Gotchas learned / to expect

- `java.net.URLEncoder`, `java.util.UUID`, `org.json` do NOT exist in common
  Kotlin code — the shared client has pure-Kotlin replacements (`urlEncode`,
  `newUuid`). Keep this rule when porting more code.
- macOS CI minutes bill at 10x Linux minutes. The framework workflow is
  branch-gated for this reason.
- Kotlin/Native linking is slow (~5–10 min cold) — keep the workflow path-
  filtered to `shared/**`.
- Keep `ApiConfig.BASE_URL` as the single swappable source of truth — it is
  already shared, so the .com domain migration is a one-line change.
