package com.dustin.callguard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // the theme reads the same settings the screens use, so the color switch applies right away
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()
            CallGuardTheme(useSystemColors = settings.useSystemColors) {
                CallGuardApp(viewModel)
            }
        }
    }
}
