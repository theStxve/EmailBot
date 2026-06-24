package com.example.emailbot.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.emailbot.MainActivity
import com.example.emailbot.data.EmailLog
import com.example.emailbot.data.EmailStatus
import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.EmailTaskRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmailBotWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EmailTaskRepository(context.applicationContext)

        provideContent {
            val tasks by produceState(initialValue = emptyList<EmailTask>()) {
                repo.tasks.collect { value = it }
            }
            val logs by produceState(initialValue = emptyList<EmailLog>()) {
                repo.logs.collect { value = it }
            }

            val activeCount = tasks.count { it.isEnabled && it.status == EmailStatus.PENDING }
            val lastLog = logs.firstOrNull { it.status == EmailStatus.SENT }

            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val lastSentStr = if (lastLog != null && lastLog.sentTimeMs > 0) {
                "${lastLog.taskLabel.ifBlank { lastLog.subject.ifBlank { "E-Mail" } }} (${sdf.format(Date(lastLog.sentTimeMs))})"
            } else {
                "Keine"
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(android.graphics.Color.parseColor("#1E1B4B")))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "EmailBot",
                    style = TextStyle(
                        color = ColorProvider(android.graphics.Color.WHITE),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aktiv: $activeCount",
                        style = TextStyle(color = ColorProvider(android.graphics.Color.parseColor("#C7D2FE")))
                    )
                    Spacer(modifier = GlanceModifier.width(16.dp))
                    Text(
                        text = "Gesendet: $lastSentStr",
                        style = TextStyle(color = ColorProvider(android.graphics.Color.parseColor("#A5B4FC")))
                    )
                }
                Spacer(modifier = GlanceModifier.height(8.dp))
                Button(
                    text = "App öffnen",
                    onClick = actionStartActivity<MainActivity>(),
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }
        }
    }
}

object EmailBotWidgetUpdater {
    suspend fun update(context: Context) {
        try {
            EmailBotWidget().updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
