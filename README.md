# Market Ai

**AI-powered FX & synthetic trading analysis** — by Veltravia Technologies.

Upload your 4H and 15M chart screenshots, pick Scalp or Swing, and get an AI signal card with entry zone, stop loss, take profits, risk-reward and the reasoning behind every call.

## Stack

- Native **Kotlin + Jetpack Compose** (Material 3)
- Single-activity, edge-to-edge, dark-first "Terminal Luxury" design
- minSdk 24 · targetSdk 34

## Structure

```
app/src/main/java/com/veltravia/marketai/
├── MainActivity.kt
└── ui/
    ├── MarketAiApp.kt        # 5-tab scaffold (Home, Signals, Community, Saved, Profile)
    ├── screens/             # Screen composables
    └── theme/               # Colors, type, Material 3 theme
```

## Build

APKs are built automatically by GitHub Actions on every push to `main` — see the **Actions** tab.
