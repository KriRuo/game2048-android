package com.example.game2048

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game2048.ui.theme.Game2048Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Plain framework dialog, not Compose -- shown before setContent below so it still
        // appears even if the crash this is diagnosing turns out to be somewhere in Compose
        // itself. See Game2048Application for where this gets written.
        application.consumeLastCrash()?.let { crash ->
            val crashText = TextView(this).apply {
                text = crash
                setTextIsSelectable(true)
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            AlertDialog.Builder(this)
                .setTitle("Previous launch crashed -- tap and hold the text below to copy it")
                .setView(ScrollView(this).apply { addView(crashText) })
                .setPositiveButton("OK", null)
                .show()
        }
        enableEdgeToEdge()
        setContent {
            // Created here (not inside GameScreen) so the selected palette is known before
            // Game2048Theme renders anything -- viewModel() returns the same cached instance
            // when GameScreen asks for it below.
            val viewModel: GameViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            Game2048Theme(palette = uiState.selectedPalette) {
                Game2048App(viewModel = viewModel)
            }
        }
    }
}
