package com.dmzs.datawatchclient.push

import android.content.Context

/**
 * S10-4 — Resolves the active alert delivery tier at runtime.
 *
 * | Tier | Mechanism | Notes |
 * |------|-----------|-------|
 * | [AlertTier.UnifiedPush] | First-party UnifiedPush provider | Placeholder — server issue #38 not yet shipped |
 * | [AlertTier.CommChannel] | Configured comm channel (Signal) | Active when a comm channel webhook is set |
 * | [AlertTier.Background]  | [NtfyFallbackService] SSE stream | Baseline fallback |
 *
 * The resolved tier is displayed on the About card so users can see at a glance
 * how they will receive push alerts.
 */
public enum class AlertTier { UnifiedPush, CommChannel, Background }

public object AlertTierDetector {
    public fun resolve(context: Context): AlertTier {
        // Tier 1: check if UnifiedPush is registered (placeholder — always false until
        // dmz006/datawatch#38 ships the server-side SSE + UnifiedPush endpoints).
        if (isUnifiedPushActive()) return AlertTier.UnifiedPush
        // Tier 2: check if any comm channel is configured.
        if (hasCommChannelConfigured(context)) return AlertTier.CommChannel
        // Tier 3: baseline fallback.
        return AlertTier.Background
    }

    /** Returns true once UnifiedPush distributor registration is supported. */
    private fun isUnifiedPushActive(): Boolean = false

    private fun hasCommChannelConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val signalConfigured = prefs.getString("comm_signal_webhook", null)?.isNotBlank() == true
        val channelType = prefs.getString("comm_channel_type", null)
        return signalConfigured || (!channelType.isNullOrBlank() && channelType != "none")
    }
}
