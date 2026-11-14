package com.throne.ui.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.throne.ui.UiState
import com.throne.ui.data.ConnectionMode
import com.throne.ui.data.ServerProfile
import com.throne.ui.vpn.EngineRuntimeState
import com.throne.ui.vpn.VpnConnectionState
import kotlinx.coroutines.delay

@Composable
fun VpnScreen(
    uiState: UiState,
    runtime: EngineRuntimeState,
    onToggleConnect: () -> Unit,
    onOpenServers: () -> Unit,
    onPingActive: (ServerProfile) -> Unit,
    onSelectMode: (ConnectionMode) -> Unit
) {
    val isConnected = runtime.state == VpnConnectionState.CONNECTED
    val isConnecting = runtime.state == VpnConnectionState.CONNECTING

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isConnected, runtime.connectedSinceMs) {
        if (isConnected) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val activeProfile = uiState.activeProfile
    val isTestingActive = activeProfile != null &&
        uiState.scan.active &&
        uiState.scan.activeIds.contains(activeProfile.id)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Brand + Engine Status Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThroneBrandMark(size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "THRONE",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.5.sp
                    )
                    Text(
                        text = "PRIVATE CONNECTION",
                        color = ThroneColors.TextSecondary,
                        fontSize = 8.sp,
                        letterSpacing = 1.8.sp
                    )
                }
            }

            val dotColor = when {
                runtime.lastError != null -> Color(0xFFF06D83)
                isConnected -> ThroneColors.MintGreen
                isConnecting -> ThroneColors.Amber
                else -> ThroneColors.MintGreen
            }
            val pillText = when {
                runtime.lastError != null -> "Engine error"
                isConnected -> "Connected"
                isConnecting -> "Connecting..."
                else -> "Engine ready"
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0x1EFFFFFF), Color(0x0AFFFFFF))
                        )
                    )
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = pillText,
                    color = Color(0xFFCFD6EA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // Hero Status
        Text(
            text = "YOUR PRIVATE NETWORK",
            color = ThroneColors.TextSecondary,
            fontSize = 10.sp,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (runtime.state) {
                VpnConnectionState.CONNECTED -> "You are protected"
                VpnConnectionState.CONNECTING -> "Connecting..."
                VpnConnectionState.STOPPED -> "Not connected"
            },
            color = ThroneColors.TextPrimary,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (runtime.state) {
                VpnConnectionState.CONNECTED ->
                    if (runtime.activeMode == ConnectionMode.VPN) "Traffic is routed through the secure tunnel"
                    else "Local proxy active on 127.0.0.1:${uiState.settings.proxyPort}"
                VpnConnectionState.CONNECTING -> "Establishing encrypted tunnel"
                VpnConnectionState.STOPPED -> "Select a server and start a secure connection"
            },
            color = ThroneColors.TextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(10.dp))

        // Session Timer (like WhiteVPN)
        Text(
            text = if (isConnected) formatDuration(runtime.connectedSinceMs, nowMs) else "00:00:00",
            color = if (isConnected) ThroneColors.MintAccent else ThroneColors.TextMuted,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp
        )

        Spacer(Modifier.height(6.dp))

        // Liquid Slime Button
        LiquidSlimeButton(
            state = runtime.state,
            onClick = onToggleConnect
        )

        val captionColor by animateColorAsState(
            targetValue = if (isConnected) ThroneColors.MintAccent else Color(0xFFC8C4DC),
            label = "capColor"
        )
        Text(
            text = when (runtime.state) {
                VpnConnectionState.CONNECTED -> "DISCONNECT"
                VpnConnectionState.CONNECTING -> "CONNECTING"
                VpnConnectionState.STOPPED -> "CONNECT"
            },
            color = captionColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.5.sp
        )

        Spacer(Modifier.height(22.dp))

        // Selected Server + Speed Metrics Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0x1CFFFFFF), Color(0x08FFFFFF))
                    )
                )
                .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(20.dp))
                .clickable { onOpenServers() }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val country = activeProfile?.let { detectCountryCode(it.name) } ?: "WW"
                CountryBadge(code = country, small = false)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SELECTED SERVER",
                        color = ThroneColors.TextSecondary,
                        fontSize = 8.sp,
                        letterSpacing = 1.6.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = activeProfile?.name ?: "No servers added",
                        color = ThroneColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (activeProfile != null) {
                            "${activeProfile.type} | ${activeProfile.server}:${activeProfile.port}"
                        } else {
                            "Tap to paste links or load built-in free servers"
                        },
                        color = ThroneColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Servers",
                    tint = Color(0xFF98A0B8),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0x18FFFFFF))
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ping Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PING",
                            color = ThroneColors.TextSecondary,
                            fontSize = 9.sp,
                            letterSpacing = 1.4.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { if (activeProfile != null) onPingActive(activeProfile) },
                            enabled = activeProfile != null && !uiState.scan.active,
                            modifier = Modifier.size(20.dp)
                        ) {
                            if (isTestingActive) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    strokeWidth = 1.5.dp,
                                    color = ThroneColors.MintAccent
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Ping",
                                    tint = Color(0xFFD0DCFF),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                    val lat = activeProfile?.latency
                    val pingText = when {
                        isTestingActive -> "Testing"
                        lat != null && lat > 0 -> "$lat ms"
                        lat != null && lat < 0 -> "ERR"
                        else -> "— ms"
                    }
                    val pingColor = when {
                        lat != null && lat in 1..120 -> ThroneColors.MintGreen
                        lat != null && lat in 121..280 -> ThroneColors.Amber
                        lat != null && lat < 0 -> ThroneColors.Danger
                        else -> Color(0xFFE6ECFF)
                    }
                    Text(
                        text = pingText,
                        color = pingColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Download Column
                val (downVal, downUnit) = formatRateParts(if (isConnected) runtime.downloadBps else 0L)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "↓ DOWN",
                        color = ThroneColors.TextSecondary,
                        fontSize = 9.sp,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$downVal $downUnit",
                        color = Color(0xFFE6ECFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Upload Column
                val (upVal, upUnit) = formatRateParts(if (isConnected) runtime.uploadBps else 0L)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "↑ UP",
                        color = ThroneColors.TextSecondary,
                        fontSize = 9.sp,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$upVal $upUnit",
                        color = Color(0xFFE6ECFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Connection Mode Switcher (VPN vs Local Proxy)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "CONNECTION MODE",
                color = ThroneColors.TextSecondary,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(Color(0x0CFFFFFF))
                    .border(1.dp, Color(0x22FFFFFF), CircleShape)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModePillButton(
                    label = "VPN (Entire Phone)",
                    selected = uiState.settings.mode == ConnectionMode.VPN,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectMode(ConnectionMode.VPN) }
                )
                ModePillButton(
                    label = "Proxy (:${uiState.settings.proxyPort})",
                    selected = uiState.settings.mode == ConnectionMode.PROXY,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectMode(ConnectionMode.PROXY) }
                )
            }
        }

        if (!runtime.lastError.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = runtime.lastError,
                color = ThroneColors.Danger,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ModePillButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    Brush.linearGradient(listOf(Color(0x44FFFFFF), Color(0x24DCE8FF)))
                } else {
                    Brush.linearGradient(listOf(Color(0x08FFFFFF), Color(0x04FFFFFF)))
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) Color(0x88FFFFFF) else Color(0x1EFFFFFF),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFFA8B1C8),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
