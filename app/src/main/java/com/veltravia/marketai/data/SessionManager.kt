package com.veltravia.marketai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GoogleUser(
    val name: String,
    val email: String,
    val picture: String
)

data class QuestionnaireAnswers(
    val experience: String,
    val style: String,
    val goal: String,
    val markets: List<String>,
    val timeframes: List<String>,
    val losingPlan: String,
    val emotions: String,
    val routine: String,
    val avoidConditions: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("experience", experience)
        put("style", style)
        put("goal", goal)
        put("markets", JSONArray(markets))
        put("timeframes", JSONArray(timeframes))
        put("losingPlan", losingPlan)
        put("emotions", emotions)
        put("routine", routine)
        put("avoidConditions", avoidConditions)
    }

    companion object {
        fun fromJson(json: JSONObject): QuestionnaireAnswers = QuestionnaireAnswers(
            experience = json.optString("experience", ""),
            style = json.optString("style", ""),
            goal = json.optString("goal", ""),
            markets = json.optJSONArray("markets")?.let { arr ->
                List(arr.length()) { arr.optString(it) }
            } ?: emptyList(),
            timeframes = json.optJSONArray("timeframes")?.let { arr ->
                List(arr.length()) { arr.optString(it) }
            } ?: emptyList(),
            losingPlan = json.optString("losingPlan", ""),
            emotions = json.optString("emotions", ""),
            routine = json.optString("routine", ""),
            avoidConditions = json.optString("avoidConditions", "")
        )
    }
}

object SessionManager {

    private const val PREFS = "marketai_session"
    private const val KEY_NAME = "user_name"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_PICTURE = "user_picture"
    private const val KEY_QUESTIONNAIRE = "questionnaire_json"
    private const val KEY_QUESTIONNAIRE_DONE = "questionnaire_done"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveUser(context: Context, user: GoogleUser) {
        prefs(context).edit()
            .putString(KEY_NAME, user.name)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_PICTURE, user.picture)
            .apply()
    }

    fun currentUser(context: Context): GoogleUser? {
        val name = prefs(context).getString(KEY_NAME, null) ?: return null
        val email = prefs(context).getString(KEY_EMAIL, "") ?: ""
        val picture = prefs(context).getString(KEY_PICTURE, "") ?: ""
        return GoogleUser(name, email, picture)
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

    /** Personalized coaching line, same logic as the reference app. */
    fun coachingLine(answers: QuestionnaireAnswers): String {
        val emotions = answers.emotions.lowercase()
        if (emotions.contains("impatience")) {
            return "Slow is smooth, smooth becomes fast. Your edge is in waiting for A-setups, not more trades."
        }
        if (answers.experience.equals("Beginner", ignoreCase = true)) {
            return "Clarity beats speed. One A-setup repeated consistently is how discipline compounds."
        }
        return "Consistency is a system: same checklist, same risk, same trigger—again and again."
    }
}
