package com.dmzs.datawatchclient.di

import android.app.Application
import android.content.Context
import com.dmzs.datawatchclient.db.DatawatchDb
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.security.KeystoreManager
import com.dmzs.datawatchclient.security.TokenVault
import com.dmzs.datawatchclient.storage.DatabaseFactory
import com.dmzs.datawatchclient.storage.ServerProfileRepository
import com.dmzs.datawatchclient.storage.SessionRepository
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.createHttpClient
import com.dmzs.datawatchclient.transport.createTrustAllHttpClient
import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers

/**
 * Hand-wired dependency graph. Deliberately not a DI framework for a solo-plus-Claude
 * scale project — a single object with `lazy` members is plenty. If we outgrow it the
 * migration is local (swap callers from `ServiceLocator.x` to constructor injection).
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

    private val httpClient: HttpClient by lazy { createHttpClient() }
    private val trustAllClient: HttpClient by lazy { createTrustAllHttpClient() }

    /**
     * Build a [TransportClient] for a given server profile. Token is unwrapped from
     * the vault on each request via a suspend provider — no plaintext in memory
     * outside the request scope. When the profile has
     * [ServerProfile.trustAnchorSha256] == [TRUST_ALL_SENTINEL] (set by the
     * "Server uses a self-signed certificate" checkbox in AddServerScreen), a
     * trust-all HttpClient is used instead of the system-trust default.
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

    public const val TRUST_ALL_SENTINEL: String = "ALLOW_ALL_INSECURE"
}
