package com.dmzs.datawatchclient.auto

import android.content.Context
import com.dmzs.datawatchclient.db.DatawatchDb
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
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
 * Auto-module DI. Mirrors what `composeApp/.../ServiceLocator`
 * does but lives inside the `:auto` module so the Car-App
 * service can read the same SQLCipher DB the phone app writes
 * to and hit the same server via its own RestTransport.
 *
 * The `:auto` module can't depend on `:composeApp` (a library
 * can't pull in an `application`), so we re-compose the minimum
 * slice here. All the underlying pieces (KeystoreManager,
 * DatabaseFactory, ServerProfileRepository, SessionRepository,
 * RestTransport) already live in `:shared`.
 *
 * Call [init] once from [DatawatchMessagingService.onCreate]
 * before any screen is constructed.
 */
public object AutoServiceLocator {
    private var appContext: Context? = null
    private var _db: DatawatchDb? = null
    private var _profileRepo: ServerProfileRepository? = null
    private var _sessionRepo: SessionRepository? = null
    private var _activeStore: ActiveServerStore? = null

    public fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
    }

    public val profileRepository: ServerProfileRepository
        get() =
            _profileRepo ?: run {
                val ctx = requireNotNull(appContext) { "AutoServiceLocator not init()ed" }
                val keystore = KeystoreManager(ctx)
                val factory = DatabaseFactory(ctx, keystore)
                val db = DatawatchDb(factory.driver()).also { _db = it }
                ServerProfileRepository(db, Dispatchers.IO).also { _profileRepo = it }
            }

    public val sessionRepository: SessionRepository
        get() =
            _sessionRepo ?: run {
                // Trigger profileRepository initialisation so _db is non-null.
                profileRepository
                SessionRepository(_db!!, Dispatchers.IO).also { _sessionRepo = it }
            }

    private var _tokenVault: TokenVault? = null
    private val tokenVault: TokenVault
        get() = _tokenVault ?: run {
            val ctx = requireNotNull(appContext) { "AutoServiceLocator not init()ed" }
            TokenVault(ctx).also { _tokenVault = it }
        }

    private var _httpClient: HttpClient? = null
    private var _trustAllClient: HttpClient? = null

    private val httpClient: HttpClient
        get() = _httpClient ?: createHttpClient().also { _httpClient = it }

    private val trustAllClient: HttpClient
        get() = _trustAllClient ?: createTrustAllHttpClient().also { _trustAllClient = it }

    // Must match ServiceLocator — the auto module reads the same DB and
    // Keystore, so it must build transports the same way.
    public val TRUST_ALL_SENTINEL: String = "ALLOW_ALL_INSECURE"

    public fun transportFor(profile: ServerProfile): TransportClient {
        val alias = profile.bearerTokenRef.takeIf { it.isNotBlank() }
        val tokenProvider: (suspend () -> String)? =
            alias?.let { { tokenVault.get(it) ?: error("Missing token for profile ${profile.id}") } }
        val client = if (profile.trustAnchorSha256 == TRUST_ALL_SENTINEL) trustAllClient else httpClient
        return RestTransport(profile = profile, client = client, tokenProvider = tokenProvider)
    }

    /**
     * Shared active-profile preference with the phone app. The car
     * picker writes to this and the phone's Sessions tab reflects the
     * change next observe() tick.
     */
    public val activeServerStore: ActiveServerStore
        get() =
            _activeStore ?: run {
                val ctx = requireNotNull(appContext) { "AutoServiceLocator not init()ed" }
                ActiveServerStore(ctx).also { _activeStore = it }
            }
}
