package com.wangjingfei.hometv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wangjingfei.hometv.ui.HomeTvApp
import com.wangjingfei.hometv.ui.HomeTvTheme
import com.wangjingfei.hometv.ui.AppScreen
import com.wangjingfei.hometv.ui.MainViewModel
import com.wangjingfei.hometv.ui.digitFromKeyCode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { HomeTvTheme { HomeTvApp(viewModel) } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.externalActions.collect { intent ->
                    runCatching { startActivity(intent) }
                        .onFailure { viewModel.reportUpdateActionFailure() }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val screen = viewModel.uiState.value.screen
        if (screen == AppScreen.HOME || screen == AppScreen.PLAYER) {
            digitFromKeyCode(keyCode)?.let {
                viewModel.enterDigit(it)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        viewModel.pauseForBackground()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        viewModel.resumeAfterForeground()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumePendingUpdateInstall()
    }

    override fun onDestroy() {
        if (isFinishing) viewModel.releasePlayer()
        super.onDestroy()
    }
}
