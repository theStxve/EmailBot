package com.example.emailbot

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.emailbot.ui.main.MainScreen
import com.example.emailbot.ui.settings.SettingsScreen
import com.example.emailbot.ui.settings.SmtpAccountsScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    onOpenSettings = { backStack.add(SmtpAccounts) },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<SmtpAccounts> {
                SmtpAccountsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onEditAccount = { accountId -> backStack.add(SmtpSettings(accountId)) },
                    onNewAccount = { backStack.add(SmtpSettings(null)) },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<SmtpSettings> { entry ->
                SettingsScreen(
                    accountId = entry.accountId,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
        }
    )
}
