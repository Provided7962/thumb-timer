package hu.laszlobaki.thumbtimer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import hu.laszlobaki.thumbtimer.ui.theme.ThumbTimerTheme

private val ThumbButtonHeight = 64.dp
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
        setContent {
            ThumbTimerApp()
        }
    }
}

@Composable
fun ThumbTimerApp() {
    var selectedSeconds by remember { mutableIntStateOf(5 * 60) }
    var remainingSeconds by remember { mutableIntStateOf(selectedSeconds) }
    var isRunning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val serviceRemainingSeconds by TimerService.remainingSecondsFlow.collectAsState()
    val serviceIsRunning by TimerService.isRunningFlow.collectAsState()
    LaunchedEffect(serviceIsRunning) {
        if (!serviceIsRunning && serviceRemainingSeconds > 0) {
            remainingSeconds = serviceRemainingSeconds
        }

        isRunning = serviceIsRunning
    }
    val resetSignal by TimerService.resetSignalFlow.collectAsState()
    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            remainingSeconds = selectedSeconds
            isRunning = false
        }
    }

/*
    LaunchedEffect(isRunning) {
        while (isRunning && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }

        if (remainingSeconds == 0 && isRunning) {
            isRunning = false
            vibrate(context)
        }
    }
*/
    ThumbTimerTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val displayedSeconds =
                    if (serviceRemainingSeconds > 0) serviceRemainingSeconds else remainingSeconds

                TimerDisplay(displayedSeconds)

                ThumbControls(
                    isRunning = isRunning,
                    onSelectTime = { seconds ->
                        selectedSeconds = seconds
                        remainingSeconds = seconds
                        isRunning = false
                    },
                    onStartPause = {
                        if (!isRunning) {

                            val intent = Intent(context, TimerService::class.java)

                            intent.putExtra("seconds", remainingSeconds)

                            ContextCompat.startForegroundService(
                                context,
                                intent
                            )

                            isRunning = true

                        } else {
                            val intent = Intent(context, TimerService::class.java)
                            intent.action = TimerService.ACTION_STOP

                            context.startService(intent)

                            remainingSeconds = serviceRemainingSeconds

                            isRunning = false
                        }
                    },
                    onReset = {
                        val intent = Intent(context, TimerService::class.java)
                        intent.action = TimerService.ACTION_RESET

                        context.startService(intent)

                        remainingSeconds = selectedSeconds
                        isRunning = false
                    }
                )
            }
        }
    }
}

@Composable
fun TimerDisplay(seconds: Int) {
    val minutes = seconds / 60
    val remaining = seconds % 60

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.45f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Thumb Timer",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "%02d:%02d".format(minutes, remaining),
            style = MaterialTheme.typography.displayLarge
        )
    }
}

@Composable
fun ThumbControls(
    isRunning: Boolean,
    onSelectTime: (Int) -> Unit,
    onStartPause: () -> Unit,
    onReset: () -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimerButton("5 min", Modifier.weight(1f)) { onSelectTime(5 * 60) }
            TimerButton("10 min", Modifier.weight(1f)) { onSelectTime(10 * 60) }
            TimerButton("15 min", Modifier.weight(1f)) { onSelectTime(15 * 60) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom button
        Button(
            onClick = { showCustomDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(ThumbButtonHeight)
        ) {
            Text("Custom")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onStartPause,
            modifier = Modifier
                .fillMaxWidth()
                .height(ThumbButtonHeight)
        ) {
            Text(if (isRunning) "Pause" else "Start")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(ThumbButtonHeight)
        ) {
            Text("Reset")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showCustomDialog) {
        var minutesText by remember { mutableStateOf("") }
        var secondsText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Custom Timer") },
            text = {
                Column {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter { c -> c.isDigit() } },
                        label = { Text("Minutes") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { secondsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Seconds") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = minutesText.toIntOrNull() ?: 0
                    val seconds = secondsText.toIntOrNull() ?: 0
                    val totalSeconds = minutes * 60 + seconds
                    if (totalSeconds > 0) {
                        onSelectTime(totalSeconds)
                    }
                    showCustomDialog = false
                }) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TimerButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(ThumbButtonHeight)
    ) {
        Text(text)
    }
}

fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as VibratorManager

        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE)
                as Vibrator
    }

    vibrator.vibrate(
        VibrationEffect.createOneShot(
            600,
            VibrationEffect.DEFAULT_AMPLITUDE
        )
    )
}