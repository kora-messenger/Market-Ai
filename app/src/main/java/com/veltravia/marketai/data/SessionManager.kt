package com.veltravia.marketai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GoogleUser(
    val name: String,
    val email: String,
    val picture: String
)

data class UserSession(
    val user: GoogleUser,
    val sessionToken: String?,
    val communityJoined: Boolean,
    val trialActive: Boolean = true,
    val trialDaysRemaining: Int = 7,
    val isPremium: Boolean = false
)

data class QuestionnaireAnswers(
    val experience: String,
    val goal: String,
    val capitalUsd: String,
    val assets: List<String>,
    val style: String,
    val timeframes: List<String>,
    val entryCriteria: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("experience", experience)
        put("goal", goal)
        put("capitalUsd", capitalUsd)
        put("assets", JSONArray(assets))
        put("style", style)
        put("timeframes", JSONArray(timeframes))
        put("entryCriteria", entryCriteria)
    }

    companion object {
        fun fromJson(json: JSONObject): QuestionnaireAnswers = QuestionnaireAnswers(
            experience = json.optString("experience", ""),
            goal = json.optString("goal", ""),
            capitalUsd = json.optString("capitalUsd", ""),
            assets = json.optJSONArray("assets")?.let { arr ->
                List(arr.length()) { arr.optString(it) }
            } ?: json.optJSONArray("markets")?.let { arr ->
                // tolerate answers saved by the pre-rebuild questionnaire
                List(arr.length()) { arr.optString(it) }
            } ?: emptyList(),
            style = json.optString("style", ""),
            timeframes = json.optJSONArray("timeframes")?.let { arr ->
                List(arr.length()) { arr.optString(it) }
            } ?: emptyList(),
            entryCriteria = json.optString("entryCriteria", "")
        )
    }
}

object SessionManager {

    private const val PREFS = "marketai_session"
    private const val KEY_NAME = "user_name"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_PICTURE = "user_picture"
    private const val KEY_SESSION_TOKEN = "session_token"
    private const val KEY_COMMUNITY_JOINED = "community_joined"
    private const val KEY_NOTIFICATIONS_PROMPT_SHOWN = "notifications_prompt_shown"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_PROJECTION_INTRO_SHOWN = "projection_intro_shown"
    private const val KEY_BROKER_SETUP_SHOWN = "broker_setup_shown"
    private const val KEY_BROKER_CHOICE = "broker_choice"
    private const val KEY_SCREENSHOT_GUIDE_SHOWN = "screenshot_guide_shown"
    private const val KEY_TRIAL_ACTIVE = "trial_active"
    private const val KEY_TRIAL_DAYS_REMAINING = "trial_days_remaining"
    private const val KEY_IS_PREMIUM = "is_premium"
    private const val KEY_QUESTIONNAIRE = "questionnaire_json"
    private const val KEY_QUESTIONNAIRE_DONE = "questionnaire_done"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Persists the profile plus the server-issued session JWT and community membership flag. */
    fun saveSession(context: Context, session: UserSession) {
        prefs(context).edit()
            .putString(KEY_NAME, session.user.name)
            .putString(KEY_EMAIL, session.user.email)
            .putString(KEY_PICTURE, session.user.picture)
            .putString(KEY_SESSION_TOKEN, session.sessionToken)
            .putBoolean(KEY_COMMUNITY_JOINED, session.communityJoined)
            .putBoolean(KEY_TRIAL_ACTIVE, session.trialActive)
            .putInt(KEY_TRIAL_DAYS_REMAINING, session.trialDaysRemaining)
            .putBoolean(KEY_IS_PREMIUM, session.isPremium)
            .apply()
    }

    /** Updates just the trial/premium fields (e.g. after re-checking /api/trial/status). */
    fun updateTrialState(context: Context, trialActive: Boolean, trialDaysRemaining: Int, isPremium: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_TRIAL_ACTIVE, trialActive)
            .putInt(KEY_TRIAL_DAYS_REMAINING, trialDaysRemaining)
            .putBoolean(KEY_IS_PREMIUM, isPremium)
            .apply()
    }

    fun trialActive(context: Context): Boolean = prefs(context).getBoolean(KEY_TRIAL_ACTIVE, true)

    fun trialDaysRemaining(context: Context): Int = prefs(context).getInt(KEY_TRIAL_DAYS_REMAINING, 7)

    fun isPremium(context: Context): Boolean = prefs(context).getBoolean(KEY_IS_PREMIUM, false)

    fun currentUser(context: Context): GoogleUser? {
        val name = prefs(context).getString(KEY_NAME, null) ?: return null
        val email = prefs(context).getString(KEY_EMAIL, "") ?: ""
        val picture = prefs(context).getString(KEY_PICTURE, "") ?: ""
        return GoogleUser(name, email, picture)
    }

    /** Bearer token for authenticated backend calls (community join, etc.), or null if unset. */
    fun sessionToken(context: Context): String? =
        prefs(context).getString(KEY_SESSION_TOKEN, null)

    fun communityJoined(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COMMUNITY_JOINED, false)

    fun setCommunityJoined(context: Context, joined: Boolean) {
        prefs(context).edit().putBoolean(KEY_COMMUNITY_JOINED, joined).apply()
    }

    fun notificationsPromptShown(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATIONS_PROMPT_SHOWN, false)

    fun setNotificationsPromptShown(context: Context, shown: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS_PROMPT_SHOWN, shown).apply()
    }

    fun notificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATIONS_ENABLED, false)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun projectionIntroShown(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROJECTION_INTRO_SHOWN, false)

    fun setProjectionIntroShown(context: Context, shown: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROJECTION_INTRO_SHOWN, shown).apply()
    }

    fun brokerSetupShown(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BROKER_SETUP_SHOWN, false)

    fun setBrokerSetupShown(context: Context, shown: Boolean) {
        prefs(context).edit().putBoolean(KEY_BROKER_SETUP_SHOWN, shown).apply()
    }

    fun brokerChoice(context: Context): String? =
        prefs(context).getString(KEY_BROKER_CHOICE, null)

    fun setBrokerChoice(context: Context, choice: String) {
        prefs(context).edit().putString(KEY_BROKER_CHOICE, choice).apply()
    }

    fun screenshotGuideShown(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCREENSHOT_GUIDE_SHOWN, false)

    fun setScreenshotGuideShown(context: Context, shown: Boolean) {
        prefs(context).edit().putBoolean(KEY_SCREENSHOT_GUIDE_SHOWN, shown).apply()
    }

    fun signOut(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun saveQuestionnaire(context: Context, answers: QuestionnaireAnswers) {
        prefs(context).edit()
            .putString(KEY_QUESTIONNAIRE, answers.toJson().toString())
            .putBoolean(KEY_QUESTIONNAIRE_DONE, true)
            .apply()
    }

    fun questionnaireAnswers(context: Context): QuestionnaireAnswers? {
        val raw = prefs(context).getString(KEY_QUESTIONNAIRE, null) ?: return null
        return runCatching { QuestionnaireAnswers.fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun questionnaireDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_QUESTIONNAIRE_DONE, false)

    /** Personalized coaching line derived from the real questionnaire answers. */
    fun coachingLine(answers: QuestionnaireAnswers): String {
        if (answers.experience.equals("Beginner", ignoreCase = true)) {
            return "Clarity beats speed. One A-setup repeated consistently is how discipline compounds."
        }
        if (answers.style.contains("Scalping", ignoreCase = true)) {
            return "Scalping rewards precision, not frequency. Protect your focus like it's capital."
        }
        return "Consistency is a system: same checklist, same risk, same trigger—again and again."
    }
}
