/**
 * Terms of Service & Privacy Policy — MarketScope AI, by Veltravia Technologies.
 * Served as real static pages from the live API so the app's welcome-screen
 * links open genuine, current legal text (not placeholders).
 *
 * Content rule: these pages are PUBLIC. Describe categories of data and
 * providers generically ("our AI analysis provider", "our hosting provider").
 * Never name backend vendors, models, infra hosts, admin accounts, or expose
 * any credential material here.
 */

const EFFECTIVE_DATE = "September 9, 2026";
const CONTACT_EMAIL = "support@veltraviatech.com";

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

function page(title, body) {
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>${title} — MarketScope AI</title>
<style>${baseStyle}</style>
</head>
<body>
<div class="wrap">
${body}
</div>
</body>
</html>`;
}

function termsOfServiceHtml() {
  return page(
    "Terms of Service",
    `
<h1>Terms of Service</h1>
<p class="updated">Effective ${EFFECTIVE_DATE}</p>

<p>These Terms of Service ("Terms") govern your use of MarketScope AI (the "App"), provided by
Veltravia Technologies ("Veltravia", "we", "us"). By creating an account or using the App,
you agree to these Terms. If you do not agree, do not use the App.</p>

<h2>1. What MarketScope AI Is</h2>
<p>MarketScope AI is a trading-companion app. Its features include:</p>
<ul>
<li><strong>AI chart analysis</strong> — you upload chart screenshots of financial instruments
(forex, metals, indices, crypto, and synthetic instruments) and receive an AI-generated
technical analysis, including a suggested trade direction, entry zone, stop loss,
take-profit levels, and a written rationale.</li>
<li><strong>Daily Signals</strong> — AI-generated and team-curated trade ideas, with live
updates from our mentor desk and a discussion area where traders can comment and share
trade screenshots.</li>
<li><strong>Markets</strong> — a live watchlist across futures, forex, and crypto, with
interactive candlestick charts, plus a news feed and an economic calendar.</li>
<li><strong>Trading tools</strong> — a trade-plan builder and a risk calculator.</li>
<li><strong>Community</strong> — a members-only feed where traders share posts, images,
polls, comments, and reactions, with weekly engagement highlights.</li>
</ul>

<h2>2. Not Financial Advice</h2>
<p><strong>All Signals, analyses, and other market commentary in the App are AI-generated or
team-opinion technical commentary — not financial, investment, tax, or legal advice.</strong>
They can be wrong, outdated, or based on an incomplete picture of the market. Trading
foreign exchange, indices, crypto assets, and synthetic instruments carries a high level of
risk and may not be suitable for all investors. You are solely responsible for any trading
or investment decision you make. Past performance shown by any Signal, sample, or
community-posted outcome is not indicative of future results. Veltravia is not a licensed
broker, financial adviser, or investment manager, and nothing in the App constitutes a
recommendation to buy or sell any instrument. Risk-calculator and lot-size outputs are
mathematical estimates only — verify all figures before trading.</p>

<h2>3. Market Data &amp; News</h2>
<p>Prices, charts, news, and economic-calendar events shown in the App are supplied by
third-party data providers. Quotes may be delayed, incomplete, or inaccurate, and can never
be assumed to be execution-quality. Do not rely on the App's data alone to enter, manage, or
exit live trades; always confirm prices with your broker before acting.</p>

<h2>4. Eligibility &amp; Account</h2>
<p>You must be at least 18 years old to use the App. You are responsible for keeping your
account credentials secure and for all activity under your account. We reserve the right to
suspend or terminate accounts that violate these Terms, are used fraudulently, or are used to
abuse or overload the service.</p>

<h2>5. Trial, Free Tier, Subscriptions &amp; Payments</h2>
<p>New accounts receive a free trial of premium features. When the trial ends, the account
moves to a free tier with a limited number of AI analyses per day, or you can upgrade to a
paid subscription ("Pro") for unrestricted access. Trial terms and any prices are shown in
the App before you commit to anything. Subscriptions renew automatically until cancelled.
You can cancel at any time; access to Pro features continues until the end of the current
billing period. Fees already charged are non-refundable except where required by law.</p>

<h2>6. Broker Referrals</h2>
<p>The App may recommend a third-party broker and include a referral link. If you sign up with
a broker through such a link, we may receive referral compensation from the broker at no
additional cost to you. Any brokerage relationship, account terms, and execution services are
solely between you and that broker — Veltravia is not a party to it, does not operate the
broker, and has no custody of your funds. A referral is not financial advice.</p>

<h2>7. Acceptable Use</h2>
<p>You agree not to: reverse-engineer or scrape the App or its API; resell or redistribute
Signals as your own trading advisory service without our written permission; upload content
you do not have the right to upload; or use the App to violate any applicable law or
regulation, including securities and financial-promotion laws in your jurisdiction.</p>
<p>In the Community and in signal discussions you agree to keep content honest and legal:
no spam, scams, misleading trade claims, harassment, or unauthorized promotion. Screenshots
attached to signal comments are reviewed before they become visible to other members, and we
may remove content or restrict Community access for accounts that violate these rules.</p>

<h2>8. Your Content</h2>
<p>You retain ownership of the chart images you upload. You grant Veltravia a limited license
to process those images (including sending them to our AI analysis provider) solely to
generate your Signal and improve the App's analysis quality. See our
<a href="/privacy">Privacy Policy</a> for details on how uploaded images and data are handled.</p>
<p>The same applies to content you post in the Community or in signal discussions (posts,
images, polls, comments and reactions): you keep ownership, and you grant us a limited
license to display it to other members. Engagement metrics (reactions, view counts, and
weekly highlights) may be shown publicly alongside your display name. You are responsible
for what you choose to share publicly there, and we may moderate Community content at our
discretion.</p>

<h2>9. Disclaimer of Warranties</h2>
<p>The App and all Signals are provided "as is" and "as available," without warranties of any
kind, whether express or implied, including accuracy, reliability, or fitness for a
particular purpose. We do not warrant that the App will be uninterrupted, error-free, or that
any Signal will result in a profitable outcome.</p>

<h2>10. Limitation of Liability</h2>
<p>To the maximum extent permitted by law, Veltravia Technologies and its team will not be
liable for any trading losses, lost profits, or indirect, incidental, special, or
consequential damages arising from your use of, or reliance on, the App or any Signal, even
if advised of the possibility of such damages. Our total aggregate liability for any claim
relating to the App is limited to the amount you paid us in the 3 months preceding the claim,
or USD 50, whichever is greater.</p>

<h2>11. Changes to the App or These Terms</h2>
<p>We may update the App and these Terms from time to time. If we make material changes, we
will update the "Effective" date above and, where required by law, notify you in the App.
Continued use of the App after changes take effect means you accept the updated Terms.</p>

<h2>12. Termination &amp; Account Deletion</h2>
<p>You may stop using the App at any time, and you can request account deletion directly from
the App's settings. A deletion request enters a 30-day grace period during which you can
cancel it from the App; after that period your account and associated data are permanently
erased. We may suspend or terminate your access if you breach these Terms or if required by
law.</p>

<h2>13. Governing Law</h2>
<p>These Terms are governed by the laws applicable to Veltravia Technologies' place of
business, without regard to conflict-of-law principles, except where local consumer-protection
law provides otherwise.</p>

<h2>14. Contact</h2>
<p>Questions about these Terms? Email <a href="mailto:${CONTACT_EMAIL}">${CONTACT_EMAIL}</a>.</p>
`
  );
}

function privacyPolicyHtml() {
  return page(
    "Privacy Policy",
    `
<h1>Privacy Policy</h1>
<p class="updated">Effective ${EFFECTIVE_DATE}</p>

<p>This Privacy Policy explains what data MarketScope AI (the "App"), provided by Veltravia
Technologies ("Veltravia", "we", "us"), collects, why, and how it is used.</p>

<h2>1. Information We Collect</h2>
<p><strong>Account information.</strong> When you sign in with Google, we receive your name,
email address, and profile picture URL from Google to create and identify your account. We
do not receive or store your Google password. If your account has no profile picture, a
standard MarketScope AI illustration is used as your avatar instead.</p>
<p><strong>Trading profile.</strong> Your answers to the onboarding questionnaire (experience
level, trading style, goals, markets, timeframes, and free-text answers) so the App and its AI
analysis can be tailored to you.</p>
<p><strong>Chart images.</strong> The chart screenshots you upload for analysis. These are
sent to our AI analysis provider to generate your Signal and are stored so you can view your
analysis history inside the App. Screenshots you attach to signal comments are held for
review and only become visible to other members once approved.</p>
<p><strong>Community content.</strong> The posts, images, polls, comments and reactions you
create in the Community or in signal discussions are shared with other members, with your
display name attached. Engagement metrics (reactions, view counts, weekly highlights) may be
shown publicly alongside that content. Everything else in this section stays between you
and us.</p>
<p><strong>Notification data.</strong> If you enable push notifications, we store a device
push token and basic platform information so signals and community updates can reach you.
You can turn notifications off at any time in your device settings or in the App, and the
token is removed when it is no longer valid.</p>
<p><strong>Usage data.</strong> Basic technical data such as app version, device model, and
crash diagnostics, used only to keep the App stable. When you sign in, we may also record
the sign-in time and general device type so we can send you a security notice email about
the activity on your account.</p>

<h2>2. How We Use Your Information</h2>
<ul>
<li>To create your account and let you sign in.</li>
<li>To generate AI Signals from your uploaded charts and trading profile.</li>
<li>To show you your analysis history ("Saved") and your saved trade plans.</li>
<li>To deliver the live watchlist, charts, news, and economic calendar.</li>
<li>To operate the trial, free tier, and Pro subscriptions.</li>
<li>To send the notifications you have enabled (signals, community, and general updates).</li>
<li>To send account and security emails, such as a welcome email or a sign-in security
notice.</li>
<li>To run the trader Community — showing members the posts, images, comments and
reactions that traders choose to share, plus engagement highlights.</li>
<li>To maintain, secure, and improve the App.</li>
</ul>
<p>We do not sell your personal information.</p>

<h2>3. Who We Share Data With</h2>
<p>We share data only with service providers strictly necessary to run the App:</p>
<ul>
<li><strong>Google</strong> — to verify your sign-in and to deliver push notifications to
Android devices.</li>
<li><strong>Our AI analysis provider</strong> — receives the chart images and your
questionnaire answers to generate each Signal. Images are transmitted for the purpose of
generating that Signal only.</li>
<li><strong>Our hosting, database, and email-delivery providers</strong> — store your
account, questionnaire answers, analysis history, and send transactional account/security
emails on our behalf.</li>
<li><strong>Payment processor</strong> — handles subscription billing; we do not store your
full card details ourselves.</li>
<li><strong>Third-party market-data and news providers</strong> — supply prices, charts,
news, and calendar data. These are data sources we request data <em>from</em>; no personal
data about you is sent to them when you browse market information.</li>
</ul>
<p>Opening a broker referral link takes you to an external broker's website, which has its
own privacy practices — your use of that site is not covered by this Policy.</p>
<p>We do not share your data with advertisers or data brokers.</p>

<h2>4. Data Retention &amp; Deleting Your Account</h2>
<p>We keep your account and analysis history for as long as your account is active, so you can
review past Signals. You can request deletion of your account and associated data directly
from the App's settings ("Delete my account"). The request enters a 30-day grace period
during which you can cancel it from the App; after that period, your account, questionnaire
answers, uploaded content, and analysis history are permanently erased. You can also email
us using the address in Section 6.</p>

<h2>5. Data Security</h2>
<p>We use industry-standard measures (encrypted connections, access-controlled databases) to
protect your data. No method of transmission or storage is 100% secure, and we cannot
guarantee absolute security.</p>

<h2>6. Your Rights &amp; Contact</h2>
<p>Depending on where you live, you may have the right to access, correct, export, or delete
your personal data, or to object to certain processing. To exercise any of these rights, or
for any question about this Policy or your data, email
<a href="mailto:${CONTACT_EMAIL}">${CONTACT_EMAIL}</a> and we will respond within a
reasonable time.</p>

<h2>7. Children's Privacy</h2>
<p>The App is not directed to anyone under 18, and we do not knowingly collect data from
children.</p>

<h2>8. Changes to This Policy</h2>
<p>We may update this Policy from time to time. Material changes will update the "Effective"
date above and, where required by law, be communicated in the App.</p>
`
  );
}

module.exports = { termsOfServiceHtml, privacyPolicyHtml };
