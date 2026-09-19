package com.example.game2048

import android.app.Application
import java.io.PrintWriter
import java.io.StringWriter

private const val CRASH_PREFS_NAME = "crash_report"
private const val KEY_LAST_CRASH = "last_crash"

/**
 * Records the previous run's uncaught exception (if any) to SharedPreferences before letting
 * the platform's default handler crash the process as normal. There's no adb/device access
 * available for diagnosing the Firebase crash-on-launch this was added for, so [MainActivity]
 * surfaces whatever lands here as a copyable dialog on the next launch instead -- the only way
 * to get a real stack trace off the device without a computer.
 */
class Game2048Application : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
                getSharedPreferences(CRASH_PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_LAST_CRASH, stackTrace)
                    .apply()
            } catch (_: Throwable) {
                // Crash reporting itself must never be why the crash handler fails to run.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

/** The stack trace saved by [Game2048Application] for the previous run's crash, if any -- read
 *  once by [MainActivity] and cleared so it's shown only once. */
fun Application.consumeLastCrash(): String? {
    val prefs = getSharedPreferences(CRASH_PREFS_NAME, Application.MODE_PRIVATE)
    val crash = prefs.getString(KEY_LAST_CRASH, null)
    if (crash != null) prefs.edit().remove(KEY_LAST_CRASH).apply()
    return crash
}
