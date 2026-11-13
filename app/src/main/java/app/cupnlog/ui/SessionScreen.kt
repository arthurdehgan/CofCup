package app.cupnlog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cupnlog.data.CuppingMath
import app.cupnlog.data.CoffeeEntry
import app.cupnlog.data.CupRating
import app.cupnlog.data.CuppingPlan
import app.cupnlog.data.CuppingStep
import app.cupnlog.data.CuppingTimeline
import app.cupnlog.data.SavedSession
import app.cupnlog.data.SessionStatus
import app.cupnlog.data.blindDoneInstruction
import app.cupnlog.data.isRated
import app.cupnlog.ui.components.BlindCoffeeListCard
import app.cupnlog.ui.components.CoffeeListCard
import app.cupnlog.ui.components.ConfirmDialog
import app.cupnlog.ui.components.displaySessionName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(vm: CuppingViewModel) {
    val live by vm.live.collectAsState()
    val elapsedMs by vm.elapsedMs.collectAsState()
    val showLeaveDialog by vm.showLeaveDialog.collectAsState()
    val history by vm.history.collectAsState()
    val myId by vm.myId.collectAsState()
    val myRatings by vm.myRatings.collectAsState()
    val plannedSetups = history.filter { it.status == SessionStatus.PLANNED }
    val session = live

    if (session == null) {
        // Should only happen transiently during navigation; recover gracefully.
        LaunchedEffect(Unit) { vm.handleBack() }
        return
    }

    val running = session.startedAtMs != null
    val finished = session.finished
    val elapsedSec = elapsedMs / 1000
    val current = CuppingTimeline.stepAt(elapsedSec)
    val next = CuppingTimeline.nextStep(current)

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
                    // "Rate" only exists once the timer is running: the pre-start
                    // "Cupping" screen (with a setup picked) has nothing to rate yet.
                    if (running && session.plan.cupCount > 0) {
                        AssistChip(
                            onClick = { vm.openRatings() },
                            label = { Text("Rate") },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
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
                finished -> {
                    // The Finished screen reads the reveal state from the saved
                    // history row (see onCuppingComplete's savedSessionId).
                    val savedRow = history.firstOrNull { it.id == session.savedSessionId }
                    // This device's ratings keyed by cup position, shown inline
                    // on the identification card; the VM-held ratings cover the
                    // brief window before the Done save has landed.
                    val ratingsByCup = savedRow?.ratings
                        ?.filter { it.raterId == myId }
                        ?.associateBy { it.cup }
                        ?: myRatings
                    FinishedContent(
                        vm = vm,
                        plan = session.plan,
                        sessionId = session.savedSessionId ?: session.plannedSessionId,
                        blindRevealed = savedRow?.revealedAtMs != null,
                        // After a confirmed identification the saved row's
                        // coffees are the ones reordered into cup positions —
                        // the live plan keeps the setup order.
                        revealedCoffees = savedRow?.plan?.coffees,
                        ratingsByCup = ratingsByCup,
                    )
                }
                !running -> ReadyToStartContent(
                    vm = vm,
                    plan = session.plan,
                    elapsedSec = elapsedSec,
                    plannedSetups = plannedSetups,
                    selectedSetupId = session.plannedSessionId,
                    // The picker is a quick-start convenience: a run started
                    // through the setup flow already IS a setup, and a
                    // planned run already has its row.
                    showSetupPicker = session.fromQuickStart,
                )
                else -> RunningContent(plan = session.plan, elapsedSec = elapsedSec, current = current)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showLeaveDialog) {
        // Ratings captured so far are the only thing lost with an unfinished run.
        val hasRatings = myRatings.values.any { it.isRated }
        ConfirmDialog(
            title = "End session?",
            text = if (hasRatings) {
                "Leave before the cupping is finished? Your ratings for this unfinished " +
                    "session will be lost."
            } else {
                "Leave before the cupping is finished? Nothing will be saved."
            },
            confirmLabel = "End & discard",
            onConfirm = { vm.discardLiveSession() },
            onDismiss = { vm.dismissLeaveDialog() },
        )
    }
}

/** Timer not running yet: instructions, the big Start button, and a step
 *  preview. The saved-setup picker ([showSetupPicker]) appears only on a
 *  quick-started run (straight from the timer with no cups) — it covers
 *  picking a setup plus swapping/cancelling a pick before the timer starts. */
@Composable
private fun ReadyToStartContent(
    vm: CuppingViewModel,
    plan: CuppingPlan,
    elapsedSec: Long,
    plannedSetups: List<SavedSession>,
    selectedSetupId: String?,
    showSetupPicker: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Get everything ready",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "\u2022 Heat water while you set up the cups and grind the coffee.\n" +
                "\u2022 Place your cups in a row.\n" +
                "\u2022 Press START as water first hits the grounds of the first coffee.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    if (showSetupPicker && plannedSetups.isNotEmpty()) {
        Text(
            "Saved setups",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(plannedSetups.size) { index ->
                SavedSetupPickerChip(
                    session = plannedSetups[index],
                    selected = plannedSetups[index].id == selectedSetupId,
                    onSelect = {
                        val id = plannedSetups[index].id
                        if (id == selectedSetupId) {
                            vm.clearSelectedSetupForLive()
                        } else {
                            vm.selectPlannedSetupForLive(id)
                        }
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
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

/** One saved setup on the pre-start "Cupping" screen: tap loads it into the
 *  session that is about to start (its planned row converts to done on Done).
 *  Blind, un-revealed rows show a crossed-eye icon after the subtitle — names,
 *  labels and colours are never rendered here. */
@Composable
private fun SavedSetupPickerChip(
    session: SavedSession,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val blind = session.blind && session.revealedAtMs == null
    val label = if (session.plan.sessionName.isNotBlank()) {
        // The session name never leaks cup identity, so blind setups show it too.
        displaySessionName(session.plan.sessionName)
    } else {
        "Unnamed setup"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier.clickable(onClick = onSelect),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${session.plan.cupSizeMl} ml per cup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (blind) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.VisibilityOff,
                        contentDescription = "Blind setup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Timer running: the current step's title with the elapsed timer inside a
 *  progress ring (the ring itself fills across the current step), then the
 *  step timeline — where the current row shows the live clock. */
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
            Text(
                current.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (next != null) {
                Box(
                    modifier = Modifier.size(92.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = {
                            CuppingTimeline.countdownFraction(elapsedSec, current, next)
                        },
                        strokeWidth = 6.dp,
                        modifier = Modifier.size(92.dp),
                    )
                    Text(
                        CuppingMath.formatClock(CuppingTimeline.secondsUntil(elapsedSec, next)),
                        style = MaterialTheme.typography.headlineSmall,
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
            // Blind sessions must not leak identities through the Done step's
            // instruction (the colour/number wording) — swap in the neutral
            // variant without moving any step window out of CuppingTimeline.
            val instruction = if (plan.blind && current.isLast) {
                blindDoneInstruction(plan.cupCount)
            } else {
                current.instruction
            }
            Text(instruction, style = MaterialTheme.typography.bodyMedium)
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
            // The current step shows the live elapsed clock; other rows keep
            // their fixed start timestamps.
            Text(
                CuppingMath.formatClock(if (isCurrent) elapsedSec else step.startSec),
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

/** Done screen: coffee identification, unless this was an empty quick-start session.
 *  Blind, un-identified sessions show positions only and offer the "Identify
 *  the cups" flow (label under each cup → position). Revealed sessions list
 *  the coffees in cup positions — [revealedCoffees] from the saved row, not
 *  the live plan — with this device's ratings inline ([ratingsByCup]). */
@Composable
private fun FinishedContent(
    vm: CuppingViewModel,
    plan: CuppingPlan,
    sessionId: String?,
    blindRevealed: Boolean,
    revealedCoffees: List<CoffeeEntry>? = null,
    ratingsByCup: Map<Int, CupRating> = emptyMap(),
) {
    if (plan.coffees.isEmpty()) {
        Text(
            "Empty Cupping Session",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "This session was started without a setup, no cups were labelled. " +
                "It was saved to your history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            "Cupping complete",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        when {
            plan.blind && !blindRevealed -> {
                Text(
                    "Coffee names stay hidden until you identify the cups. The session " +
                        "was saved to your history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                BlindCoffeeListCard(cupCount = plan.cupCount)
            }
            else -> {
                Text(
                    if (plan.blind) {
                        "Coffee names are revealed. The session was saved to your history."
                    } else {
                        "Identify each cup below by its colour and number. " +
                            "The session was saved to your history."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                CoffeeListCard(
                    coffees = revealedCoffees ?: plan.coffees,
                    ratingsByCup = ratingsByCup,
                )
            }
        }
    }
    // Bottom actions as one full-width stack: the primary next action filled,
    // the escape hatches outlined below it. Identical rhythm in every variant
    // (blind, revealed, empty session) — only which action is primary changes.
    Spacer(modifier = Modifier.height(24.dp))
    if (plan.blind && !blindRevealed && sessionId != null) {
        Button(
            onClick = { vm.openIdentifyCups(sessionId, Screen.LiveSession) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Identify the cups")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
    Button(
        onClick = { vm.goHomeAfterSession() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("New cupping")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = { vm.goHistoryAfterSession() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("History")
    }
}
