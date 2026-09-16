package com.example.game2048

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game2048.ui.theme.LocalPaletteColors
import kotlinx.coroutines.delay

private const val COMBO_POPUP_LIFETIME_MS = 900L

/** Shows a transient "x3 Combo!" callout when a single move merges more than one pair. */
@Composable
internal fun ComboPopup(comboCount: Int, moveToken: Long, modifier: Modifier = Modifier) {
    var visibleCombo by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(moveToken) {
        if (moveToken > 0 && comboCount >= 2) {
            visibleCombo = comboCount
            delay(COMBO_POPUP_LIFETIME_MS)
            visibleCombo = null
        } else {
            // Covers New Game resetting moveToken back to 0 while a popup from the
            // previous game was still showing/fading -- otherwise it's stuck forever,
            // since the branch above (which is what normally clears it) never runs.
            visibleCombo = null
        }
    }

    AnimatedVisibility(
        visible = visibleCombo != null,
        modifier = modifier,
        enter = fadeIn(tween(120)) + scaleIn(
            initialScale = 0.6f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.8f, animationSpec = tween(220))
    ) {
        Text(
            text = "×${visibleCombo ?: 0} Combo!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = LocalPaletteColors.current.accent
        )
    }
}

private const val STREAK_MILESTONE_BANNER_LIFETIME_MS = 2200L

/** Full-screen celebration shown once, the first time a streak milestone (3, 7, 14, ...
 *  days) is reached, then auto-dismisses via [onShown]. */
@Composable
internal fun StreakMilestoneBanner(milestone: Int?, onShown: () -> Unit) {
    var shownMilestone by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(milestone) {
        if (milestone != null) {
            shownMilestone = milestone
            delay(STREAK_MILESTONE_BANNER_LIFETIME_MS)
            shownMilestone = null
            onShown()
        }
    }

    AnimatedVisibility(
        visible = shownMilestone != null,
        enter = fadeIn(tween(200)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.9f, animationSpec = tween(220))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🔥", fontSize = 48.sp)
                Text(
                    text = "${shownMilestone ?: 0}-Day Streak!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalPaletteColors.current.accent
                )
            }
        }
    }
}

@Composable
internal fun GameOverlay(
    title: String,
    buttonLabel: String,
    onButtonClick: () -> Unit,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            extraContent?.invoke(this)
            OutlinedButton(
                modifier = Modifier.padding(top = 18.dp),
                onClick = onButtonClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalPaletteColors.current.accent)
            ) {
                Text(buttonLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
