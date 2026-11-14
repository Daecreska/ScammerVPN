package com.throne.ui.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.throne.ui.vpn.VpnConnectionState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LiquidSlimeButton(
    state: VpnConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "pressScale"
    )

    val inf = rememberInfiniteTransition(label = "slime")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val spinAngle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )
    val auraPulse by inf.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura"
    )

    val isConnected = state == VpnConnectionState.CONNECTED
    val isConnecting = state == VpnConnectionState.CONNECTING

    val auraColor by animateColorAsState(
        targetValue = if (isConnected) Color(0x555EE6BE) else Color(0x38D8DCFF),
        animationSpec = tween(450),
        label = "auraColor"
    )
    val coreEdgeColor by animateColorAsState(
        targetValue = if (isConnected) Color(0xFFCCEEE3) else Color(0xFFD4DCF3),
        animationSpec = tween(450),
        label = "coreEdge"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isConnected) Color(0xFF0D8563) else Color(0xFF242138),
        animationSpec = tween(350),
        label = "iconTint"
    )

    Box(
        modifier = modifier
            .size(196.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(196.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseR = size.minDimension * 0.37f

            // Soft breathing aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraColor, Color.Transparent),
                    center = Offset(cx, cy),
                    radius = baseR * 1.48f * auraPulse
                ),
                radius = baseR * 1.48f * auraPulse,
                center = Offset(cx, cy)
            )

            // Outer translucent membrane
            val outerPath = buildOrganicBlobPath(
                cx = cx,
                cy = cy,
                radius = baseR * 1.13f,
                phase = -phase * 0.85f + 1.1f,
                wobble = 0.048f
            )
            drawPath(
                path = outerPath,
                color = if (isConnected) Color(0x2470E1BD) else Color(0x18FFFFFF)
            )
            drawPath(
                path = outerPath,
                color = if (isConnected) Color(0x6070E1BD) else Color(0x38FFFFFF),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Connecting progress arc
            if (isConnecting) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color.Transparent, Color.White, ThroneColors.MintAccent)
                    ),
                    startAngle = spinAngle,
                    sweepAngle = 220f,
                    useCenter = false,
                    topLeft = Offset(cx - baseR * 1.18f, cy - baseR * 1.18f),
                    size = androidx.compose.ui.geometry.Size(baseR * 2.36f, baseR * 2.36f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Inner pearl/mint liquid core
            val blobPath = buildOrganicBlobPath(
                cx = cx,
                cy = cy,
                radius = baseR,
                phase = phase,
                wobble = 0.055f
            )
            drawPath(
                path = blobPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        if (isConnected) Color(0xFFF0FFF9) else Color(0xFFF6F8FF),
                        coreEdgeColor
                    ),
                    center = Offset(cx - baseR * 0.25f, cy - baseR * 0.28f),
                    radius = baseR * 1.45f
                )
            )

            // Iridescent specular highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xD8FFFFFF), Color.Transparent),
                    center = Offset(cx - baseR * 0.28f, cy - baseR * 0.34f),
                    radius = baseR * 0.65f
                ),
                radius = baseR * 0.65f,
                center = Offset(cx - baseR * 0.28f, cy - baseR * 0.34f)
            )

            // Crisp liquid border
            drawPath(
                path = blobPath,
                color = Color(0xEEFFFFFF),
                style = Stroke(width = 1.4.dp.toPx())
            )
        }

        Icon(
            imageVector = Icons.Rounded.PowerSettingsNew,
            contentDescription = "Connect",
            tint = iconColor,
            modifier = Modifier.size(48.dp)
        )
    }
}

private fun buildOrganicBlobPath(
    cx: Float,
    cy: Float,
    radius: Float,
    phase: Float,
    wobble: Float
): Path {
    val points = 32
    val xs = FloatArray(points)
    val ys = FloatArray(points)
    for (i in 0 until points) {
        val a = (2.0 * PI * i / points).toFloat()
        val rOffset = 1f +
            wobble * sin(a * 2f + phase) +
            (wobble * 0.65f) * cos(a * 3f - phase * 1.3f)
        val r = radius * rOffset
        xs[i] = cx + r * cos(a)
        ys[i] = cy + r * sin(a)
    }

    val path = Path()
    val startX = (xs[points - 1] + xs[0]) / 2f
    val startY = (ys[points - 1] + ys[0]) / 2f
    path.moveTo(startX, startY)
    for (i in 0 until points) {
        val next = (i + 1) % points
        val midX = (xs[i] + xs[next]) / 2f
        val midY = (ys[i] + ys[next]) / 2f
        path.quadraticTo(xs[i], ys[i], midX, midY)
    }
    path.close()
    return path
}
