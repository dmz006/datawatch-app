package com.dmzs.datawatchclient.domain

/**
 * Who is acting on a given server. v1 is single-user per ADR-0011, but the type is
 * carried through the transport + repository layers so future multi-user work
 * (role-based views, per-user auth) doesn't require a rewrite.
 *
 * For v1, [Single] is always used and [role] is always [Role.Owner].
 */
public sealed interface Principal {
    public val id: String
    public val role: Role

    public data class Single(override val id: String = "local") : Principal {
        override val role: Role = Role.Owner
    }

    public enum class Role {
        Owner, Admin, Operator, Viewer,
    }
}
