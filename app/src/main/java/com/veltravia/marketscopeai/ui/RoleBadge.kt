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
 * Small role chip shown next to a community author's name.
 * Admin = teal, Moderator = cyan (both run official team messaging),
 * Mentor = violet (a verified trading mentor). Normal users render nothing.
 */
@Composable
fun RoleBadge(role: String) {
    val admin = role.equals("admin", ignoreCase = true)
    val moderator = role.equals("moderator", ignoreCase = true)
    val mentor = role.equals("mentor", ignoreCase = true)
    if (!admin && !moderator && !mentor) return
    val label = when {
        admin -> "Admin"
        moderator -> "Moderator"
        else -> "Mentor"
    }
    val fg = when {
        admin -> Color(0xFF0F766E)
        moderator -> Color(0xFF0369A1)
        else -> Color(0xFF7C3AED)
    }
    val bg = when {
        admin -> Color(0xFFECFDF5)
        moderator -> Color(0xFFEFF6FF)
        else -> Color(0xFFF5F3FF)
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
