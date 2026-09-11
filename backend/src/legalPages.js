/**
 * Terms of Service & Privacy Policy — MarketScope AI, by Veltravia Technologies.
 * Served from the live API in TWO forms from ONE source of truth:
 *   - HTML pages at /terms and /privacy (public links, Play-Store ready)
 *   - JSON at /api/legal/:doc (rendered natively inside the app)
 *
 * Content rule: these pages are PUBLIC. Never expose any credential material
 * or admin accounts here.
 */

const EFFECTIVE_DATE = "September 11, 2026";
const CONTACT_EMAIL = "support@veltraviatech.com";
const CONTACT_HREF = "mailto:support@veltraviatech.com";

/**
 * Structured policy documents (single source of truth for web + app).
 * Block types:
 *   { type: "p",  text, emphasis? }  — paragraph (emphasis = bold lead-in)
 *   { type: "ul", items: [ ... ] }   — bullet list
 *   { type: "link", text, href }     — tappable external link
 */
const TERMS_SECTIONS = [
  {
    heading: "1. What MarketScope AI Is",
    blocks: [
      { type: "p", text: "MarketScope AI is a trading-companion app. Its features include:" },
      { type: "ul", items: [
        "AI chart analysis — you upload chart screenshots of financial instruments (forex, metals, indices, crypto, and stocks) and receive an AI-generated technical analysis, including a suggested trade direction, entry zone, stop loss, take-profit levels, and a written rationale.",
        "Daily Signals — AI-generated and team-curated trade ideas, with live updates from our mentor desk and a discussion area where traders can comment and share trade screenshots.",
        "Stock monitoring — when the AI calls a BUY on a stock, it can watch the price for you and alert you when your stop loss or take-profit levels are hit.",
        "Markets — a live watchlist across futures, forex, and crypto, with interactive candlestick charts, plus a news feed and an economic calendar.",
        "Trading tools — a trade-plan builder and a risk calculator.",
        "Community — a members-only feed where traders share posts, images, polls, comments, and reactions, with weekly engagement highlights."
      ] }
    ]
  },
  {
    heading: "2. Not Financial Advice",
    blocks: [
      { type: "p", emphasis: true, text: "All Signals, analyses, and other market commentary in the App are AI-generated or team-opinion technical commentary — not financial, investment, tax, or legal advice." },
      { type: "p", text: "They can be wrong, outdated, or based on an incomplete picture of the market. Trading foreign exchange, indices, crypto assets, and stocks carries a high level of risk and may not be suitable for all investors. You are solely responsible for any trading or investment decision you make. Past performance shown by any Signal, sample, or community-posted outcome is not indicative of future results." },
      { type: "p", text: "Veltravia Technologies is not a licensed broker, financial adviser, or investment manager, and nothing in the App constitutes a recommendation to buy or sell any instrument. Risk-calculator and lot-size outputs are mathematical estimates only — verify all figures before trading." }
    ]
  },
  {
    heading: "3. Market Data & News",
    blocks: [
      { type: "p", text: "Prices, charts, news, and economic-calendar events shown in the App are supplied by third-party data providers. Quotes may be delayed, incomplete, or inaccurate, and can never be assumed to be execution-quality. Do not rely on the App's data alone to enter, manage, or exit live trades; always confirm prices with your broker before acting." }
    ]
  },
  {
    heading: "4. Eligibility & Account",
    blocks: [
      { type: "p", text: "You must be at least 18 years old to use the App. You are responsible for keeping your account credentials secure and for all activity under your account. We reserve the right to suspend or terminate accounts that violate these Terms, are used fraudulently, or are used to abuse or overload the service." }
    ]
  },
  {
    heading: "5. Free Tier, Trial, Subscriptions & Payments",
    blocks: [
      { type: "p", text: "New accounts receive a free trial of premium features. When the trial ends, the account moves to the free tier (a limited number of AI analyses per day), or you can upgrade to a paid subscription (\"Pro\") for unrestricted, ad-free access." },
      { type: "p", text: "Trial terms and any prices are shown in the App before you commit to anything. Subscriptions renew automatically until cancelled. You can cancel at any time; access to Pro features continues until the end of the current billing period. Fees already charged are non-refundable except where required by law." },
      { type: "p", text: "Subscription payments are processed by our payment processor, Paystack, on behalf of Veltravia Technologies. We never receive or store your full card details — we only receive your name, email, and subscription status. Refund and chargeback handling follows the payment processor's and your bank's standard procedures and applicable law." }
    ]
  },
  {
    heading: "6. Advertising in the App",
    blocks: [
      { type: "p", text: "The free tier is funded by advertising, so it stays free. If you consent to ads through Google's consent flow, the App may show you ads supplied by Google AdMob and its certified ad networks — including native ads inside the news feed (always clearly labeled as ads), occasional interstitial ads after an analysis, and rewarded video ads." },
      { type: "p", text: "Rewarded videos are always optional: when you reach your daily free analysis limit, you can choose to watch one short video to unlock one extra analysis for that day. Watching is your choice, and the bonus is added only after the video finishes. Ad placements and frequency are controlled on our servers." },
      { type: "p", text: "Ads never appear during onboarding, never interrupt an in-progress analysis, and are never disguised as Signals, news, or community content. Pro subscribers see no ads at all." }
    ]
  },
  {
    heading: "7. Broker Referrals",
    blocks: [
      { type: "p", text: "The App may recommend a third-party broker and include a referral link. If you sign up with a broker through such a link, we may receive referral compensation from the broker at no additional cost to you. Any brokerage relationship, account terms, and execution services are solely between you and that broker — Veltravia is not a party to it, does not operate the broker, and has no custody of your funds. A referral is not financial advice." }
    ]
  },
  {
    heading: "8. Acceptable Use",
    blocks: [
      { type: "p", text: "You agree not to: reverse-engineer or scrape the App or its API; resell or redistribute Signals as your own trading advisory service without our written permission; upload content you do not have the right to upload; or use the App to violate any applicable law or regulation, including securities and financial-promotion laws in your jurisdiction." },
      { type: "p", text: "In the Community and in signal discussions you agree to keep content honest and legal: no spam, scams, misleading trade claims, harassment, or unauthorized promotion. Screenshots attached to signal comments are reviewed before they become visible to other members, and we may remove content or restrict Community access for accounts that violate these rules." }
    ]
  },
  {
    heading: "9. Your Content",
    blocks: [
      { type: "p", text: "You retain ownership of the chart images you upload. You grant Veltravia a limited license to process those images (including sending them to our AI analysis provider) solely to generate your Signal and improve the App's analysis quality. See our Privacy Policy for details on how uploaded images and data are handled." },
      { type: "p", text: "The same applies to content you post in the Community or in signal discussions (posts, images, polls, comments and reactions): you keep ownership, and you grant us a limited license to display it to other members. Engagement metrics (reactions, view counts, and weekly highlights) may be shown publicly alongside your display name. You are responsible for what you choose to share publicly there, and we may moderate Community content at our discretion." }
    ]
  },
  {
    heading: "10. Disclaimer of Warranties",
    blocks: [
      { type: "p", text: "The App and all Signals are provided \"as is\" and \"as available,\" without warranties of any kind, whether express or implied, including accuracy, reliability, or fitness for a particular purpose. We do not warrant that the App will be uninterrupted, error-free, or that any Signal will result in a profitable outcome." }
    ]
  },
  {
    heading: "11. Limitation of Liability",
    blocks: [
      { type: "p", text: "To the maximum extent permitted by law, Veltravia Technologies and its team will not be liable for any trading losses, lost profits, or indirect, incidental, special, or consequential damages arising from your use of, or reliance on, the App or any Signal, even if advised of the possibility of such damages. Our total aggregate liability for any claim relating to the App is limited to the amount you paid us in the 3 months preceding the claim, or USD 50, whichever is greater." }
    ]
  },
  {
    heading: "12. Changes to the App or These Terms",
    blocks: [
      { type: "p", text: "We may update the App and these Terms from time to time. If we make material changes, we will update the \"Effective\" date above and, where required by law, notify you in the App. Continued use of the App after changes take effect means you accept the updated Terms." }
    ]
  },
  {
    heading: "13. Termination & Account Deletion",
    blocks: [
      { type: "p", text: "You may stop using the App at any time, and you can request account deletion directly from the App's settings. A deletion request enters a 30-day grace period during which you can cancel it from the App; after that period your account and associated data are permanently erased. We may suspend or terminate your access if you breach these Terms or if required by law." }
    ]
  },
  {
    heading: "14. Governing Law",
    blocks: [
      { type: "p", text: "These Terms are governed by the laws applicable to Veltravia Technologies' place of business, without regard to conflict-of-law principles, except where local consumer-protection law provides otherwise." }
    ]
  },
  {
    heading: "15. Contact",
    blocks: [
      { type: "p", text: "Questions about these Terms? Email us — we respond within a reasonable time." },
      { type: "link", text: CONTACT_EMAIL, href: CONTACT_HREF }
    ]
  }
];

const PRIVACY_SECTIONS = [
  {
    heading: "1. Information We Collect",
    blocks: [
      { type: "p", emphasis: true, text: "Account information. When you sign in with Google, we receive your name, email address, and profile picture URL from Google to create and identify your account. We do not receive or store your Google password. If your account has no profile picture, a standard MarketScope AI illustration is used as your avatar instead." },
      { type: "p", emphasis: true, text: "Trading profile. Your answers to the onboarding questionnaire (experience level, trading style, goals, markets, timeframes, and free-text answers) so the App and its AI analysis can be tailored to you." },
      { type: "p", emphasis: true, text: "Chart images. The chart screenshots you upload for analysis. These are sent to our AI analysis provider to generate your Signal and are stored so you can view your analysis history inside the App. Screenshots you attach to signal comments are held for review and only become visible to other members once approved." },
      { type: "p", emphasis: true, text: "Community content. The posts, images, polls, comments and reactions you create in the Community or in signal discussions are shared with other members, with your display name attached. Engagement metrics (reactions, view counts, weekly highlights) may be shown publicly alongside that content. Everything else in this Policy stays between you and us." },
      { type: "p", emphasis: true, text: "Notification data. If you enable push notifications, we store a device push token and basic platform information so signals and community updates can reach you. You can turn notifications off at any time in your device settings or in the App, and the token is removed when it is no longer valid." },
      { type: "p", emphasis: true, text: "Advertising data. If you are on the free tier and consent to ads through Google's consent flow, Google's AdMob advertising SDK collects standard advertising identifiers and technical data (such as device advertising ID, IP address, and coarse device characteristics) to deliver and measure ads. This collection is done by Google and its ad-network partners under Google's own privacy policy — see Section 2 for your choices." },
      { type: "p", emphasis: true, text: "Usage data. Basic technical data such as app version, device model, and crash diagnostics, used only to keep the App stable. When you sign in, we may also record the sign-in time and general device type so we can send you a security notice email about the activity on your account." }
    ]
  },
  {
    heading: "2. Advertising Data & Your Choices",
    blocks: [
      { type: "p", text: "The free tier of the App is funded by advertising. On first use you are shown Google's official consent form (User Messaging Platform), where you choose whether ads may be personalized using your device's advertising identifier and whether measurement data may be shared with Google's ad partners. Your choice is respected: if you decline, you still see ads, but they are not personalized with your identifiers." },
      { type: "p", text: "You can change or withdraw your ad-consent choice at any time from the App's settings (Ad privacy) or by resetting your device's advertising ID in Android settings. Pro subscribers never see ads, and no advertising identifier is used for them." },
      { type: "p", text: "For more about how Google processes ad data, see Google's \"How Google uses information when you use our partners' sites or apps\" page and the Ads settings available at adssettings.google.com while signed in to your Google account." }
    ]
  },
  {
    heading: "3. How We Use Your Information",
    blocks: [
      { type: "p", text: "We use the information above only to:" },
      { type: "ul", items: [
        "Create your account and let you sign in.",
        "Generate AI Signals from your uploaded charts and trading profile.",
        "Show you your analysis history (\"Saved\") and your saved trade plans.",
        "Deliver the live watchlist, charts, news, and economic calendar.",
        "Operate the trial, free tier, and Pro subscriptions, and grant the extra analyses you unlock by watching rewarded videos.",
        "Show you ads that fund the free tier, in line with the consent choices you made in Google's consent form.",
        "Send the notifications you have enabled (signals, community, and general updates).",
        "Send account and security emails, such as a welcome email or a sign-in security notice.",
        "Run the trader Community — showing members the posts, images, comments and reactions that traders choose to share, plus engagement highlights.",
        "Maintain, secure, and improve the App."
      ] },
      { type: "p", text: "We do not sell your personal information." }
    ]
  },
  {
    heading: "4. Who We Share Data With",
    blocks: [
      { type: "p", text: "We share data only with service providers strictly necessary to run the App:" },
      { type: "ul", items: [
        "Google — to verify your sign-in, to deliver push notifications to Android devices, and (for free-tier users who consented) to deliver and measure advertising through Google AdMob and its certified ad-network partners.",
        "Our AI analysis provider — receives the chart images and your questionnaire answers to generate each Signal. Images are transmitted for the purpose of generating that Signal only.",
        "Paystack, our payment processor — handles subscription billing on our behalf. Paystack receives the payment details you enter at checkout; we never receive or store your full card details, only your name, email, and subscription status.",
        "Our hosting, database, and email-delivery providers — store your account, questionnaire answers, analysis history, and send transactional account/security emails on our behalf.",
        "Third-party market-data and news providers — supply prices, charts, news, and calendar data. These are data sources we request data from; no personal data about you is sent to them when you browse market information."
      ] },
      { type: "p", text: "Opening a broker referral link takes you to an external broker's website, which has its own privacy practices — your use of that site is not covered by this Policy." },
      { type: "p", text: "We do not share your data with any other advertisers or data brokers." }
    ]
  },
  {
    heading: "5. Data Retention & Deleting Your Account",
    blocks: [
      { type: "p", text: "We keep your account and analysis history for as long as your account is active, so you can review past Signals. You can request deletion of your account and associated data directly from the App's settings (\"Delete my account\"). The request enters a 30-day grace period during which you can cancel it from the App; after that period your account, questionnaire answers, uploaded content, and analysis history are permanently erased. You can also email us using the address in Section 7." }
    ]
  },
  {
    heading: "6. Data Security",
    blocks: [
      { type: "p", text: "We use industry-standard measures (encrypted connections, access-controlled databases) to protect your data. No method of transmission or storage is 100% secure, and we cannot guarantee absolute security." }
    ]
  },
  {
    heading: "7. Your Rights & Contact",
    blocks: [
      { type: "p", text: "Depending on where you live, you may have the right to access, correct, export, or delete your personal data, or to object to certain processing. To exercise any of these rights, or for any question about this Policy or your data, email us and we will respond within a reasonable time." },
      { type: "link", text: CONTACT_EMAIL, href: CONTACT_HREF }
    ]
  },
  {
    heading: "8. Children's Privacy",
    blocks: [
      { type: "p", text: "The App is not directed to anyone under 18, and we do not knowingly collect data from children." }
    ]
  },
  {
    heading: "9. Changes to This Policy",
    blocks: [
      { type: "p", text: "We may update this Policy from time to time. Material changes will update the \"Effective\" date above and, where required by law, be communicated in the App." }
    ]
  }
];


const COMMUNITY_SECTIONS = [
  {
    heading: "1. Real Traders, Honest Content",
    blocks: [
      { type: "p", text: "Share real charts, real trades, and real results. Never post fabricated or edited trade \"proofs\", screenshots that are not yours, or claims of profits you did not make. Win shares (\"Share Your Win\") are reviewed by the mentor team before they appear on the Wall of Wins — keep them genuine." },
      { type: "p", text: "Outcomes you tag on your posts (for example \"Profited\" or \"Lesson learned\") must reflect what actually happened. Members rely on each other's honesty." }
    ]
  },
  {
    heading: "2. Respect Each Other",
    blocks: [
      { type: "p", text: "No harassment, hate speech, slurs, personal attacks, or targeted abuse. Disagree with ideas, not people. Bullish and bearish debates are welcome — abuse is not." }
    ]
  },
  {
    heading: "3. No Spam, Scams, or Promotion",
    blocks: [
      { type: "p", text: "Do not sell signals, offer \"managed accounts\", guaranteed-returns schemes, copy-trading deals, or any paid service in the Community. Do not post invite links to WhatsApp, Telegram, or Discord groups, or bait members into direct messages (\"DM me for signals\")." },
      { type: "p", text: "Off-platform links in posts and comments are automatically checked by our moderation systems, and questionable links do not post. Circumventing these checks (for example by disguising a link) is itself a violation." }
    ]
  },
  {
    heading: "4. Trading Content Standards",
    blocks: [
      { type: "p", text: "Keep posts relevant to trading and markets. Screenshots attached to signal comments are reviewed by our team before they become visible to other members. Do not upload content you do not have the right to share — including other people's paid signals or copyrighted courses." }
    ]
  },
  {
    heading: "5. Privacy of Others",
    blocks: [
      { type: "p", text: "Do not post other people's personal information — names, email addresses, phone numbers, or account statements. If it is not yours to share, do not share it." }
    ]
  },
  {
    heading: "6. Enforcement",
    blocks: [
      { type: "p", text: "We moderate the Community at our discretion. Content that breaks these Guidelines may be removed, and accounts that violate them can have their Community access restricted or be suspended or terminated under our Terms of Service. Severe or repeat violations end in account termination." }
    ]
  },
  {
    heading: "7. Flagging Content & Questions",
    blocks: [
      { type: "p", text: "To report a post, comment, or member, email us with the details — we review every report. The same address answers any question about these Guidelines." },
      { type: "link", text: CONTACT_EMAIL, href: CONTACT_HREF }
    ]
  }
];

const DOCS = {
  terms: { title: "Terms of Service", sections: TERMS_SECTIONS, path: "/terms" },
  privacy: { title: "Privacy Policy", sections: PRIVACY_SECTIONS, path: "/privacy" },
  community: { title: "Community Guidelines", sections: COMMUNITY_SECTIONS, path: "/community-guidelines" }
};

// --- HTML rendering (public web pages) ---

const baseStyle = `
  body { background:#0B0E14; color:#E7ECF5; font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif; line-height:1.65; margin:0; padding:0; }
  .wrap { max-width:720px; margin:0 auto; padding:40px 24px 80px; }
  h1 { font-size:26px; margin-bottom:4px; }
  h2 { font-size:18px; margin-top:36px; color:#5AD1E6; }
  p, li { color:#B7C0D1; font-size:15px; }
  .updated { color:#7A8499; font-size:13px; margin-bottom:32px; }
  a { color:#5AD1E6; }
  strong { color:#E7ECF5; }
`;

function esc(s) {
  return String(s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function renderBlocksHtml(blocks) {
  return blocks.map((b) => {
    if (b.type === "p") {
      return b.emphasis
        ? `<p><strong>${esc(b.text)}</strong></p>`
        : `<p>${esc(b.text)}</p>`;
    }
    if (b.type === "ul") {
      return `<ul>${b.items.map((i) => `<li>${esc(i)}</li>`).join("\n")}</ul>`;
    }
    if (b.type === "link") {
      return `<p><a href="${esc(b.href)}">${esc(b.text)}</a></p>`;
    }
    return "";
  }).join("\n");
}

function renderDocHtml(docKey) {
  const doc = DOCS[docKey];
  const body = doc.sections.map((s) =>
    `<h2>${esc(s.heading)}</h2>\n${renderBlocksHtml(s.blocks)}`
  ).join("\n");
  const others = Object.entries(DOCS).filter(([k]) => k !== docKey)
    .map(([, d]) => `<a href="${esc(d.path)}">${esc(d.title)}</a>`)
    .join(' &nbsp;<span style="color:#7A8499">·</span>&nbsp; ');
  const footer = `<p style="margin-top:48px;color:#7A8499;font-size:13px">More policies: ${others}</p>`;
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>${esc(doc.title)} — MarketScope AI</title>
<style>${baseStyle}</style>
</head>
<body>
<div class="wrap">
<h1>${esc(doc.title)}</h1>
<p class="updated">Effective ${esc(EFFECTIVE_DATE)}</p>
${body}
${footer}
</div>
</body>
</html>`;
}

function termsOfServiceHtml() { return renderDocHtml("terms"); }
function privacyPolicyHtml() { return renderDocHtml("privacy"); }
function communityGuidelinesHtml() { return renderDocHtml("community"); }

module.exports = { termsOfServiceHtml, privacyPolicyHtml, communityGuidelinesHtml };
