package com.example.game2048

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

/** Optional cloud sync via Firebase Email/Password auth -- entirely separate from local play,
 *  which always works regardless of sign-in state. Reachable from [StartScreen]. */
@Composable
internal fun AccountDialog(
    signedInUserId: String?,
    authBusy: Boolean,
    authError: String?,
    onSignUp: (email: String, password: String) -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onSignOut: () -> Unit,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (signedInUserId != null) "Cloud Sync" else "Sign In") },
        text = {
            if (signedInUserId != null) {
                Text("Your progress syncs across devices while signed in.")
            } else {
                AccountForm(
                    busy = authBusy,
                    error = authError,
                    onDismissError = onDismissError,
                    onSignUp = onSignUp,
                    onSignIn = onSignIn
                )
            }
        },
        confirmButton = {
            if (signedInUserId != null) {
                TextButton(onClick = { onSignOut(); onDismiss() }) { Text("Sign Out") }
            } else {
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        },
        dismissButton = {
            if (signedInUserId != null) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun AccountForm(
    busy: Boolean,
    error: String?,
    onDismissError: () -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onSignIn: (email: String, password: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                if (error != null) onDismissError()
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (error != null) onDismissError()
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (busy) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { onSignIn(email, password) }) { Text("Sign In") }
                Button(onClick = { onSignUp(email, password) }) { Text("Sign Up") }
            }
        }
    }
}
