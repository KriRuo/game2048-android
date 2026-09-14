package com.example.game2048

import android.os.Bundle
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
        enableEdgeToEdge()
        setContent {
            // Created here (not inside GameScreen) so the selected palette is known before
            // Game2048Theme renders anything -- viewModel() returns the same cached instance
            // when GameScreen asks for it below.
            val viewModel: GameViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            Game2048Theme(palette = uiState.selectedPalette) {
                GameScreen(viewModel = viewModel)
            }
        }
    }
}
