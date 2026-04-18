package com.dmzs.datawatchclient.ui.shell

/**
 * Navigation route constants. Kept as string constants to avoid pulling in Kotlin
 * serialization for route types at this sprint's scope — simple enough for a single
 * level of routes.
 */
public object Destinations {
    public const val Splash: String = "splash"
    public const val SplashReplay: String = "splash/replay"
    public const val Onboarding: String = "onboarding"
    public const val AddServer: String = "servers/add"
    public const val Home: String = "home"

    // Bottom-nav tabs under Home.
    public object Tabs {
        public const val Sessions: String = "home/sessions"
        public const val Channels: String = "home/channels"
        public const val Stats: String = "home/stats"
        public const val Settings: String = "home/settings"
    }
}
