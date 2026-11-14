package com.throne.ui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.throne.ui.UiState
import com.throne.ui.data.AppInstalledItem
import com.throne.ui.data.SplitTunnelMode
import com.throne.ui.vpn.EngineRuntimeState
import com.throne.ui.vpn.VpnConnectionState

@Composable
fun SettingsScreen(
    uiState: UiState,
    runtime: EngineRuntimeState,
    installedApps: List<AppInstalledItem>,
    appsLoading: Boolean,
    onUpdateProxyPort: (Int) -> Unit,
    onUpdateMaxLatency: (Int) -> Unit,
    onUpdateSplitTunnel: (SplitTunnelMode, Set<String>) -> Unit,
    onLoadApps: () -> Unit,
    onRestartEngine: () -> Unit,
    onClearLogs: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showPortDialog by remember { mutableStateOf(false) }
    var showAppsDialog by remember { mutableStateOf(false) }

    val isRunning = runtime.state == VpnConnectionState.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(
                text = "SYSTEM & DIAGNOSTICS",
                color = ThroneColors.TextSecondary,
                fontSize = 9.sp,
                letterSpacing = 2.6.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Settings & Diagnostics",
                color = ThroneColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 1. Local Proxy Endpoint (same as PC Settings)
        SettingGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Throne local proxy endpoint",
                        color = ThroneColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "127.0.0.1:${uiState.settings.proxyPort} (HTTP + SOCKS5)",
                        color = ThroneColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isRunning) Color(0x285EE6B5) else Color(0x18FFFFFF))
                            .border(
                                1.dp,
                                if (isRunning) Color(0x665EE6B5) else Color(0x33FFFFFF),
                                CircleShape
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isRunning) "ON" else "OFF",
                            color = if (isRunning) ThroneColors.MintGreen else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    GlassPillButton(
                        text = "Port",
                        small = true,
                        onClick = { showPortDialog = true }
                    )
                }
            }
        }

        // 2. Engine Version + Restart (same as PC Settings)
        SettingGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Throne engine version",
                        color = ThroneColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = runtime.coreVersion,
                        color = ThroneColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
                GlassPillButton(
                    text = "Restart engine",
                    small = true,
                    onClick = onRestartEngine
                )
            }
        }

        // 3. Auto-clean Latency Threshold
        SettingGlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Clean latency threshold",
                    color = ThroneColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Remove servers slower than ${uiState.settings.maxLatencyMs} ms",
                    color = ThroneColors.TextSecondary,
                    fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(80, 120, 200, 350).forEach { ms ->
                        GlassPillButton(
                            text = "${ms}ms",
                            small = true,
                            accentMint = uiState.settings.maxLatencyMs == ms,
                            onClick = { onUpdateMaxLatency(ms) }
                        )
                    }
                }
            }
        }

        // 4. Split Tunneling (Mobile feature)
        SettingGlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Split Tunneling (App Routing)",
                            color = ThroneColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val count = uiState.settings.splitPackages.size
                        val desc = when (uiState.settings.splitMode) {
                            SplitTunnelMode.DISABLED -> "All apps use VPN"
                            SplitTunnelMode.BYPASS_SELECTED -> "$count apps bypass VPN"
                            SplitTunnelMode.ONLY_SELECTED -> "Only $count apps use VPN"
                        }
                        Text(
                            text = desc,
                            color = ThroneColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    GlassPillButton(
                        text = "Choose apps",
                        small = true,
                        accentMint = uiState.settings.splitMode != SplitTunnelMode.DISABLED,
                        onClick = {
                            onLoadApps()
                            showAppsDialog = true
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SplitModeChip(
                        label = "All apps",
                        selected = uiState.settings.splitMode == SplitTunnelMode.DISABLED,
                        onClick = {
                            onUpdateSplitTunnel(SplitTunnelMode.DISABLED, uiState.settings.splitPackages)
                        }
                    )
                    SplitModeChip(
                        label = "Bypass selected",
                        selected = uiState.settings.splitMode == SplitTunnelMode.BYPASS_SELECTED,
                        onClick = {
                            onUpdateSplitTunnel(
                                SplitTunnelMode.BYPASS_SELECTED,
                                uiState.settings.splitPackages
                            )
                        }
                    )
                    SplitModeChip(
                        label = "Only selected",
                        selected = uiState.settings.splitMode == SplitTunnelMode.ONLY_SELECTED,
                        onClick = {
                            onUpdateSplitTunnel(
                                SplitTunnelMode.ONLY_SELECTED,
                                uiState.settings.splitPackages
                            )
                        }
                    )
                }
            }
        }

        // 5. Recent Engine Events (PC Logs Box)
        SettingGlassCard {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT ENGINE EVENTS",
                        color = ThroneColors.TextSecondary,
                        fontSize = 9.sp,
                        letterSpacing = 1.6.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = {
                                val text = runtime.logs.joinToString("\n")
                                if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy logs",
                                tint = ThroneColors.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        GlassPillButton(
                            text = "Clear",
                            small = true,
                            onClick = onClearLogs
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xB807090E))
                        .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = if (runtime.logs.isEmpty()) "No logs yet" else runtime.logs.joinToString("\n"),
                        color = Color(0xFFA9B2C8),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    if (showPortDialog) {
        var portText by remember { mutableStateOf(uiState.settings.proxyPort.toString()) }
        AlertDialog(
            onDismissRequest = { showPortDialog = false },
            containerColor = ThroneColors.BgCardElevated,
            title = { Text("Local Proxy Port", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { if (it.length <= 5) portText = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("1024 - 65535") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = portText.toIntOrNull()
                    if (p != null && p in 1024..65535) {
                        onUpdateProxyPort(p)
                    }
                    showPortDialog = false
                }) {
                    Text("Save", color = ThroneColors.MintAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPortDialog = false }) {
                    Text("Cancel", color = ThroneColors.TextSecondary)
                }
            }
        )
    }

    if (showAppsDialog) {
        SplitTunnelAppsDialog(
            apps = installedApps,
            loading = appsLoading,
            selectedPackages = uiState.settings.splitPackages,
            mode = uiState.settings.splitMode,
            onSave = { newMode, newPackages ->
                onUpdateSplitTunnel(newMode, newPackages)
                showAppsDialog = false
            },
            onDismiss = { showAppsDialog = false }
        )
    }
}

@Composable
private fun SplitModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    GlassPillButton(
        text = label,
        small = true,
        accentMint = selected,
        onClick = onClick
    )
}

@Composable
private fun SettingGlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x07FFFFFF)))
            )
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun SplitTunnelAppsDialog(
    apps: List<AppInstalledItem>,
    loading: Boolean,
    selectedPackages: Set<String>,
    mode: SplitTunnelMode,
    onSave: (SplitTunnelMode, Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var localSelected by remember { mutableStateOf(selectedPackages) }
    var localMode by remember {
        mutableStateOf(
            if (mode == SplitTunnelMode.DISABLED) SplitTunnelMode.BYPASS_SELECTED else mode
        )
    }

    val filtered = remember(apps, search, showSystem) {
        val q = search.trim().lowercase()
        apps.filter { app ->
            if (!showSystem && app.isSystem) return@filter false
            if (q.isNotEmpty()) {
                app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)
            } else true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .fillMaxHeight(0.84f)
                .clip(RoundedCornerShape(22.dp))
                .background(ThroneColors.BgCard)
                .border(1.dp, Color(0x36FFFFFF), RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        tint = ThroneColors.MintAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Split Tunneling",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPillButton(
                    text = "Bypass selected",
                    small = true,
                    accentMint = localMode == SplitTunnelMode.BYPASS_SELECTED,
                    onClick = { localMode = SplitTunnelMode.BYPASS_SELECTED }
                )
                GlassPillButton(
                    text = "Only selected",
                    small = true,
                    accentMint = localMode == SplitTunnelMode.ONLY_SELECTED,
                    onClick = { localMode = SplitTunnelMode.ONLY_SELECTED }
                )
            }

            Spacer(Modifier.height(10.dp))

            GlassTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = "Search app (Sber, Gosuslugi, VK...)",
                leadingSearch = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show system apps",
                    color = ThroneColors.TextSecondary,
                    fontSize = 12.sp
                )
                Switch(
                    checked = showSystem,
                    onCheckedChange = { showSystem = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = ThroneColors.MintAccent)
                )
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ThroneColors.MintAccent)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = filtered, key = { it.packageName }) { item ->
                        val checked = localSelected.contains(item.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (checked) Color(0x1870E1BD) else Color(0x08FFFFFF))
                                .clickable {
                                    localSelected = if (checked) {
                                        localSelected - item.packageName
                                    } else {
                                        localSelected + item.packageName
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.label,
                                    color = ThroneColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.packageName,
                                    color = ThroneColors.TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChk ->
                                    localSelected = if (isChk) {
                                        localSelected + item.packageName
                                    } else {
                                        localSelected - item.packageName
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = ThroneColors.MintAccent)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected: ${localSelected.size}",
                    color = ThroneColors.TextSecondary,
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPillButton(
                        text = "Clear",
                        danger = true,
                        onClick = { localSelected = emptySet() }
                    )
                    GlassPillButton(
                        text = "Apply",
                        accentMint = true,
                        onClick = {
                            val finalMode = if (localSelected.isEmpty()) {
                                SplitTunnelMode.DISABLED
                            } else {
                                localMode
                            }
                            onSave(finalMode, localSelected)
                        }
                    )
                }
            }
        }
    }
}
