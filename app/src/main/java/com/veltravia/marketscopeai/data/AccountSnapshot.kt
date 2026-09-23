package com.veltravia.marketscopeai.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File
import java.security.MessageDigest

/** Last confirmed account data, scoped to the signed-in email. Never uses a Google photo. */
data class AccountSnapshot(
    val username: String?, val avatarUrl: String?, val analysesCount: Int?,
    val savedTradesCount: Int?, val savedPlanCount: Int?
)

object AccountSnapshotCache {
    private const val PREFS = "marketai_account_snapshot"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(email: String) = MessageDigest.getInstance("SHA-256")
        .digest(email.trim().lowercase().toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    private fun imageFile(context: Context, email: String) = File(context.filesDir, "own-avatar-${key(email)}.img")

    fun read(context: Context, email: String): AccountSnapshot? {
        if (email.isBlank()) return null
        val p = prefs(context)
        if (p.getString("owner", null) != email.trim().lowercase()) return null
        return AccountSnapshot(
            p.getString("username", null), p.getString("avatar", null),
            p.getInt("analyses", -1).takeIf { it >= 0 },
            p.getInt("saved", -1).takeIf { it >= 0 },
            p.getInt("plans", -1).takeIf { it >= 0 }
        )
    }

    fun save(context: Context, email: String, snapshot: AccountSnapshot) {
        if (email.isBlank()) return
        prefs(context).edit().putString("owner", email.trim().lowercase())
            .putString("username", snapshot.username)
            .putString("avatar", snapshot.avatarUrl)
            .putInt("analyses", snapshot.analysesCount ?: -1)
            .putInt("saved", snapshot.savedTradesCount ?: -1)
            .putInt("plans", snapshot.savedPlanCount ?: -1).apply()
    }

    /** Private on-device copy means the owner's photo appears immediately, even offline. */
    fun avatarUri(context: Context, email: String): String? =
        imageFile(context, email).takeIf { it.isFile && it.length() > 0 }?.let { Uri.fromFile(it).toString() + "?v=" + it.lastModified() }

    fun saveAvatar(context: Context, email: String, bytes: ByteArray): String? {
        if (email.isBlank() || bytes.isEmpty() || bytes.size > 5 * 1024 * 1024) return null
        val dest = imageFile(context, email)
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(dest)) { tmp.delete(); return null }
        return Uri.fromFile(dest).toString() + "?v=" + dest.lastModified()
    }

    fun saveUploadedAvatar(context: Context, email: String, dataUrl: String): String? =
        runCatching { saveAvatar(context, email, Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT)) }.getOrNull()

    fun removeAvatar(context: Context, email: String) { if (email.isNotBlank()) imageFile(context, email).delete() }
    fun clear(context: Context, email: String?) {
        if (!email.isNullOrBlank()) removeAvatar(context, email)
        prefs(context).edit().clear().apply()
    }
}
