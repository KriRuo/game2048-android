package com.example.game2048

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Thin, fail-safe wrapper around Firebase Analytics/Crashlytics. This project deliberately
 * skips the google-services Gradle plugin (which would require committing a
 * google-services.json) -- see the comment on `firebasePropertiesFile` in app/build.gradle.kts.
 * Firebase is instead configured from [BuildConfig] fields sourced from an optional, gitignored
 * `firebase.properties`, and initialized manually here via [FirebaseOptions] rather than relying
 * on the library's own auto-init (which is removed in AndroidManifest.xml).
 *
 * Every function here is safe to call unconditionally from anywhere in the app: with no
 * `firebase.properties` present (the default -- true for every clone of this repo and for CI),
 * [init] leaves [analytics]/[crashlytics] null and every logging call below silently no-ops.
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
            val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build()
            )
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
