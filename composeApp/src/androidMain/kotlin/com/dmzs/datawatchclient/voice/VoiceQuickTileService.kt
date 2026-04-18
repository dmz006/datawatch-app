package com.dmzs.datawatchclient.voice

import android.content.Intent
import android.service.quicksettings.TileService

/**
 * Voice quick-settings tile per ADR-0025 (four voice invocation surfaces).
 * Tapping the tile launches `dwclient://voice/new` directly into the voice-capture
 * screen. Pre-MVP scaffold wires the intent only; Sprint 3 implements capture.
 */
public class VoiceQuickTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("dwclient://voice/new")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivityAndCollapse(intent)
    }
}
