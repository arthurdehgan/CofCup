package app.cofcup.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cofcup.R
import app.cofcup.data.CuppingMath
import app.cofcup.data.CuppingPlan
import app.cofcup.data.CuppingStep
import app.cofcup.data.CuppingTimeline
import app.cofcup.ui.components.CoffeeListCard
import app.cofcup.ui.components.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(vm: CuppingViewModel) {
    val live by vm.live.collectAsState()
    val elapsedMs by vm.elapsedMs.collectAsState()
    val showLeaveDialog by vm.showLeaveDialog.collectAsState()
    val session = live

    if (session == null) {
        // Should only happen transiently during navigation; recover gracefully.
        LaunchedEffect(Unit) { vm.handleBack() }
        return
    }

    // A cupping timer must stay visible: keep the screen on during the session.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // A soft chime and/or vibration whenever the timer advances to the next step.
    // The ViewModel only emits for timer-driven advances, so "Skip step" stays silent.
    val context = LocalContext.current
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    }
    val chimeId = remember(soundPool) { soundPool.load(context, R.raw.step_ding, 1) }
    DisposableEffect(Unit) {
        onDispose { soundPool.release() }
    }
    val soundOn by vm.soundOn.collectAsState()
    val vibrateOn by vm.vibrateOn.collectAsState()
    val vibrator = remember { context.stepVibrator() }
    LaunchedEffect(soundPool, chimeId) {
        vm.stepChimes.collect {
            if (soundOn) soundPool.play(chimeId, 0.55f, 0.55f, 1, 0, 1f)
            if (vibrateOn) vibrator?.vibrate(CHIME_VIBRATION)
        }
    }

    val running = session.startedAtMs != null
    val finished = session.finished
    val elapsedSec = elapsedMs / 1000
    val current = CuppingTimeline.stepAt(elapsedSec)
    val next = CuppingTimeline.nextStep(current)

    // Auto-mark the session complete (and save it to history) when done is reached.
    LaunchedEffect(current.startSec) {
        if (running && current.isLast) vm.onCuppingComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            finished -> "Session complete"
                            running -> "Cupping in progress"
                            else -> "Cupping"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { vm.handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.openSettings() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            if (running && !finished && !current.isLast) {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = { vm.skipStep() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (current.title.contains("third", ignoreCase = true)) {
                                    "Skip third round"
                                } else {
                                    "Skip step \u2192 ${next?.title ?: "done"}"
                                }
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                finished -> FinishedContent(vm = vm, plan = session.plan)
                !running -> ReadyToStartContent(vm = vm, plan = session.plan, elapsedSec = elapsedSec)
                else -> RunningContent(plan = session.plan, elapsedSec = elapsedSec, current = current)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showLeaveDialog) {
        ConfirmDialog(
            title = "End session?",
            text = "Leave before the cupping is finished? Nothing will be saved.",
            confirmLabel = "End & discard",
            onConfirm = { vm.discardLiveSession() },
            onDismiss = { vm.dismissLeaveDialog() },
        )
    }
}

/** Timer not running yet: instructions, the big Start button, and a step preview. */
@Composable
private fun ReadyToStartContent(vm: CuppingViewModel, plan: CuppingPlan, elapsedSec: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Get everything ready",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "\u2022 Place the ${plan.cupCount} cup${if (plan.cupCount == 1) "" else "s"} in a row, " +
                    "in your tasting order.\n" +
                    "\u2022 Grind the ground coffee into each cup and heat water to ~93 \u00b0C.\n" +
                    "\u2022 Press START as water first hits the grounds \u2014 that moment is 0:00.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = { vm.beginTimer() },
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Start cupping",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    StepsCard(elapsedSec = elapsedSec, currentStep = null)
}

/** Timer running: header timer, steps with timestamps, current instructions. */
@Composable
private fun RunningContent(plan: CuppingPlan, elapsedSec: Long, current: CuppingStep) {
    val next = CuppingTimeline.nextStep(current)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Current step",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    current.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Time lapsed  ${CuppingMath.formatClock(elapsedSec)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                next?.let {
                    Text(
                        "Next: ${it.title} in " +
                            CuppingMath.formatClock(CuppingTimeline.secondsUntil(elapsedSec, it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (next != null) {
                Box(
                    modifier = Modifier.size(84.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = {
                            CuppingTimeline.countdownFraction(elapsedSec, current, next)
                        },
                        strokeWidth = 6.dp,
                        modifier = Modifier.size(84.dp),
                    )
                    Text(
                        CuppingMath.formatClock(CuppingTimeline.secondsUntil(elapsedSec, next)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    StepsCard(elapsedSec = elapsedSec, currentStep = current)

    Spacer(modifier = Modifier.height(16.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "What to do now",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(current.instruction, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The list of steps with timestamps; the current one is highlighted. */
@Composable
private fun StepsCard(elapsedSec: Long, currentStep: CuppingStep?) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            CuppingTimeline.steps.forEachIndexed { index, step ->
                StepRow(
                    step = step,
                    index = index,
                    elapsedSec = elapsedSec,
                    isCurrent = currentStep != null && step.startSec == currentStep.startSec,
                )
                if (index != CuppingTimeline.steps.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: CuppingStep, index: Int, elapsedSec: Long, isCurrent: Boolean) {
    val completed = when {
        step.isLast -> isCurrent
        else -> elapsedSec >= (step.endSec ?: Long.MAX_VALUE)
    }
    Surface(
        color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (isCurrent) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepLeadIcon(completed = completed, isCurrent = isCurrent, index = index + 1)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                CuppingMath.formatClock(step.startSec),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(48.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                step.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (isCurrent && step.isLast) {
                Text(
                    "Now",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** Completed rows get a check; running rows a filled dot; later rows a number badge. */
@Composable
private fun StepLeadIcon(completed: Boolean, isCurrent: Boolean, index: Int) {
    Box(
        modifier = Modifier.size(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            completed -> Icon(
                Icons.Default.Check,
                contentDescription = "Completed step",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            isCurrent -> Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            else -> Text(
                "$index",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Completed rows get a check; running rows a filled dot; later rows a number badge. */
@Composable
private fun FinishedContent(vm: CuppingViewModel, plan: CuppingPlan) {    Text(
        "Cupping complete",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Identify each cup below by its colour and number. The session was saved to your history.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(16.dp))
    CoffeeListCard(coffees = plan.coffees)
    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.goHomeAfterSession() }) {
            Text("New cupping")
        }
        TextButton(onClick = { vm.goHistoryAfterSession() }) {
            Text("History")
        }
    }
}

/** Quick double pulse — a "brrr brrr" — for step advances. */
private val CHIME_VIBRATION: VibrationEffect =
    VibrationEffect.createWaveform(longArrayOf(0, 60, 120, 60), -1)

/** Default Vibrator, working across API levels (26+). */
private fun Context.stepVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
