package com.example.game2048

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Thin, fail-safe wrapper around Firebase Analytics/Crashlytics. Configuration comes from
 * app/google-services.json (gitignored, fetched via the Firebase CLI -- see the comment on
 * `googleServicesFile` in app/build.gradle.kts) via the standard google-services Gradle plugin,
 * which generates the resources [FirebaseOptions.fromResource] reads below -- but Firebase's
 * own *automatic* startup hook (`FirebaseInitProvider`) is deliberately removed in
 * AndroidManifest.xml, because it runs before any app code at all (before MainActivity, before
 * GameViewModel, before any try/catch we control) and a bad interaction there can crash the app
 * before a single screen is ever drawn. [init] is the *only* place Firebase actually gets
 * initialized, entirely inside this function's own try/catch. [BuildConfig.FIREBASE_ENABLED]
 * mirrors whether google-services.json existed at build time, so every function here stays safe
 * to call unconditionally even when it didn't (true for every fresh clone and for CI today).
 */
object AppAnalytics {
    private const val TAG = "AppAnalytics"

    private var analytics: FirebaseAnalytics? = null
    private var initialized = false

    /** Call once, e.g. from [com.example.game2048.GameViewModel]'s init block -- safe to call
     *  more than once (a no-op after the first real call). */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        if (!BuildConfig.FIREBASE_ENABLED) return
        try {
            val app = FirebaseApp.getApps(context).firstOrNull() ?: run {
                val options = FirebaseOptions.fromResource(context) ?: return
                FirebaseApp.initializeApp(context, options)
            }
            analytics = FirebaseAnalytics.getInstance(context)
            // Touching the instance installs Crashlytics' uncaught-exception handler.
            FirebaseCrashlytics.getInstance()
            Log.i(TAG, "Firebase initialized for project ${app.options.projectId}")
        } catch (t: Throwable) {
            // Never let a bad/missing Firebase config take the app down with it.
            Log.w(TAG, "Firebase init failed, analytics disabled for this session", t)
            analytics = null
        }
    }

    private fun logEvent(name: String, params: Map<String, Any> = emptyMap()) {
        val bundle = android.os.Bundle()
        for ((key, value) in params) {
            when (value) {
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        analytics?.logEvent(name, bundle)
    }

    fun logGameStarted() = logEvent("game_started")

    fun logLevelUp(level: Int) = logEvent("level_up", mapOf("level" to level))

    fun logStreakMilestone(days: Int) = logEvent("streak_milestone", mapOf("days" to days))

    fun logThemeUnlocked(paletteId: String) = logEvent("theme_unlocked", mapOf("theme_id" to paletteId))

    fun logPatternUnlocked(patternId: String) = logEvent("pattern_unlocked", mapOf("pattern_id" to patternId))

    fun logBoardSizeUnlocked(boardSizeId: String) = logEvent("board_size_unlocked", mapOf("board_size_id" to boardSizeId))

    fun logSignUp() = logEvent("sign_up")

    fun logSignIn() = logEvent("sign_in")

    fun logGameOver(score: Int, level: Int) = logEvent("game_over", mapOf("score" to score, "level" to level))

    fun logJokerUsed(jokerId: String) = logEvent("joker_used", mapOf("joker_id" to jokerId))

    /** Fired on every finished attempt (win, loss, or ran out of moves -- all "completed" the
     *  same way). Exists specifically to answer "does anyone actually play the Daily Challenge?"
     *  before investing further in it (more challenge types, a leaderboard, etc.) -- see
     *  CLAUDE.md's Daily Challenge section. */
    fun logDailyChallengeCompleted(score: Int) = logEvent("daily_challenge_completed", mapOf("score" to score))

    /** Ties subsequent Crashlytics reports and Analytics events to the signed-in account, or
     *  clears that link on sign-out -- otherwise both are anonymous per device/install, with no
     *  way to correlate a specific tester's bug report to a crash, or to answer cross-device
     *  questions like "do people who sign in come back more?" [uid] is a Firebase Auth uid, not
     *  PII (e.g. never the player's email) -- exactly what both SDKs' own docs call for here. */
    fun setUserId(uid: String?) {
        if (!BuildConfig.FIREBASE_ENABLED) return
        try {
            FirebaseCrashlytics.getInstance().setUserId(uid ?: "")
        } catch (_: Throwable) {
            // Crashlytics itself not initialized -- nothing to do.
        }
        try {
            analytics?.setUserId(uid)
        } catch (_: Throwable) {
            // Analytics itself not initialized -- nothing to do.
        }
    }

    /** For a future caught-but-worth-knowing-about condition; not wired to anything yet. */
    fun recordNonFatal(t: Throwable) {
        if (!BuildConfig.FIREBASE_ENABLED) return
        try {
            FirebaseCrashlytics.getInstance().recordException(t)
        } catch (_: Throwable) {
            // Crashlytics itself not initialized -- nothing to do.
        }
    }
}
