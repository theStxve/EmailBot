package com.example.emailbot

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
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
        // Gives every screen its own saved state and ViewModel.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(onOpenAccounts = { backStack.add(SmtpAccounts) })
            }
            entry<SmtpAccounts> {
                SmtpAccountsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onEditAccount = { accountId -> backStack.add(SmtpSettings(accountId)) },
                    onNewAccount = { backStack.add(SmtpSettings(null)) }
                )
            }
            entry<SmtpSettings> { key ->
                SettingsScreen(
                    accountId = key.accountId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
