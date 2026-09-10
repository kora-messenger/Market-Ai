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

---

## Premium Entitlement System

MarketScope AI Premium is a **central entitlement**, not a flag. A user has effective Premium when at least ONE of these holds:

| Tier | Source | Behavior |
|---|---|---|
| **Free** | default | 3 chart analyses per rolling 24h after the trial lapses; latest signal only |
| **Trial** | `users.trial_started_at` (7 days) | Full Premium while active |
| **Paid Premium** | `users.is_premium` (set by the Paystack `charge.success` webhook, verified server-side) | Full Premium; sticky until changed by the payment system |
| **Lifetime / Duration Premium** | `premium_grants` table (admin grant) | Full Premium until expiry; `lifetime` never expires until revoked |

### Database
- `premium_grants` — one ACTIVE grant per user (partial unique index `premium_grants_one_active`): `duration_type` (`lifetime|months|years`), `duration_count`, `expires_at` (NULL = lifetime), `reason`, `granted_by`, `granted_by_email`, `granted_at`, `revoked_at`, `revoked_by`.
- `premium_audit` — every admin grant/revoke: target, admin, action, kind, expiry, reason, previous/new status, timestamp.

### Entitlement logic
All protected endpoints (`/api/trial/status`, `/api/analyze`, `/api/analyze/stock`, `/api/daily-signals/access`, `/api/daily-signals`) call `getActivePremiumGrant()` and treat effective premium = paid subscription OR active trial OR active admin grant. The backend is the authority; the client never declares premium.

### Admin API (admin session required — 403 otherwise)
- `GET /api/admin/premium/users?search=` — search by email / name / id
- `GET /api/admin/premium/status/:userId` — full status snapshot (paid, trial, grant, history)
- `POST /api/admin/premium/grant` — `{userId, durationType: lifetime|months|years, durationCount?, reason?}` (409 on duplicate active grant)
- `POST /api/admin/premium/revoke` — `{userId}`; removes ONLY the admin grant, recalculates effective access, never touches paid subscriptions
- `GET /api/admin/premium/audit` — recent grant/revoke activity

### Grant flow
Admin (Team Console → Premium management) searches a user, picks Lifetime / N months / N years + optional reason, confirms. The backend saves the entitlement, writes the audit row, then notifies the user by **email** (Brevo — "Congratulations, you've been given free Lifetime Premium… Expires: Lifetime / <date>") **and in-app notification + push** (FCM). Revoke notifies the user too, telling them whether they remain Premium. Subscription activation (Paystack) similarly emails "Your MarketScope AI Premium activation was successful" + in-app notification.

### Frontend
- `AdminPremiumScreen` (route `premium_admin`) — search, user cards, premium status, grant dialog (duration + reason), revoke confirmation, Premium Activity feed. Loading/disabled states throughout; user IDs are never displayed.
- Profile plan chip shows `LIFETIME` (violet) for lifetime-grant users, `PRO` for paid, trial countdown, or `FREE`.
