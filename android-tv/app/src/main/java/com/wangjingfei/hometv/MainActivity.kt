package com.wangjingfei.hometv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wangjingfei.hometv.ui.HomeTvApp
import com.wangjingfei.hometv.ui.HomeTvTheme
import com.wangjingfei.hometv.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

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
    }

    override fun onStop() {
        viewModel.pauseForBackground()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        viewModel.resumeAfterForeground()
    }

    override fun onDestroy() {
        if (isFinishing) viewModel.releasePlayer()
        super.onDestroy()
    }
}
