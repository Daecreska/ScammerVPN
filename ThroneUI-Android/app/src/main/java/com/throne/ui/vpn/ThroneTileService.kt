package com.throne.ui.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.throne.ui.MainActivity
import com.throne.ui.data.AppStorage
import com.throne.ui.data.ConfigBuilder
import com.throne.ui.data.ConnectionMode

class ThroneTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        val runtime = ThroneEngine.runtime.value
        if (runtime.state != VpnConnectionState.STOPPED) {
            ThroneEngine.stop(applicationContext)
            syncTile()
            return
        }

        val storage = AppStorage(applicationContext)
        val settings = storage.loadSettings()
        val profiles = storage.loadProfiles()
        val target = profiles.firstOrNull { it.id == settings.selectedProfileId }
            ?: profiles.firstOrNull()

        if (target == null || (settings.mode == ConnectionMode.VPN && VpnService.prepare(this) != null)) {
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    android.app.PendingIntent.getActivity(
                        this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val config = ConfigBuilder.buildRuntimeConfig(target, settings, packageName)
        ThroneEngine.start(applicationContext, config, target.id, target.name, settings.mode)
        syncTile()
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val runtime = ThroneEngine.runtime.value
        when (runtime.state) {
            VpnConnectionState.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = runtime.activeProfileName.ifBlank { "Protected" }
                }
            }
            VpnConnectionState.CONNECTING -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Connecting..."
                }
            }
            VpnConnectionState.STOPPED -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Off"
                }
            }
        }
        tile.updateTile()
    }
}
