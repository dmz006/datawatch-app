package com.dmzs.datawatchclient.domain

import kotlinx.serialization.Serializable

/**
 * How the mobile client reaches a datawatch server (ADR-0009). A given
 * [ServerProfile] binds to exactly one profile; fallback chains are computed by
 * use-cases at call time, not stored here.
 */
@Serializable
public sealed interface ReachabilityProfile {
    public val id: String

    @Serializable
    public data class Tailscale(
        override val id: String,
        val tailnetHostname: String,
    ) : ReachabilityProfile

    @Serializable
    public data class Lan(
        override val id: String,
        val hostname: String,
    ) : ReachabilityProfile

    @Serializable
    public data class DnsTxt(
        override val id: String,
        val rootDomain: String,
        val hmacSecretRef: String,
    ) : ReachabilityProfile

    /** Intent-handoff relay via an on-device messenger app (ADR-0004 fallback). */
    @Serializable
    public data class Relay(
        override val id: String,
        val kind: RelayKind,
        val recipient: String,
    ) : ReachabilityProfile

    public enum class RelayKind {
        Signal, Sms, Slack, Discord, Matrix, Telegram, Ntfy, Email,
    }
}
