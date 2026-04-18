package com.dmzs.datawatchclient

import android.app.Application

/**
 * Application bootstrap. Sprint 1 wires SQLCipher + SQLDelight + Ktor here; pre-MVP
 * this is intentionally minimal so the scaffold can compile without the Sprint 1
 * dependency injection graph.
 */
public class DatawatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Sprint 1: initialize encrypted DB, keystore, FCM / ntfy registration, etc.
    }
}
