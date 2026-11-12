package com.example.emailbot.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.emailbot.MainActivity
import com.example.emailbot.data.EmailLog
import com.example.emailbot.data.EmailStatus
import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.EmailTaskRepository
import com.example.emailbot.data.displayTitle
import com.example.emailbot.data.isActive
import com.example.emailbot.scheduler.EmailScheduler
import com.example.emailbot.ui.formatRelative

class EmailBotWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EmailTaskRepository(context.applicationContext)

        provideContent {
            val tasks by produceState(initialValue = emptyList<EmailTask>()) { repo.tasks.collect { value = it } }
            val logs by produceState(initialValue = emptyList<EmailLog>()) { repo.logs.collect { value = it } }

            val active = tasks.filter { it.isActive }
            val next = active.minByOrNull { EmailScheduler.nextTriggerMs(it) }
            val lastLog = logs.firstOrNull()

            val white = ColorProvider(Color.White)
            val soft = ColorProvider(Color(0xFFC7D2FE))

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(24.dp)
                    .background(Color(0xFF3730A3))
                    .padding(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (active.size == 1) "1 aktiver Zeitplan" else "${active.size} aktive Zeitpläne",
                    style = TextStyle(color = soft, fontSize = 12.sp)
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    next?.let { "Nächste: ${formatRelative(EmailScheduler.nextTriggerMs(it))}" } ?: "Nichts geplant",
                    style = TextStyle(color = white, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (next != null) {
                    Text(next.displayTitle, style = TextStyle(color = soft, fontSize = 13.sp), maxLines = 1)
                }
                if (lastLog != null) {
                    Spacer(GlanceModifier.height(8.dp))
                    val ok = lastLog.status == EmailStatus.SENT
                    Text(
                        (if (ok) "✓ Zuletzt gesendet " else "✗ Fehlgeschlagen ") + formatRelative(lastLog.sentTimeMs),
                        style = TextStyle(color = ColorProvider(if (ok) Color(0xFF86EFAC) else Color(0xFFFCA5A5)), fontSize = 12.sp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

object EmailBotWidgetUpdater {
    suspend fun update(context: Context) {
        runCatching { EmailBotWidget().updateAll(context) }
    }
}
