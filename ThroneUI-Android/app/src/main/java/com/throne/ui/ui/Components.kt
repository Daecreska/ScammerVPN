package com.throne.ui.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val COUNTRY_RULES: List<Pair<String, Regex>> = listOf(
    "NL" to Regex("🇳🇱|(?:\\bNL\\b|nether|amster|haarlem)", RegexOption.IGNORE_CASE),
    "DE" to Regex("🇩🇪|(?:\\bDE\\b|german|frankfurt|berlin|munich|falkenstein)", RegexOption.IGNORE_CASE),
    "US" to Regex("🇺🇸|(?:\\bUS\\b|\\bUSA\\b|united states|new york|ashburn|dallas|los angeles|miami|seattle)", RegexOption.IGNORE_CASE),
    "FI" to Regex("🇫🇮|(?:\\bFI\\b|finland|helsinki)", RegexOption.IGNORE_CASE),
    "PL" to Regex("🇵🇱|(?:\\bPL\\b|poland|warsaw|gdansk)", RegexOption.IGNORE_CASE),
    "GB" to Regex("🇬🇧|(?:\\bGB\\b|\\bUK\\b|united kingdom|london|england)", RegexOption.IGNORE_CASE),
    "FR" to Regex("🇫🇷|(?:\\bFR\\b|france|paris|marseille)", RegexOption.IGNORE_CASE),
    "SE" to Regex("🇸🇪|(?:\\bSE\\b|sweden|stockholm)", RegexOption.IGNORE_CASE),
    "TR" to Regex("🇹🇷|(?:\\bTR\\b|turkey|istanbul|ankara)", RegexOption.IGNORE_CASE),
    "EE" to Regex("🇪🇪|(?:\\bEE\\b|estonia|tallinn)", RegexOption.IGNORE_CASE),
    "LV" to Regex("🇱🇻|(?:\\bLV\\b|latvia|riga)", RegexOption.IGNORE_CASE),
    "LT" to Regex("🇱🇹|(?:\\bLT\\b|lithuania|vilnius)", RegexOption.IGNORE_CASE),
    "CH" to Regex("🇨🇭|(?:\\bCH\\b|switz|zurich|geneva)", RegexOption.IGNORE_CASE),
    "CA" to Regex("🇨🇦|(?:\\bCA\\b|canada|toronto|montreal|vancouver)", RegexOption.IGNORE_CASE),
    "SG" to Regex("🇸🇬|(?:\\bSG\\b|singapore)", RegexOption.IGNORE_CASE),
    "JP" to Regex("🇯🇵|(?:\\bJP\\b|japan|tokyo|osaka)", RegexOption.IGNORE_CASE),
    "UA" to Regex("🇺🇦|(?:\\bUA\\b|ukraine|kyiv|kiev)", RegexOption.IGNORE_CASE),
    "AE" to Regex("🇦🇪|(?:\\bAE\\b|uae|dubai)", RegexOption.IGNORE_CASE)
)

fun detectCountryCode(name: String): String {
    for ((code, regex) in COUNTRY_RULES) {
        if (regex.containsMatchIn(name)) return code
    }
    return "WW"
}

@Composable
fun CountryBadge(code: String, small: Boolean = false) {
    val width = if (small) 34.dp else 40.dp
    val height = if (small) 24.dp else 29.dp
    val corner = if (small) 7.dp else 9.dp

    val (brush, textColor) = when (code) {
        "NL" -> Brush.verticalGradient(listOf(Color(0xFFAE1C28), Color(0xFFFFFFFF), Color(0xFF21468B))) to Color(0xFF111111)
        "DE" -> Brush.verticalGradient(listOf(Color(0xFF151515), Color(0xFFDD0000), Color(0xFFFFCE00))) to Color.White
        "US" -> Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFFB91C1C))) to Color.White
        "FI" -> Brush.linearGradient(listOf(Color(0xFF1E40AF), Color(0xFF60A5FA))) to Color.White
        "PL" -> Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFFDC2626))) to Color(0xFF111111)
        "GB" -> Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFFDC2626))) to Color.White
        "FR" -> Brush.horizontalGradient(listOf(Color(0xFF1D4ED8), Color(0xFFF8FAFC), Color(0xFFDC2626))) to Color(0xFF111111)
        "SE" -> Brush.linearGradient(listOf(Color(0xFF0369A1), Color(0xFFEAB308))) to Color.White
        "TR" -> Brush.linearGradient(listOf(Color(0xFFB91C1C), Color(0xFFEF4444))) to Color.White
        "SG", "JP", "CH" -> Brush.linearGradient(listOf(Color(0xFFBE123C), Color(0xFFF43F5E))) to Color.White
        "EE" -> Brush.verticalGradient(listOf(Color(0xFF0284C7), Color(0xFF111111), Color(0xFFE2E8F0))) to Color.White
        "LV" -> Brush.verticalGradient(listOf(Color(0xFF991B1B), Color(0xFFF8FAFC), Color(0xFF991B1B))) to Color(0xFF111111)
        "LT" -> Brush.verticalGradient(listOf(Color(0xFFEAB308), Color(0xFF15803D), Color(0xFFDC2626))) to Color.White
        "UA" -> Brush.verticalGradient(listOf(Color(0xFF0284C7), Color(0xFFEAB308))) to Color(0xFF111111)
        "CA" -> Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFF8FAFC), Color(0xFFDC2626))) to Color(0xFF111111)
        "AE" -> Brush.linearGradient(listOf(Color(0xFF15803D), Color(0xFFDC2626))) to Color.White
        else -> Brush.linearGradient(listOf(Color(0x36FFFFFF), Color(0x16FFFFFF))) to Color(0xFFF4F3FF)
    }

    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(corner))
            .background(brush)
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = code,
            color = textColor,
            fontSize = if (small) 10.sp else 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.7.sp
        )
    }
}

@Composable
fun ThroneBrandMark(size: Dp = 36.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.minDimension / 100f

        val shield = Path().apply {
            moveTo(50f * s, 9f * s)
            lineTo(83f * s, 22f * s)
            lineTo(83f * s, 48f * s)
            cubicTo(83f * s, 69f * s, 68f * s, 83f * s, 50f * s, 91f * s)
            cubicTo(32f * s, 83f * s, 17f * s, 69f * s, 17f * s, 48f * s)
            lineTo(17f * s, 22f * s)
            close()
        }

        drawPath(
            path = shield,
            brush = Brush.linearGradient(
                listOf(Color(0x52FFFFFF), Color(0x22DBEAFF), Color(0x10FFFFFF))
            )
        )
        drawPath(
            path = shield,
            brush = Brush.linearGradient(
                listOf(Color.White, Color(0xFFCFE6FF), Color(0xFFE3D4FF), Color(0xFFAEF7EA))
            ),
            style = Stroke(width = 4.2f * s, join = StrokeJoin.Round)
        )

        val crown = Path().apply {
            moveTo(31f * s, 58f * s)
            lineTo(35f * s, 37f * s)
            lineTo(44f * s, 46f * s)
            lineTo(50f * s, 31f * s)
            lineTo(56f * s, 46f * s)
            lineTo(65f * s, 37f * s)
            lineTo(69f * s, 58f * s)
            close()
        }
        drawPath(
            path = crown,
            brush = Brush.linearGradient(
                listOf(Color.White, Color(0xFFE6F0FF), Color(0xFFC3F7EC))
            )
        )
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFE6F0FF), Color(0xFFC3F7EC))),
            topLeft = androidx.compose.ui.geometry.Offset(33f * s, 63f * s),
            size = androidx.compose.ui.geometry.Size(34f * s, 5f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * s, 2.5f * s)
        )
    }
}

fun formatRateParts(bps: Long): Pair<String, String> {
    if (bps >= 1024L * 1024L) {
        return String.format("%.1f", bps / (1024.0 * 1024.0)) to "MB/s"
    }
    return "${bps / 1024L}" to "KB/s"
}

fun formatDuration(sinceMs: Long, nowMs: Long): String {
    if (sinceMs <= 0L || nowMs < sinceMs) return "00:00:00"
    val totalSec = (nowMs - sinceMs) / 1000L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return String.format("%02d:%02d:%02d", h, m, s)
}
