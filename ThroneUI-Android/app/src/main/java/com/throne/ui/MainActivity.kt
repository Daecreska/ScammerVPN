package com.throne.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.throne.ui.ui.ServersScreen
import com.throne.ui.ui.SettingsScreen
import com.throne.ui.ui.ThroneColors
import com.throne.ui.ui.ThroneTheme
import com.throne.ui.ui.VpnScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: ThroneViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        setContent {
            ThroneTheme {
                ThroneRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ThroneRoot(viewModel: ThroneViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val runtime by viewModel.runtimeState.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val appsLoading by viewModel.appsLoading.collectAsState()
    val context = LocalContext.current

    var currentTab by remember { mutableIntStateOf(0) } // 0=VPN, 1=Servers, 2=Settings

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onVpnPermissionResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        if (uiState.toastMessage != null) {
            delay(2600L)
            viewModel.clearToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0x1EA4B8FF), ThroneColors.BgBase),
                    radius = 1200f
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                ThroneBottomBar(
                    selectedTab = currentTab,
                    onSelectTab = { currentTab = it }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                when (currentTab) {
                    0 -> VpnScreen(
                        uiState = uiState,
                        runtime = runtime,
                        onToggleConnect = {
                            viewModel.toggleConnection { intent ->
                                vpnPermissionLauncher.launch(intent)
                            }
                        },
                        onOpenServers = { currentTab = 1 },
                        onPingActive = { profile -> viewModel.pingSingle(profile) },
                        onSelectMode = { mode ->
                            viewModel.setMode(mode) { intent ->
                                vpnPermissionLauncher.launch(intent)
                            }
                        }
                    )
                    1 -> ServersScreen(
                        uiState = uiState,
                        runtime = runtime,
                        onImport = { text, gid -> viewModel.importInput(text, gid) },
                        onAddBuiltin = { src -> viewModel.addOrUpdateBuiltin(src) },
                        onRemoveBuiltin = { src -> viewModel.removeBuiltinGroup(src) },
                        onPingAll = { viewModel.pingFiltered() },
                        onCancelScan = { viewModel.cancelScan() },
                        onPingOne = { profile -> viewModel.pingSingle(profile) },
                        onConnectFastest = { gid ->
                            currentTab = 0
                            viewModel.connectFastest(gid) { intent ->
                                vpnPermissionLauncher.launch(intent)
                            }
                        },
                        onUpdateSubs = { viewModel.updateAllSubscriptions() },
                        onRemoveDeadAndSlow = { gid -> viewModel.removeDeadAndSlow(gid) },
                        onSelectGroup = { gid -> viewModel.selectGroup(gid) },
                        onDeleteGroup = { gid -> viewModel.deleteGroup(gid) },
                        onSearchChange = { q -> viewModel.setSearchQuery(q) },
                        onSelectProfile = { id -> viewModel.selectProfile(id) },
                        onConnectProfile = { profile ->
                            currentTab = 0
                            viewModel.connectProfile(profile) { intent ->
                                vpnPermissionLauncher.launch(intent)
                            }
                        },
                        onDeleteProfile = { id -> viewModel.deleteProfile(id) }
                    )
                    2 -> SettingsScreen(
                        uiState = uiState,
                        runtime = runtime,
                        installedApps = installedApps,
                        appsLoading = appsLoading,
                        onUpdateProxyPort = { p -> viewModel.updateProxyPort(p) },
                        onUpdateMaxLatency = { ms -> viewModel.updateMaxLatencyMs(ms) },
                        onUpdateSplitTunnel = { m, pkgs -> viewModel.updateSplitTunnel(m, pkgs) },
                        onLoadApps = { viewModel.loadInstalledApps() },
                        onRestartEngine = {
                            viewModel.restartEngine { intent ->
                                vpnPermissionLauncher.launch(intent)
                            }
                        },
                        onClearLogs = { viewModel.clearLogs() }
                    )
                }
            }
        }

        // Floating Toast
        AnimatedVisibility(
            visible = uiState.toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 86.dp, start = 24.dp, end = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xE61F2536))
                    .border(1.dp, Color(0x55FFFFFF), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = uiState.toastMessage.orEmpty(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ThroneBottomBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xEE0E111A))
            .border(1.dp, Color(0x16FFFFFF), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTabItem(
                label = "VPN",
                icon = Icons.Rounded.Shield,
                selected = selectedTab == 0,
                onClick = { onSelectTab(0) }
            )
            BottomTabItem(
                label = "Servers",
                icon = Icons.Rounded.Dns,
                selected = selectedTab == 1,
                onClick = { onSelectTab(1) }
            )
            BottomTabItem(
                label = "Settings",
                icon = Icons.Rounded.Tune,
                selected = selectedTab == 2,
                onClick = { onSelectTab(2) }
            )
        }
    }
}

@Composable
private fun BottomTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) ThroneColors.MintAccent else ThroneColors.TextSecondary
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(width = 20.dp, height = 2.5.dp)
                .clip(CircleShape)
                .background(if (selected) ThroneColors.MintAccent else Color.Transparent)
        )
    }
}
