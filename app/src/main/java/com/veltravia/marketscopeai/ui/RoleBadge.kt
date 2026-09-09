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
 * Admin = teal (the team that runs MarketScope), Mentor = violet
 * (a verified trading mentor). Normal users render nothing.
 */
@Composable
fun RoleBadge(role: String) {
    val admin = role.equals("admin", ignoreCase = true)
    val mentor = role.equals("mentor", ignoreCase = true)
    if (!admin && !mentor) return
    val label = if (admin) "Admin" else "Mentor"
    val fg = if (admin) Color(0xFF0F766E) else Color(0xFF7C3AED)
    val bg = if (admin) Color(0xFFECFDF5) else Color(0xFFF5F3FF)
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
