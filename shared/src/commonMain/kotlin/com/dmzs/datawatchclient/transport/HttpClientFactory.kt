package com.dmzs.datawatchclient.transport

import io.ktor.client.HttpClient

/**
 * Per-platform Ktor HttpClient construction. Android uses the OkHttp engine (shared
 * with the rest of the Android ecosystem for proxy settings, NetworkSecurityConfig
 * trust anchors); iOS uses the Darwin engine (URLSession-backed).
 *
 * Callers normally don't instantiate this directly — `di.ServiceLocator` owns a
 * single shared HttpClient per process.
 */
public expect fun createHttpClient(): HttpClient
