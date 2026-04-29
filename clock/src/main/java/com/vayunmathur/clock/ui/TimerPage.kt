package com.vayunmathur.clock.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.clock.R
import com.vayunmathur.clock.Route
import com.vayunmathur.clock.util.TimerReceiver
import com.vayunmathur.clock.mainPages
import com.vayunmathur.clock.data.Timer
import com.vayunmathur.library.ui.IconAdd
import com.vayunmathur.library.ui.IconDelete
import com.vayunmathur.library.ui.IconPause
import com.vayunmathur.library.ui.IconPlay
import com.vayunmathur.library.util.BottomNavBar
import com.vayunmathur.library.util.DatabaseViewModel
import com.vayunmathur.library.util.nowState
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerPage(backStack: NavBackStack<Route>, viewModel: DatabaseViewModel) {
    val now by nowState()
    val timers by viewModel.data<Timer>().collectAsState(initial = emptyList())

    Scaffold(topBar = {
        TopAppBar({ Text(stringResource(R.string.label_timer)) })
    }, bottomBar = {
        BottomNavBar(backStack, mainPages(), Route.Timer)
    }, floatingActionButton = {
        if (timers.isNotEmpty()) {
            FloatingActionButton({
                backStack.add(Route.NewTimerDialog())
            }) {
                IconAdd()
            }
        }
    }) { paddingValues ->
        if (timers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Button(onClick = { backStack.add(Route.NewTimerDialog()) }) {
                    IconAdd()
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.set_a_timer))
                }
            }
        } else {
            LazyColumn(
                contentPadding = paddingValues + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(timers, key = { it.id }) { timer ->
                    TimerCard(timer, now, viewModel)
                }
            }
        }
    }
}

@Composable
fun TimerCard(timer: Timer, now: Instant, viewModel: DatabaseViewModel) {
    val context = LocalContext.current

    // Calculate actual remaining time for the UI
    val realRemainingTime = remember(timer, now) {
        if (timer.isRunning) {
            timer.remainingLength - (now - timer.remainingStartTime)
        } else {
            timer.remainingLength
        }.coerceAtLeast(0.seconds)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timer.name.ifBlank { stringResource(R.string.label_timer) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = {
                    sendTimerNotification(context, timer, false)
                    viewModel.delete(timer)
                }) {
                    IconDelete()
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                val colorScheme = MaterialTheme.colorScheme
                val inactiveColor = colorScheme.outlineVariant
                val activeColor = colorScheme.primary
                val strokeWidth = 8.dp

                Canvas(Modifier.fillMaxSize()) {
                    val strokeWidthPx = strokeWidth.toPx()
                    drawCircle(inactiveColor, style = Stroke(width = strokeWidthPx), alpha = 0.3f)

                    val sweep = (realRemainingTime.inWholeMilliseconds.toFloat() /
                            timer.totalLength.inWholeMilliseconds.coerceAtLeast(1000).toFloat()) * 360f

                    drawArc(
                        color = activeColor,
                        startAngle = -90f,
                        sweepAngle = sweep.coerceAtLeast(0f),
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }

                Text(
                    text = formatTimerDuration(realRemainingTime),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (timer.isRunning) colorScheme.primary else colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        val newLength = timer.remainingLength + 1.minutes
                        val updatedTimer = timer.copy(
                            remainingLength = newLength,
                            totalLength = timer.totalLength + 1.minutes
                        )
                        viewModel.upsertAsync(updatedTimer)
                        if (timer.isRunning) {
                            sendTimerNotification(context, updatedTimer, true)
                        }
                    }
                ) {
                    Text(stringResource(R.string.button_add_minute))
                }

                Spacer(Modifier.width(16.dp))

                FloatingActionButton(
                    onClick = {
                        if (timer.isRunning) {
                            viewModel.upsertAsync(timer.stopped())
                            sendTimerNotification(context, timer, false)
                        } else {
                            val startedTimer = timer.started()
                            viewModel.upsertAsync(startedTimer)
                            sendTimerNotification(context, startedTimer, true)
                        }
                    },
                    containerColor = if (timer.isRunning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (timer.isRunning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    if (timer.isRunning) IconPause() else IconPlay()
                }
            }
        }
    }
}

fun formatTimerDuration(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (hours > 0) {
            append(hours)
            append(":")
            if (minutes < 10) append("0")
        }
        append(minutes)
        append(":")
        if (seconds < 10) append("0")
        append(seconds)
    }
}


/**
 * Helper function to communicate with the Foreground Service
 */
fun sendTimerNotification(context: Context, timer: Timer, isStarting: Boolean) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val notificationId = timer.id.hashCode()

    val alarmIntent = Intent(context, TimerReceiver::class.java).apply {
        putExtra("timer_id", timer.id)
        putExtra("timer_name", timer.name)
    }

    val pendingAlarm = PendingIntent.getBroadcast(
        context, notificationId, alarmIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    if (!isStarting) {
        nm.cancel(notificationId)
        am.cancel(pendingAlarm)
        return
    }

    // 1. Calculate the end timestamp (The "When")
    val remaining = if (timer.isRunning) {
        timer.remainingLength - (Clock.System.now() - timer.remainingStartTime)
    } else {
        timer.remainingLength
    }
    val endTimestamp = System.currentTimeMillis() + remaining.inWholeMilliseconds

    // 2. Create the Visual Notification (UI-driven)
    val notification = NotificationCompat.Builder(context, "active_timers_channel")
        .setSmallIcon(R.drawable.outline_timer_24)
        .setContentTitle(timer.name)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setWhen(endTimestamp)
        .setOngoing(true) // Makes it harder to swipe away accidentally
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .build()

    nm.notify(notificationId, notification)

    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTimestamp, pendingAlarm)
}