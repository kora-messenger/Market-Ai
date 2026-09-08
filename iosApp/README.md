# MarketScope AI — iOS shell (scaffold)

SwiftUI app consuming the Kotlin Multiplatform `:shared` module
(`MarketScopeShared.framework`). Real data flows: watchlist, daily signals,
community stats/feed, calendar (news + economic events), trial status —
all via `SharedApiClient` (Ktor + Darwin engine).

## Build (CI)

`.github/workflows/ios-framework.yml` on this branch does everything on a
macos-14 runner: Kotlin frameworks → xcframework → XcodeGen project →
unsigned simulator build → `.app` artifact.

## Build locally (Mac required)

1. `./gradlew :shared:linkReleaseFrameworkIosArm64 :shared:linkReleaseFrameworkIosSimulatorArm64`
2. `xcodebuild -create-xcframework -framework shared/build/bin/iosArm64/releaseFramework/MarketScopeShared.framework -framework shared/build/bin/iosSimulatorArm64/releaseFramework/MarketScopeShared.framework -output build/xcframework/MarketScopeShared.xcframework`
3. `cd iosApp && xcodegen generate`
4. Open `MarketScope.xcodeproj` in Xcode, pick an iOS Simulator, run.

## Still to wire (needs the Apple Developer account / GCP iOS client)

- **Google Sign-In**: real GIDSignIn code is in `GoogleAuth.swift`. It reads
  `GOOGLE_IOS_CLIENT_ID` from Info.plist (project.yml). Register an *iOS* OAuth
  client in the existing `market-ai-507812` GCP project with bundle id
  `com.veltravia.marketscopeai` (reverse-client-ID URL scheme added by XcodeGen
  once set), then set `GOOGLE_IOS_CLIENT_ID` in project.yml. Until then the
  welcome button is honestly disabled with a note.
- **Signing/TestFlight**: CODE_SIGNING_ALLOWED=NO on CI for now; device builds
  and TestFlight start once the Apple Developer account exists.
- **Push**: FCM iOS SDK + APNs p8 in the marketscope-ai-acf47 Firebase project.
- **Chart analysis**: Swift `prepareChartImage` counterpart (photo picker +
  downscale; base64 contract unchanged).
- **Questionnaire / onboarding**: port the 3-screen flow once sign-in is live.
