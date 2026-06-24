package com.example.emailbot.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class EmailLog(
    val id: String = UUID.randomUUID().toString(),
    val taskId: Int,
    val taskLabel: String,
    val recipient: String,
    val subject: String,
    val sentTimeMs: Long,
    val status: EmailStatus, // SENT or FAILED
    val errorMessage: String = ""
)
