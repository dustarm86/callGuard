package com.dustin.callguard.screening

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dustin.callguard.data.ScreenerSettingsStore

/** quick settings tile that flips the master screening switch without opening the app. */
class ScreeningTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val store = ScreenerSettingsStore(applicationContext)
        val settings = store.load()
        store.save(settings.copy(screeningEnabled = !settings.screeningEnabled))
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val enabled = ScreenerSettingsStore(applicationContext).load().screeningEnabled
        tile.label = "callGuard"
        tile.subtitle = if (enabled) "screening on" else "screening off"
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
