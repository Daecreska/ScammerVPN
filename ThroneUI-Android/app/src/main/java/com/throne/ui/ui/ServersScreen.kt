package com.throne.ui.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.throne.ui.UiState
import com.throne.ui.data.BUILTIN_SOURCES
import com.throne.ui.data.BuiltinSource
import com.throne.ui.data.ServerProfile
import com.throne.ui.vpn.EngineRuntimeState
import com.throne.ui.vpn.VpnConnectionState

@Composable
fun ServersScreen(
    uiState: UiState,
    runtime: EngineRuntimeState,
    onImport: (String, Long) -> Unit,
    onAddBuiltin: (BuiltinSource) -> Unit,
    onRemoveBuiltin: (BuiltinSource) -> Unit,
    onPingAll: () -> Unit,
    onCancelScan: () -> Unit,
    onPingOne: (ServerProfile) -> Unit,
    onConnectFastest: (Long) -> Unit,
    onUpdateSubs: () -> Unit,
    onRemoveDeadAndSlow: (Long) -> Unit,
    onSelectGroup: (Long) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectProfile: (Long) -> Unit,
    onConnectProfile: (ServerProfile) -> Unit,
    onDeleteProfile: (Long) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var importText by remember { mutableStateOf("") }
    var showBuiltin by remember { mutableStateOf(uiState.profiles.isEmpty()) }

    val groupCounts = remember(uiState.profiles) {
        uiState.profiles.groupingBy { it.groupId }.eachCount()
    }
    val lockMutations = uiState.scan.active || uiState.isBusy
    val isConnected = runtime.state == VpnConnectionState.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "SERVERS & SUBSCRIPTIONS",
                    color = ThroneColors.TextSecondary,
                    fontSize = 9.sp,
                    letterSpacing = 2.6.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Servers",
                    color = ThroneColors.TextPrimary,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${uiState.filteredProfiles.size} servers",
                color = ThroneColors.TextSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        // Import URL / Link Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassTextField(
                value = importText,
                onValueChange = { importText = it },
                placeholder = "Paste vless://, ss://, hy2:// or sub URL...",
                modifier = Modifier.weight(1f)
            )
            GlassPillButton(
                text = "Add",
                enabled = !lockMutations && importText.isNotBlank(),
                accent = true,
                onClick = {
                    onImport(importText, uiState.selectedGroupId)
                    importText = ""
                }
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(ThroneColors.GlassFill)
                    .border(1.dp, ThroneColors.GlassBorder, CircleShape)
                    .clickable(enabled = !lockMutations) {
                        val txt = clipboard.getText()?.text?.trim().orEmpty()
                        if (txt.isNotEmpty()) {
                            onImport(txt, uiState.selectedGroupId)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentPaste,
                    contentDescription = "Paste",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Action Pills Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassPillButton(
                text = "⚡ Built-in Free",
                accentMint = showBuiltin,
                onClick = { showBuiltin = !showBuiltin }
            )
            GlassPillButton(
                text = if (uiState.scan.active) "Scanning..." else "Ping all",
                enabled = !lockMutations && uiState.filteredProfiles.isNotEmpty(),
                onClick = onPingAll
            )
            GlassPillButton(
                text = "✦ Auto Fastest",
                enabled = !lockMutations && uiState.profiles.isNotEmpty(),
                onClick = { onConnectFastest(uiState.selectedGroupId) }
            )
            GlassPillButton(
                text = "↻ Update subs",
                enabled = !lockMutations,
                onClick = onUpdateSubs
            )
            GlassPillButton(
                text = "Clean dead & >${uiState.settings.maxLatencyMs}ms",
                danger = true,
                enabled = !lockMutations && uiState.profiles.isNotEmpty(),
                onClick = { onRemoveDeadAndSlow(uiState.selectedGroupId) }
            )
        }

        // Built-in GitHub Subscriptions Panel (WhiteVPN card style)
        AnimatedVisibility(visible = showBuiltin) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x10FFFFFF))
                    .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Public open-source GitHub collections (1-tap load):",
                    color = Color(0xFFCDEEE8),
                    fontSize = 11.sp
                )
                BUILTIN_SOURCES.forEach { src ->
                    val matched = uiState.groups.firstOrNull { it.url == src.url || it.name == src.name }
                    val count = matched?.let { groupCounts[it.id] ?: 0 } ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0DFFFFFF))
                            .border(
                                1.dp,
                                if (matched != null) Color(0x4570E1BD) else Color(0x1EFFFFFF),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = src.name,
                                color = ThroneColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (matched != null) "$count servers · loaded" else src.desc,
                                color = if (matched != null) ThroneColors.MintAccent else ThroneColors.TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        GlassPillButton(
                            text = if (matched != null) "Update" else "Load",
                            small = true,
                            enabled = !lockMutations,
                            onClick = { onAddBuiltin(src) }
                        )
                        if (matched != null) {
                            Spacer(Modifier.width(6.dp))
                            GlassPillButton(
                                text = "Remove",
                                small = true,
                                danger = true,
                                enabled = !lockMutations,
                                onClick = { onRemoveBuiltin(src) }
                            )
                        }
                    }
                }
            }
        }

        // Live Latency Scan Progress Banner
        AnimatedVisibility(visible = uiState.scan.active) {
            val total = uiState.scan.total.coerceAtLeast(1)
            val done = uiState.scan.done.coerceIn(0, total)
            val pct = done.toFloat() / total.toFloat()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Color(0x3CFFFFFF), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = ThroneColors.MintAccent
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (uiState.scan.single) {
                            "Testing: ${uiState.scan.targetName}"
                        } else {
                            "Scanning fastest first (${(pct * 100).toInt()}%)"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!uiState.scan.single) {
                        Text(
                            text = "$done/$total · ${uiState.scan.onlineCount} online",
                            color = Color(0xFFD6F7F0),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    GlassPillButton(
                        text = "Stop",
                        small = true,
                        danger = true,
                        onClick = onCancelScan
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (uiState.scan.single) 0.35f else pct.coerceAtLeast(0.03f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = ThroneColors.MintAccent,
                    trackColor = Color(0x1FFFFFFF)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Group Filter Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GroupTabChip(
                label = "All (${uiState.profiles.size})",
                selected = uiState.selectedGroupId == -1L,
                onClick = { onSelectGroup(-1L) }
            )
            uiState.groups.forEach { g ->
                val cnt = groupCounts[g.id] ?: 0
                GroupTabChip(
                    label = if (cnt > 0) "${g.name} ($cnt)" else g.name,
                    selected = uiState.selectedGroupId == g.id,
                    onClick = { onSelectGroup(g.id) }
                )
            }
            if (uiState.selectedGroupId >= 0L && (groupCounts[uiState.selectedGroupId] ?: 0) > 0) {
                IconButton(
                    onClick = { onDeleteGroup(uiState.selectedGroupId) },
                    enabled = !lockMutations,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete group",
                        tint = ThroneColors.Danger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Search Bar
        GlassTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            placeholder = "Search (NL, Germany, VLESS...)",
            leadingSearch = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // Virtualized Server List (60-120 FPS LazyColumn)
        if (uiState.filteredProfiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (uiState.profiles.isEmpty()) {
                            "No servers yet"
                        } else {
                            "Nothing matches your search"
                        },
                        color = ThroneColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    if (uiState.profiles.isEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        GlassPillButton(
                            text = "⚡ Open Built-in Free Servers",
                            accentMint = true,
                            onClick = { showBuiltin = true }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(
                    items = uiState.filteredProfiles,
                    key = { it.id }
                ) { profile ->
                    val isActive = isConnected && runtime.activeProfileId == profile.id
                    val isSelected = uiState.settings.selectedProfileId == profile.id
                    val isTestingThis = uiState.scan.active && uiState.scan.activeIds.contains(profile.id)

                    ServerRowItem(
                        profile = profile,
                        isActive = isActive,
                        isSelected = isSelected,
                        isTesting = isTestingThis,
                        lockMutations = lockMutations,
                        onSelect = { onSelectProfile(profile.id) },
                        onPingOne = { onPingOne(profile) },
                        onConnect = { onConnectProfile(profile) },
                        onDelete = { onDeleteProfile(profile.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerRowItem(
    profile: ServerProfile,
    isActive: Boolean,
    isSelected: Boolean,
    isTesting: Boolean,
    lockMutations: Boolean,
    onSelect: () -> Unit,
    onPingOne: () -> Unit,
    onConnect: () -> Unit,
    onDelete: () -> Unit
) {
    val bgColor = when {
        isActive -> Color(0x1A70E1BD)
        isSelected -> Color(0x16FFFFFF)
        else -> Color(0x07FFFFFF)
    }
    val borderColor = when {
        isActive -> Color(0x6670E1BD)
        isSelected -> Color(0x55FFFFFF)
        else -> Color(0x14FFFFFF)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onSelect() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CountryBadge(code = detectCountryCode(profile.name), small = true)
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                color = ThroneColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${profile.type} | ${profile.server}:${profile.port}",
                color = ThroneColors.TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(6.dp))

        // Ping text
        val lat = profile.latency
        val (pingStr, pingCol) = when {
            isTesting -> "..." to Color.White
            lat != null && lat in 1..120 -> "$lat ms" to ThroneColors.MintGreen
            lat != null && lat in 121..280 -> "$lat ms" to ThroneColors.Amber
            lat != null && lat > 280 -> "$lat ms" to ThroneColors.Danger
            lat != null && lat < 0 -> "Timeout" to ThroneColors.Danger
            else -> "—" to Color(0xFF9BA0B0)
        }
        Text(
            text = pingStr,
            color = pingCol,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(54.dp)
        )

        IconButton(
            onClick = onPingOne,
            enabled = !lockMutations,
            modifier = Modifier.size(30.dp)
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.6.dp,
                    color = ThroneColors.MintAccent
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Ping",
                    tint = Color(0xFFD2DDF7),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        GlassPillButton(
            text = if (isActive) "Active" else "Connect",
            small = true,
            accentMint = isActive,
            onClick = onConnect
        )

        Spacer(Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0x10FFFFFF))
                .clickable(enabled = !lockMutations) { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Delete",
                tint = ThroneColors.Danger,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun GroupTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color(0x38FFFFFF) else Color(0x12FFFFFF))
            .border(
                1.dp,
                if (selected) Color(0x99FFFFFF) else Color(0x28FFFFFF),
                CircleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFFCBD3E8),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
fun GlassPillButton(
    text: String,
    small: Boolean = false,
    accent: Boolean = false,
    accentMint: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bgBrush = when {
        accent -> Brush.linearGradient(listOf(Color(0x52FFFFFF), Color(0x30DCE8FF)))
        accentMint -> Brush.linearGradient(listOf(Color(0x3CE1FFF8), Color(0x1CC3F2EC)))
        else -> Brush.linearGradient(listOf(Color(0x26FFFFFF), Color(0x10FFFFFF)))
    }
    val borderCol = when {
        !enabled -> Color(0x18FFFFFF)
        danger -> Color(0x55FFBECA)
        accentMint -> Color(0x6CEEFDF9)
        accent -> Color(0x88FFFFFF)
        else -> Color(0x3AFFFFFF)
    }
    val textCol = when {
        !enabled -> Color(0x66FFFFFF)
        danger -> Color(0xFFFFD3DA)
        accentMint -> Color(0xFFEEFEF9)
        else -> Color(0xFFF4F7FF)
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgBrush)
            .border(1.dp, borderCol, CircleShape)
            .clickable(enabled = enabled) { onClick() }
            .padding(
                horizontal = if (small) 10.dp else 13.dp,
                vertical = if (small) 5.dp else 8.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textCol,
            fontSize = if (small) 10.sp else 11.sp,
            fontWeight = if (accent || accentMint) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingSearch: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(Color(0x0DFFFFFF))
            .border(1.dp, Color(0x28FFFFFF), CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingSearch) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = Color(0xFF828AA3),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(7.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = Color(0xFF828AA3),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = ThroneColors.TextPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Clear",
                tint = Color(0xFF939BB4),
                modifier = Modifier
                    .size(15.dp)
                    .clickable { onValueChange("") }
            )
        }
    }
}
