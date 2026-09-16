package com.example.game2048

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Confirms the player really wants to abandon the current board before
 *  [GameViewModel.onNewGame] runs -- shown when the persistent New Game button
 *  ([Header]/[Sidebar]) is tapped mid-game (see [GameScreen]), so a stray tap doesn't silently
 *  wipe a board in progress. Not shown once the game has already ended, since there's nothing
 *  left to lose at that point -- see [GameScreen]'s `isGameOver` check. */
@Composable
internal fun ConfirmNewGameDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start fresh?") },
        text = { Text("This will end your current game and reset the board.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("New Game") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
