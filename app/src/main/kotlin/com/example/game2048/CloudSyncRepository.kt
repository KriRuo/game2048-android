package com.example.game2048

import android.util.Log
import com.example.game2048.logic.BoardSizeOption
import com.example.game2048.logic.GameMode
import com.example.game2048.logic.TilePalette
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private const val TAG = "CloudSyncRepository"

/** This project's Firestore database is NOT the "(default)" one -- see CLAUDE.md -- so every
 *  access must target this name explicitly via the [com.google.firebase.firestore.firestore]
 *  overload that takes a database id. */
private const val FIRESTORE_DATABASE_ID = "game2048-db"
private const val USERS_COLLECTION = "users"

/**
 * A signed-in player's cross-device progress snapshot -- mirrors the subset of
 * [GameViewModel]'s locally-persisted lifetime stats/preferences worth syncing. Every field has
 * a default so the Firestore SDK's POJO mapping ([com.google.firebase.firestore.DocumentSnapshot.toObject])
 * can construct one via reflection.
 */
data class CloudProgress(
    val cumulativeScore: Long = 0L,
    val bestScore: Int = 0,
    val highestTileEver: Int = 0,
    val totalMerges: Long = 0L,
    val gamesPlayed: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakLastPlayedEpochDay: Long = 0L,
    val selectedPaletteId: String = TilePalette.DEFAULT.id,
    val selectedBoardSizeId: String = BoardSizeOption.DEFAULT.id,
    val selectedGameModeId: String = GameMode.DEFAULT.id,
    val updatedAtEpochMillis: Long = 0L,
    /** Which build last wrote this document -- lets a query answer "which version is this
     *  account on" directly, since Analytics/Crashlytics' own automatic version dimension isn't
     *  tied to a signed-in account (see BuildConfig.VERSION_NAME/VERSION_CODE). Defaults cover
     *  a doc read back from before these fields existed. */
    val appVersionName: String = "",
    val appVersionCode: Int = 0
)

/**
 * Thin wrapper around Cloud Firestore for [CloudProgress]. Every function fails soft (returns
 * null / does nothing) when Firebase isn't configured (see [BuildConfig.FIREBASE_ENABLED]) or a
 * call fails for any other reason -- cloud sync is always best-effort on top of local play,
 * never something a failure here should visibly disrupt.
 */
object CloudSyncRepository {
    // Never throws -- see AuthRepository.auth for why this matters (a throwing lazy property
    // crashes every caller, not just the one that happens to be inside a try/catch).
    private val db by lazy {
        if (!BuildConfig.FIREBASE_ENABLED) return@lazy null
        try {
            FirebaseFirestore.getInstance(FIRESTORE_DATABASE_ID)
        } catch (t: Throwable) {
            Log.w(TAG, "Firestore unavailable, cloud sync disabled for this session", t)
            null
        }
    }

    suspend fun pull(uid: String): CloudProgress? {
        val firestore = db ?: return null
        return try {
            firestore.collection(USERS_COLLECTION).document(uid).get().await()
                .toObject(CloudProgress::class.java)
        } catch (t: Exception) {
            null
        }
    }

    suspend fun push(uid: String, progress: CloudProgress) {
        val firestore = db ?: return
        try {
            firestore.collection(USERS_COLLECTION).document(uid).set(progress).await()
        } catch (t: Exception) {
            // Best-effort -- see class doc.
        }
    }
}
