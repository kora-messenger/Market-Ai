package com.veltravia.marketscopeai.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colors + label for a community role. Every roled author carries three
 * coordinated colors: a text color, a soft background for their badge, and
 * a ring color that wraps their avatar so official voices are recognizable
 * at a glance anywhere they appear (feed posts, comments, DMs).
 */
data class RoleStyle(
    val label: String,
    val text: Color,
    val bg: Color,
    val ring: Color
)

/** Role color system: Admin teal, Mentor violet, Moderator blue, Team amber. */
fun roleStyle(role: String?, isTeam: Boolean = false): RoleStyle? = when {
    role.equals("admin", true) ->
        RoleStyle("Admin", Color(0xFF0F766E), Color(0xFFECFDF5), Color(0xFF5EEAD4))
    role.equals("mentor", true) ->
        RoleStyle("Mentor", Color(0xFF7C3AED), Color(0xFFF5F3FF), Color(0xFFC4B5FD))
    role.equals("moderator", true) ->
        RoleStyle("Mod", Color(0xFF2563EB), Color(0xFFEFF6FF), Color(0xFF93C5FD))
    isTeam ->
        RoleStyle("Team", Color(0xFFB45309), Color(0xFFFFF7ED), Color(0xFFFCD34D))
    else -> null
}

/**
 * Small role chip shown next to a community author's name — a fully
 * rounded pill in the role's colors. Normal users render nothing.
 */
@Composable
fun RoleBadge(role: String, isTeam: Boolean = false) {
    val style = roleStyle(role, isTeam) ?: return
    Surface(color = style.bg, shape = RoundedCornerShape(50)) {
        Text(
            style.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = style.text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
