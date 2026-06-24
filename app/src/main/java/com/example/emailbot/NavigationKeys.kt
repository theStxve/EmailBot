package com.example.emailbot

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object SmtpAccounts : NavKey
@Serializable data class SmtpSettings(val accountId: Int? = null) : NavKey
