package com.example.emailbot.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for our AndroidViewModels. The per-screen ViewModelStore from Navigation 3
 * doesn't put the Application into CreationExtras, so the default factory would crash.
 */
@Composable
fun appViewModelFactory(): ViewModelProvider.Factory {
    val app = LocalContext.current.applicationContext as Application
    return remember(app) { ViewModelProvider.AndroidViewModelFactory.getInstance(app) }
}
