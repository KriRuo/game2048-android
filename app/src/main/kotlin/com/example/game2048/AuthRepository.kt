package com.example.game2048

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around Firebase Email/Password Authentication. Cloud sync (this, plus
 * [CloudSyncRepository]) is entirely optional plumbing on top of the game -- every function
 * here fails soft when Firebase isn't configured (see [BuildConfig.FIREBASE_ENABLED]), so the
 * game is always fully playable offline/signed-out; nothing here gates local play.
 */
object AuthRepository {
    sealed interface Outcome {
        data class Success(val uid: String) : Outcome
        data class Failure(val message: String) : Outcome
    }

    private val auth: FirebaseAuth? by lazy {
        if (BuildConfig.FIREBASE_ENABLED) FirebaseAuth.getInstance() else null
    }

    private val _currentUserId = MutableStateFlow<String?>(null)

    /** The signed-in user's uid, or null when signed out (or Firebase isn't configured). */
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    /** Call once, e.g. from [GameViewModel]'s init block -- safe to call more than once. */
    fun init() {
        val firebaseAuth = auth ?: return
        _currentUserId.value = firebaseAuth.currentUser?.uid
        firebaseAuth.addAuthStateListener { _currentUserId.value = it.currentUser?.uid }
    }

    suspend fun signUp(email: String, password: String): Outcome {
        val firebaseAuth = auth ?: return Outcome.Failure("Cloud sync isn't set up for this build.")
        return try {
            val uid = firebaseAuth.createUserWithEmailAndPassword(email, password).await().user?.uid
                ?: return Outcome.Failure("Sign-up succeeded but no account was returned.")
            Outcome.Success(uid)
        } catch (t: Exception) {
            Outcome.Failure(t.message ?: "Sign-up failed.")
        }
    }

    suspend fun signIn(email: String, password: String): Outcome {
        val firebaseAuth = auth ?: return Outcome.Failure("Cloud sync isn't set up for this build.")
        return try {
            val uid = firebaseAuth.signInWithEmailAndPassword(email, password).await().user?.uid
                ?: return Outcome.Failure("Sign-in succeeded but no account was returned.")
            Outcome.Success(uid)
        } catch (t: Exception) {
            Outcome.Failure(t.message ?: "Sign-in failed.")
        }
    }

    fun signOut() {
        auth?.signOut()
    }
}
