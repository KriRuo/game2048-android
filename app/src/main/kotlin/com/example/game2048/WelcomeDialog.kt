package com.example.game2048

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.game2048.ui.theme.LocalPaletteColors

/** One page of [WelcomeDialog]'s first-run walkthrough. */
private data class WelcomePage(val title: String, val body: String)

private val WELCOME_PAGES = listOf(
    WelcomePage(
        title = "Welcome to 2048!",
        body = "Swipe up, down, left, or right to slide every tile at once. Two tiles with " +
            "the same number merge into one, doubling its value. Keep merging to build up " +
            "toward 2048 — and beyond."
    ),
    WelcomePage(
        title = "Original vs. Extended",
        body = "Original is the classic game: swipe only, no Undo, no Jokers. Extended (the " +
            "default) adds a single-move Undo plus five Jokers you can use mid-game. Pick " +
            "your mode on this screen any time — switching never touches a board already " +
            "in progress."
    ),
    WelcomePage(
        title = "Jokers",
        body = "In Extended mode, tap a Joker button then tap a tile to use it: Teleport " +
            "moves a tile, Swap exchanges two, Bomb removes one, Double doubles it in place. " +
            "Rotate spins the whole board instantly, no target needed. Each Joker gives you " +
            "1 use per game."
    ),
    WelcomePage(
        title = "Level up to unlock more",
        body = "Every point you score adds to your Level — it's cumulative across every " +
            "game you've ever played, and never resets when a board does. Leveling up unlocks " +
            "bigger boards (5×5, 6×6, 8×8) and new color themes, both selectable " +
            "from this screen."
    ),
    WelcomePage(
        title = "Make it yours",
        body = "Use the icons above — Theme, board size, and Stats — to customize " +
            "your game and check your progress any time. Come back daily to build a streak, too."
    )
)

/** First-run walkthrough, shown automatically once per install (see
 *  [GameViewModel.onWelcomeDismissed]/[GameViewModel.onDebugResetWelcome]) and reopenable any
 *  time from the Start Screen's "?" icon. A short paged carousel rather than a wall of text,
 *  matching the reading pace of someone who hasn't opened the app before. */
@Composable
internal fun WelcomeDialog(onDismiss: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val isLastPage = page == WELCOME_PAGES.lastIndex
    val current = WELCOME_PAGES[page]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(current.title) },
        text = {
            Column {
                Text(current.body, style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WELCOME_PAGES.indices.forEach { i ->
                        val accent = LocalPaletteColors.current.accent
                        Row(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (i == page) accent else accent.copy(alpha = 0.25f))
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isLastPage) onDismiss() else page += 1
            }) {
                Text(if (isLastPage) "Let's play!" else "Next")
            }
        },
        dismissButton = if (isLastPage) null else {
            { TextButton(onClick = onDismiss) { Text("Skip") } }
        }
    )
}
