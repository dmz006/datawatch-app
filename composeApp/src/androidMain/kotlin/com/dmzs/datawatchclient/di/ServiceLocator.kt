package com.dmzs.datawatchclient.di

import android.app.Application
import android.content.Context
import com.dmzs.datawatchclient.db.DatawatchDb
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.security.KeystoreManager
import com.dmzs.datawatchclient.security.TokenVault
import com.dmzs.datawatchclient.storage.DatabaseFactory
import com.dmzs.datawatchclient.storage.ServerProfileRepository
import com.dmzs.datawatchclient.storage.SessionEventRepository
import com.dmzs.datawatchclient.storage.SessionRepository
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.createHttpClient
import com.dmzs.datawatchclient.transport.createHttpClientWithWebSockets
import com.dmzs.datawatchclient.transport.createTrustAllHttpClient
import com.dmzs.datawatchclient.transport.rest.RestTransport
import com.dmzs.datawatchclient.transport.ws.WebSocketTransport
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers

/**
 * Hand-wired dependency graph. Deliberately not a DI framework for a solo-plus-Claude
 * scale project — a single object with `lazy` members is plenty.
 *
 * Call [init] once from [com.dmzs.datawatchclient.DatawatchApp.onCreate] before any
 * member is read.
 */
public object ServiceLocator {

    private lateinit var appContext: Context

    public fun init(app: Application) {
        appContext = app.applicationContext
    }

    private val keystoreManager: KeystoreManager by lazy { KeystoreManager(appContext) }

    private val databaseFactory: DatabaseFactory by lazy {
        DatabaseFactory(appContext, keystoreManager)
    }

    private val database: DatawatchDb by lazy { DatawatchDb(databaseFactory.driver()) }

    public val tokenVault: TokenVault by lazy { TokenVault(appContext) }

    public val profileRepository: ServerProfileRepository by lazy {
        ServerProfileRepository(database, Dispatchers.IO)
    }

    public val sessionRepository: SessionRepository by lazy {
        SessionRepository(database, Dispatchers.IO)
    }

    public val sessionEventRepository: SessionEventRepository by lazy {
        SessionEventRepository(database, Dispatchers.IO)
    }

    private val httpClient: HttpClient by lazy { createHttpClient() }
    private val trustAllClient: HttpClient by lazy { createTrustAllHttpClient() }
    private val wsClient: HttpClient by lazy { createHttpClientWithWebSockets(trustAll = false) }
    private val wsTrustAllClient: HttpClient by lazy {
        createHttpClientWithWebSockets(trustAll = true)
    }

    public const val TRUST_ALL_SENTINEL: String = "ALLOW_ALL_INSECURE"

    /**
     * Build a [TransportClient] for a given server profile. When the profile has
     * [ServerProfile.trustAnchorSha256] == [TRUST_ALL_SENTINEL], a trust-all
     * HttpClient is used instead of the system-trust default.
     */
    public fun transportFor(profile: ServerProfile): TransportClient {
        val alias = profile.bearerTokenRef.takeIf { it.isNotBlank() }
        val tokenProvider: (suspend () -> String)? = alias?.let {
            { tokenVault.get(it) ?: error("Missing token for profile ${profile.id}") }
        }
        val client = if (profile.trustAnchorSha256 == TRUST_ALL_SENTINEL) {
            trustAllClient
        } else {
            httpClient
        }
        return RestTransport(profile, client, tokenProvider)
    }

    /**
     * Build a [WebSocketTransport] for a given server profile. Uses the trust-all
     * WS client when the profile opted into self-signed certs. Token provider
     * mirrors [transportFor].
     */
    public fun wsTransportFor(profile: ServerProfile): WebSocketTransport {
        val alias = profile.bearerTokenRef.takeIf { it.isNotBlank() }
        val tokenProvider: (suspend () -> String)? = alias?.let {
            { tokenVault.get(it) ?: error("Missing token for profile ${profile.id}") }
        }
        val client = if (profile.trustAnchorSha256 == TRUST_ALL_SENTINEL) {
            wsTrustAllClient
        } else {
            wsClient
        }
        return WebSocketTransport(profile, client, tokenProvider)
    }
}
