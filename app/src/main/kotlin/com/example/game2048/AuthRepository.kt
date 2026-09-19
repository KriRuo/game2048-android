package com.example.game2048

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "AuthRepository"

/**
 * Thin wrapper around Firebase Email/Password Authentication. Cloud sync (this, plus
 * [CloudSyncRepository]) is entirely optional plumbing on top of the game -- every function
 * here fails soft when Firebase isn't configured (see [BuildConfig.FIREBASE_ENABLED]) *or when
 * obtaining/using a Firebase instance throws for any other reason* (network hiccup on a cold
 * start, Play Services unavailable, anything), so the game is always fully playable
 * offline/signed-out; nothing here gates local play or is allowed to crash app startup.
 */
object AuthRepository {
    sealed interface Outcome {
        data class Success(val uid: String) : Outcome
        data class Failure(val message: String) : Outcome
    }

    sealed interface ResetOutcome {
        data object Sent : ResetOutcome
        data class Failure(val message: String) : ResetOutcome
    }

    // Never throws -- FirebaseAuth.getInstance() failing (for any reason) just leaves this
    // null, same as FIREBASE_ENABLED being false, rather than crashing every caller including
    // init() below, which GameViewModel calls unconditionally on every app launch.
    private val auth: FirebaseAuth? by lazy {
        if (!BuildConfig.FIREBASE_ENABLED) return@lazy null
        try {
            FirebaseAuth.getInstance()
        } catch (t: Throwable) {
            Log.w(TAG, "FirebaseAuth unavailable, cloud sync disabled for this session", t)
            null
        }
    }

    private val _currentUserId = MutableStateFlow<String?>(null)

    /** The signed-in user's uid, or null when signed out (or Firebase isn't configured). */
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    /** Call once, e.g. from [GameViewModel]'s init block -- safe to call more than once. */
    fun init() {
        try {
            val firebaseAuth = auth ?: return
            _currentUserId.value = firebaseAuth.currentUser?.uid
            firebaseAuth.addAuthStateListener { _currentUserId.value = it.currentUser?.uid }
        } catch (t: Throwable) {
            Log.w(TAG, "AuthRepository.init failed, cloud sync disabled for this session", t)
        }
    }

    suspend fun signUp(email: String, password: String): Outcome {
        val firebaseAuth = auth ?: return Outcome.Failure("Cloud sync isn't set up for this build.")
        return try {
            val uid = firebaseAuth.createUserWithEmailAndPassword(email, password).await().user?.uid
                ?: return Outcome.Failure("Sign-up succeeded but no account was returned.")
            Outcome.Success(uid)
        } catch (t: Exception) {
            Outcome.Failure(signUpErrorMessage(t))
        }
    }

    suspend fun signIn(email: String, password: String): Outcome {
        val firebaseAuth = auth ?: return Outcome.Failure("Cloud sync isn't set up for this build.")
        return try {
            val uid = firebaseAuth.signInWithEmailAndPassword(email, password).await().user?.uid
                ?: return Outcome.Failure("Sign-in succeeded but no account was returned.")
            Outcome.Success(uid)
        } catch (t: Exception) {
            Outcome.Failure(signInErrorMessage(t))
        }
    }

    suspend fun sendPasswordResetEmail(email: String): ResetOutcome {
        val firebaseAuth = auth ?: return ResetOutcome.Failure("Cloud sync isn't set up for this build.")
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            ResetOutcome.Sent
        } catch (t: Exception) {
            ResetOutcome.Failure(resetErrorMessage(t))
        }
    }

    // Firebase's own exception messages are raw and developer-facing (e.g. "The supplied auth
    // credential is incorrect, malformed or has expired."). Each flow below translates the
    // error codes that actually matter for it into something a player can act on, falling back
    // to a short generic message for anything unexpected rather than surfacing SDK internals.

    private fun signInErrorMessage(t: Exception): String = when {
        t is FirebaseNetworkException -> "No internet connection. Check your network and try again."
        t is FirebaseAuthException && t.errorCode == "ERROR_TOO_MANY_REQUESTS" ->
            "Too many attempts. Please wait a moment and try again."
        t is FirebaseAuthException && t.errorCode == "ERROR_INVALID_EMAIL" ->
            "That doesn't look like a valid email address."
        t is FirebaseAuthException && t.errorCode == "ERROR_USER_DISABLED" ->
            "This account has been disabled."
        // Modern Firebase deliberately uses one generic code (ERROR_INVALID_CREDENTIAL) for
        // both "wrong password" and "no such account", so it can't be told which one this is
        // without confirming to an attacker which emails have accounts -- stays just as vague.
        t is FirebaseAuthException -> "Incorrect email or password."
        else -> "Couldn't sign in. Please try again."
    }

    private fun signUpErrorMessage(t: Exception): String = when {
        t is FirebaseNetworkException -> "No internet connection. Check your network and try again."
        t is FirebaseAuthException && t.errorCode == "ERROR_EMAIL_ALREADY_IN_USE" ->
            "An account already exists for that email -- try signing in instead."
        t is FirebaseAuthException && t.errorCode == "ERROR_INVALID_EMAIL" ->
            "That doesn't look like a valid email address."
        t is FirebaseAuthException && t.errorCode == "ERROR_WEAK_PASSWORD" ->
            "Password must be at least 6 characters."
        else -> "Couldn't create an account. Please try again."
    }

    private fun resetErrorMessage(t: Exception): String = when {
        t is FirebaseNetworkException -> "No internet connection. Check your network and try again."
        t is FirebaseAuthException && t.errorCode == "ERROR_INVALID_EMAIL" ->
            "That doesn't look like a valid email address."
        t is FirebaseAuthException && t.errorCode == "ERROR_USER_NOT_FOUND" ->
            "No account found for that email."
        else -> "Couldn't send the reset email. Please try again."
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (t: Throwable) {
            Log.w(TAG, "Sign-out failed", t)
        }
    }
}
