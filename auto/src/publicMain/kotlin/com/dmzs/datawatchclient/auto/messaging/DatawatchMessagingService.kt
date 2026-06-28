package com.dmzs.datawatchclient.auto.messaging

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.dmzs.datawatchclient.auto.AutoServiceLocator
import com.dmzs.datawatchclient.auto.AutoSummaryScreen
import com.dmzs.datawatchclient.auto.R

/** Must match [com.dmzs.datawatchclient.push.NotificationPoster.EXTRA_CAR_SESSION_ID]. */
private const val CAR_SESSION_ID_EXTRA = "dw.car.session_id"

/** Must match [com.dmzs.datawatchclient.push.NotificationPoster.EXTRA_CAR_SESSION_TITLE]. */
private const val CAR_SESSION_TITLE_EXTRA = "dw.car.session_title"

/** Must match [com.dmzs.datawatchclient.push.NotificationPoster.EXTRA_CAR_AUTO_PLAY_LONG]. */
private const val CAR_AUTO_PLAY_LONG_EXTRA = "dw.car.auto_play_long"

/** Must match [com.dmzs.datawatchclient.push.NotificationPoster.EXTRA_CAR_AUTO_VOICE_REPLY]. */
private const val CAR_AUTO_VOICE_REPLY_EXTRA = "dw.car.auto_voice_reply"

/**
 * Public Android Auto Messaging-template service per ADR-0031.
 * Play-compliant: TTS inbound, voice reply, no free-form UI, no terminal.
 *
 * Opens [AutoSummaryScreen] as the root hub — shows session counts,
 * server vitals inline, and last completed task. ActionStrip exposes
 * About (info icon) and Monitor. Sessions and Automata are in the list.
 *
 * Navigation routing:
 *   contentIntent tap → Gearhead HOST routes to Session.onNewIntent()
 *   addAction() button → Android fires startService() → onStartCommand()
 *
 * Both paths delegate to [navigateFromIntent] so logic stays in one place.
 * [activeSession] holds the bound session so onStartCommand() can reach
 * its CarContext and ScreenManager.
 */
public class DatawatchMessagingService : CarAppService() {

    // Retained while the session is alive so onStartCommand() can access
    // the car screen stack. CarAppExtender.addAction() PendingIntents are
    // delivered via startService() → onStartCommand(), NOT onNewIntent(),
    // so we need this reference to navigate the car app from that path.
    private var activeSession: Session? = null

    // When a notification action button fires startService() before the car
    // session has been established (user hasn't opened the app on the head
    // unit yet), activeSession is null and navigation can't happen immediately.
    // Store the intent here; onCreateScreen() processes it once the session exists.
    @Volatile private var pendingNavIntent: Intent? = null

    override fun onCreate() {
        super.onCreate()
        AutoServiceLocator.init(applicationContext)
    }

    override fun createHostValidator(): HostValidator =
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            // Debug builds: permissive so the Desktop Head Unit
            // simulator binds without signing-cert fuss.
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            // Release builds: strict allowlist per ADR-0031 +
            // Google's Auto host-validation rules. The
            // hosts_allowlist array is in publicMain/res and is
            // included via res.srcDirs in auto/build.gradle.kts.
            //
            // Using R.array.hosts_allowlist directly (not getIdentifier)
            // so the reference is verified at compile time and can never
            // silently return 0 if the resource is missing.
            HostValidator.Builder(this)
                .addAllowedHosts(R.array.hosts_allowlist)
                .build()
        }

    override fun onCreateSession(): Session =
        object : Session() {
            init { activeSession = this }

            override fun onCreateScreen(intent: android.content.Intent): AutoSummaryScreen {
                val pending = pendingNavIntent
                if (pending != null) {
                    pendingNavIntent = null
                    // Defer navigation until after the root screen is installed — popToRoot()
                    // requires the stack to exist, and Car framework sets it after this returns.
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            val sm = carContext.getCarService(ScreenManager::class.java)
                            navigateFromIntent(pending, sm, carContext)
                        } catch (e: Exception) {
                            android.util.Log.w(TAG, "pendingNavIntent nav failed: ${e.message}")
                        }
                    }
                }
                return AutoSummaryScreen(carContext)
            }

            override fun onNewIntent(intent: android.content.Intent) {
                // contentIntent tap: Gearhead routes here. Delegate to shared nav logic.
                val screenManager = carContext.getCarService(ScreenManager::class.java)
                if (navigateFromIntent(intent, screenManager, carContext)) return

                // Voice actions from Google Assistant — spoken text arrives via
                // android.speech.RecognizerIntent.EXTRA_RESULTS.
                val voiceResults = intent.getStringArrayListExtra("android.speech.extra.RESULTS")
                val spokenText = voiceResults?.firstOrNull() ?: return
                val cmd = com.dmzs.datawatchclient.auto.voice.parseVoiceCommand(spokenText)
                screenManager.popToRoot()
                screenManager.push(
                    com.dmzs.datawatchclient.auto.voice.VoiceStatusScreen(carContext, cmd),
                )
            }
        }

    /**
     * Handles [CarAppExtender.addAction] button taps.
     *
     * Gearhead only routes [contentIntent] through [Session.onNewIntent]; action
     * buttons fire [startService] directly, landing here. We retrieve the active
     * session's [CarContext] and delegate to [navigateFromIntent].
     *
     * If the car session isn't active yet (user hasn't opened the app on the head
     * unit), we queue the intent in [pendingNavIntent] so [onCreateScreen] can
     * process it once the Car framework establishes the session.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val sess = activeSession
            if (sess != null) {
                try {
                    val sm = sess.carContext.getCarService(ScreenManager::class.java)
                    navigateFromIntent(intent, sm, sess.carContext)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "onStartCommand nav failed: ${e.message}")
                }
            } else if (intent.hasExtra(CAR_SESSION_ID_EXTRA)) {
                // Car session not yet active — queue for onCreateScreen().
                pendingNavIntent = intent
                android.util.Log.d(TAG, "queued pendingNavIntent for session ${intent.getStringExtra(CAR_SESSION_ID_EXTRA)}")
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Shared navigation logic for both [onNewIntent] and [onStartCommand].
     * Returns true if a session navigation was performed, false if the intent
     * carried no session ID (caller may handle voice-action fallback).
     */
    private fun navigateFromIntent(
        intent: android.content.Intent,
        screenManager: ScreenManager,
        carCtx: CarContext,
    ): Boolean {
        val sessionId = intent.getStringExtra(CAR_SESSION_ID_EXTRA) ?: return false
        val sessionTitle = intent.getStringExtra(CAR_SESSION_TITLE_EXTRA) ?: sessionId
        val autoPlayLong = intent.getBooleanExtra(CAR_AUTO_PLAY_LONG_EXTRA, false)
        val autoVoiceReply = intent.getBooleanExtra(CAR_AUTO_VOICE_REPLY_EXTRA, false)

        // §8: always pop to root before pushing to stay within the 5-screen limit.
        screenManager.popToRoot()
        screenManager.push(
            com.dmzs.datawatchclient.auto.AutoSessionDetailScreen(
                carCtx,
                sessionId,
                sessionTitle,
                autoPlayLong = autoPlayLong,
            ),
        )
        // "Reply" notification button: go straight to voice input on top of session.
        if (autoVoiceReply) {
            screenManager.push(
                com.dmzs.datawatchclient.auto.VoiceRecordingScreen(
                    carCtx,
                    sessionId,
                    sessionTitle,
                ),
            )
        }
        return true
    }

    private companion object {
        const val TAG = "DatawatchMsgSvc"
    }
}
