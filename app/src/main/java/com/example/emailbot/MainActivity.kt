package com.example.emailbot

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.emailbot.scheduler.Notifications
import com.example.emailbot.theme.EmailBotTheme

/**
 * Permissions are not requested on start. The task list shows a card with whatever
 * is still missing, so the user decides when to grant it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every screen has the dark indigo header behind the status bar, so its icons are always white.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        Notifications.createChannels(this)

        setContent {
            EmailBotTheme {
                MainNavigation()
            }
        }
    }
}
