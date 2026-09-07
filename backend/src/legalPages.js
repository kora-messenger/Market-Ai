/**
 * Terms of Service & Privacy Policy — MarketScope AI, by Veltravia Technologies.
 * Served as real static pages from the live API so the app's welcome-screen
 * links open genuine, current legal text (not placeholders).
 */

const EFFECTIVE_DATE = "September 7, 2026";
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
<p>MarketScope AI lets you upload chart screenshots of financial instruments (forex, metals,
indices, crypto, and synthetic instruments) and receive an AI-generated technical analysis,
including a suggested trade direction, entry zone, stop loss, take-profit levels, and a
written rationale ("Signals").</p>

<h2>2. Not Financial Advice</h2>
<p><strong>Signals are AI-generated technical commentary, not financial, investment, tax, or
legal advice.</strong> They are produced by a large-language-model reading the chart images
you upload and can be wrong, outdated, or based on an incomplete picture of the market.
Trading foreign exchange, indices, crypto assets, and synthetic instruments carries a high
level of risk and may not be suitable for all investors. You are solely responsible for any
trading or investment decision you make. Past performance shown by any Signal, sample, or
outcome tracker is not indicative of future results. Veltravia is not a licensed broker,
financial adviser, or investment manager, and no Signal constitutes a recommendation to buy
or sell any instrument.</p>

<h2>3. Eligibility &amp; Account</h2>
<p>You must be at least 18 years old to use the App. You are responsible for keeping your
account credentials secure and for all activity under your account. We reserve the right to
suspend or terminate accounts that violate these Terms, are used fraudulently, or are used to
abuse or overload the service.</p>

<h2>4. Subscriptions &amp; Payments</h2>
<p>Certain features (including higher daily Signal limits) are offered as a paid subscription
("Pro"). Prices are shown in the App before purchase and are billed in accordance with the
payment method you choose. Subscriptions renew automatically until cancelled. You can cancel
at any time; access to Pro features continues until the end of the current billing period.
Fees already charged are non-refundable except where required by law.</p>

<h2>5. Acceptable Use</h2>
<p>You agree not to: reverse-engineer or scrape the App or its API; resell or redistribute
Signals as your own trading advisory service without our written permission; upload content
you do not have the right to upload; or use the App to violate any applicable law or
regulation, including securities and financial-promotion laws in your jurisdiction.</p>
<p>In the Community you agree to keep content honest and legal: no spam, scams,
misleading trade claims, harassment, or unauthorized promotion. We may remove
content or restrict Community access for accounts that violate these rules.</p>

<h2>6. Your Content</h2>
<p>You retain ownership of the chart images you upload. You grant Veltravia a limited license
to process those images (including sending them to our AI analysis provider) solely to
generate your Signal and improve the App's analysis quality. See our
<a href="/privacy">Privacy Policy</a> for details on how uploaded images and data are handled.</p>
<p>The same applies to content you post in the Community (posts, images, comments
and reactions): you keep ownership, and you grant us a limited license to display
it to other members of the Community. You are responsible for what you choose
to share publicly there, and we may moderate Community content at our discretion.</p>

<h2>7. Disclaimer of Warranties</h2>
<p>The App and all Signals are provided "as is" and "as available," without warranties of any
kind, whether express or implied, including accuracy, reliability, or fitness for a
particular purpose. We do not warrant that the App will be uninterrupted, error-free, or that
any Signal will result in a profitable outcome.</p>

<h2>8. Limitation of Liability</h2>
<p>To the maximum extent permitted by law, Veltravia Technologies and its team will not be
liable for any trading losses, lost profits, or indirect, incidental, special, or
consequential damages arising from your use of, or reliance on, the App or any Signal, even
if advised of the possibility of such damages. Our total aggregate liability for any claim
relating to the App is limited to the amount you paid us in the 3 months preceding the claim,
or USD 50, whichever is greater.</p>

<h2>9. Changes to the App or These Terms</h2>
<p>We may update the App and these Terms from time to time. If we make material changes, we
will update the "Effective" date above and, where required by law, notify you in the App.
Continued use of the App after changes take effect means you accept the updated Terms.</p>

<h2>10. Termination</h2>
<p>You may stop using the App at any time. We may suspend or terminate your access if you
breach these Terms or if required by law.</p>

<h2>11. Governing Law</h2>
<p>These Terms are governed by the laws applicable to Veltravia Technologies' place of
business, without regard to conflict-of-law principles, except where local consumer-protection
law provides otherwise.</p>

<h2>12. Contact</h2>
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
do not receive or store your Google password.</p>
<p><strong>Trading profile.</strong> Your answers to the onboarding questionnaire (experience
level, trading style, goals, markets, timeframes, and free-text answers) so the App and its AI
analysis can be tailored to you.</p>
<p><strong>Chart images.</strong> The chart screenshots you upload for analysis. These are
sent to our AI analysis provider to generate your Signal and are stored so you can view your
analysis history inside the App.</p>
<p><strong>Community content.</strong> The posts, images, comments and reactions you
create in the Community are shared with other members, with your display name attached.
Everything else in this section stays between you and us.</p>
<p><strong>Usage data.</strong> Basic technical data such as app version, device model, and
crash diagnostics, used only to keep the App stable.</p>

<h2>2. How We Use Your Information</h2>
<ul>
<li>To create your account and let you sign in.</li>
<li>To generate AI Signals from your uploaded charts and trading profile.</li>
<li>To show you your analysis history ("Saved").</li>
<li>To operate subscriptions and payments for Pro features.</li>
<li>To run the trader Community — showing members the posts, images, comments and
reactions that traders choose to share.</li>
<li>To run Daily Signals and weekly Community features (leaderboard, featured proof).</li>
<li>To maintain, secure, and improve the App.</li>
</ul>
<p>We do not sell your personal information.</p>

<h2>3. Who We Share Data With</h2>
<p>We share data only with service providers strictly necessary to run the App:</p>
<ul>
<li><strong>Google</strong> — to verify your sign-in.</li>
<li><strong>Our AI analysis provider</strong> — receives the two chart images and your
questionnaire answers to generate each Signal. Images are transmitted for the purpose of
generating that Signal only.</li>
<li><strong>Our hosting and database provider</strong> — stores your account, questionnaire
answers, and analysis history securely.</li>
<li><strong>Payment processor</strong> — handles subscription billing; we do not store your
full card details ourselves.</li>
</ul>
<p>We do not share your data with advertisers or data brokers.</p>

<h2>4. Data Retention</h2>
<p>We keep your account and analysis history for as long as your account is active, so you can
review past Signals. You can request deletion of your account and associated data at any time
by contacting us — see Section 7.</p>

<h2>5. Data Security</h2>
<p>We use industry-standard measures (encrypted connections, access-controlled databases) to
protect your data. No method of transmission or storage is 100% secure, and we cannot
guarantee absolute security.</p>

<h2>6. Your Rights</h2>
<p>Depending on where you live, you may have the right to access, correct, export, or delete
your personal data, or to object to certain processing. To exercise any of these rights,
contact us using the details below and we will respond within a reasonable time.</p>

<h2>7. Deleting Your Account</h2>
<p>To delete your account and all associated data (questionnaire answers, uploaded charts, and
analysis history), email <a href="mailto:${CONTACT_EMAIL}">${CONTACT_EMAIL}</a> from the
address associated with your account. We will confirm once deletion is complete.</p>

<h2>8. Children's Privacy</h2>
<p>The App is not directed to anyone under 18, and we do not knowingly collect data from
children.</p>

<h2>9. Changes to This Policy</h2>
<p>We may update this Policy from time to time. Material changes will update the "Effective"
date above and, where required, be communicated in the App.</p>

<h2>10. Contact</h2>
<p>Questions about this Policy or your data? Email
<a href="mailto:${CONTACT_EMAIL}">${CONTACT_EMAIL}</a>.</p>
`
  );
}

module.exports = { termsOfServiceHtml, privacyPolicyHtml };
