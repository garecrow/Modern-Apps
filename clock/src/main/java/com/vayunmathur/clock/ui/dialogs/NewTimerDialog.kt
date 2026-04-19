package com.vayunmathur.clock.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.clock.R
import com.vayunmathur.clock.Route
import com.vayunmathur.clock.data.Timer
import com.vayunmathur.clock.ui.sendTimerNotification
import com.vayunmathur.library.util.DatabaseViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
fun NewTimerDialog(
    backStack: NavBackStack<Route>,
    viewModel: DatabaseViewModel,
    initialLengthSeconds: Int? = null,
    initialMessage: String? = null
) {
    var name by remember { mutableStateOf(initialMessage ?: "") }
    var minutesStr by remember { 
        mutableStateOf(initialLengthSeconds?.let { (it / 60).toString() } ?: "") 
    }
    var secondsStr by remember { 
        mutableStateOf(initialLengthSeconds?.let { (it % 60).toString() } ?: "") 
    }
    
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { backStack.pop() },
        title = { Text(stringResource(R.string.label_new_timer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutesStr,
                        onValueChange = { if (it.length <= 2) minutesStr = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.field_minutes)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = secondsStr,
                        onValueChange = { if (it.length <= 2) secondsStr = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.field_seconds)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val m = minutesStr.toIntOrNull() ?: 0
                    val s = secondsStr.toIntOrNull() ?: 0
                    if (m > 0 || s > 0) {
                        val duration = m.minutes + s.seconds
                        val timer = Timer(true, name, Clock.System.now(), duration, duration)
                        viewModel.upsertAsync(timer) {
                            sendTimerNotification(context, timer.copy(id = it), true)
                        }
                        backStack.pop()
                    }
                },
                enabled = (minutesStr.toIntOrNull() ?: 0) > 0 || (secondsStr.toIntOrNull() ?: 0) > 0
            ) {
                Text(stringResource(R.string.button_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { backStack.pop() }) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}
