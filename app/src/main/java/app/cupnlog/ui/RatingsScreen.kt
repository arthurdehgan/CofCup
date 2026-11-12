package app.cupnlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.cupnlog.data.CoffeeEntry
import app.cupnlog.data.CuppingPlan
import app.cupnlog.data.CuppingStep
import app.cupnlog.data.CupRating
import app.cupnlog.data.CuppingMath
import app.cupnlog.data.CuppingTimeline
import app.cupnlog.data.RatingField
import app.cupnlog.data.isRated
import app.cupnlog.data.scoreFor
import app.cupnlog.ui.components.SwatchCircle
import kotlin.math.roundToInt

/**
 * Phase 2: per-cup ratings, reachable from the live session's "Rate" chip.
 * Rating is optional and never gates Done; the edits live in the view model
 * and are persisted at Done (or on leaving this screen after Done).
 *
 * Blind sessions render positions only — "Cup N" — with no names, colours or
 * numbers anywhere (see `Blind.kt`); the session shell keeps chimes, the
 * screen-on flag and the Done save working while this screen is on top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingsScreen(vm: CuppingViewModel) {
    val live by vm.live.collectAsState()
    val elapsedMs by vm.elapsedMs.collectAsState()
    val ratings by vm.myRatings.collectAsState()
    val session = live

    // Reachable only while a session with cups exists; recover otherwise.
    if (session == null || session.plan.cupCount == 0) {
        LaunchedEffect(Unit) { vm.handleBack() }
        return
    }

    val plan = session.plan
    val elapsedSec = elapsedMs / 1000
    val current = CuppingTimeline.stepAt(elapsedSec)
    var editingCup by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rate the cups") },
                navigationIcon = {
                    IconButton(onClick = { vm.handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            CompactTimerCard(elapsedSec = elapsedSec, current = current)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Tap a cup to score it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            repeat(plan.cupCount) { cup ->
                CupRow(
                    cup = cup,
                    plan = plan,
                    rating = ratings[cup],
                    onClick = { editingCup = cup },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    editingCup?.let { cup ->
        CupRatingEditor(
            vm = vm,
            plan = plan,
            cup = cup,
            rating = ratings[cup],
            onDismiss = { editingCup = null },
        )
    }
}

/** The compact timer from deepseek §7.10: the person rating sees where the
 *  session is without leaving this screen. */
@Composable
private fun CompactTimerCard(elapsedSec: Long, current: CuppingStep) {
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
            Text(
                CuppingMath.formatClock(elapsedSec),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** One cup in the timeline order. Blind rows show "Cup N" with a neutral,
 *  numberless swatch; content descriptions carry no identity either. */
@Composable
private fun CupRow(cup: Int, plan: CuppingPlan, rating: CupRating?, onClick: () -> Unit) {
    val coffee = plan.coffees.getOrNull(cup)
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (plan.blind) {
                SwatchCircle(colorArgb = CoffeeEntry.FALLBACK_COLOR, size = 40.dp)
            } else {
                SwatchCircle(
                    colorArgb = coffee?.colorArgb ?: CoffeeEntry.FALLBACK_COLOR,
                    insideText = coffee?.displayNumber,
                    size = 40.dp,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (plan.blind) "Cup ${cup + 1}" else coffee?.displayName ?: "Cup ${cup + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!plan.blind) {
                    Text(
                        "Position ${cup + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rating?.isRated == true) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        "Overall ${rating.overall ?: "\u2014"}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            } else {
                Text(
                    "Not rated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The full-screen per-cup editor: six informational 1–10 sliders, the strictly
 * manual Overall (1–10), and a notes field capped at 500 characters. An
 * unrated slider shows "—" and sits at 5; only an actual change records a
 * score, so opening and closing the editor never creates a rating.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CupRatingEditor(
    vm: CuppingViewModel,
    plan: CuppingPlan,
    cup: Int,
    rating: CupRating?,
    onDismiss: () -> Unit,
) {
    val coffee = plan.coffees.getOrNull(cup)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (plan.blind) "Cup ${cup + 1}"
                            else coffee?.displayName ?: "Cup ${cup + 1}"
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Attributes",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(
                    RatingField.AROMA,
                    RatingField.FLAVOR,
                    RatingField.ACIDITY,
                    RatingField.SWEETNESS,
                    RatingField.MOUTHFEEL,
                    RatingField.AFTERTASTE,
                ).forEach { field ->
                    RatingSlider(
                        label = field.label,
                        value = rating?.scoreFor(field),
                        onValueChange = { vm.setCupRatingScore(cup, field, it) },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Overall",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RatingSlider(
                    label = "Overall",
                    value = rating?.scoreFor(RatingField.OVERALL),
                    onValueChange = { vm.setCupRatingScore(cup, RatingField.OVERALL, it) },
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = rating?.notes ?: "",
                    onValueChange = { vm.setCupRatingNotes(cup, it) },
                    label = { Text("Notes") },
                    placeholder = { Text("Aroma, impressions\u2026") },
                    supportingText = { Text("${(rating?.notes ?: "").length}/500") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.clearCupRating(cup) }) {
                        Text("Clear rating")
                    }
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
                Spacer(modifier = Modifier.navigationBarsPadding())
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/** A 1–10 integer slider. The value text stays "—" until a score is set. */
@Composable
private fun RatingSlider(label: String, value: Int?, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            value?.toString() ?: "\u2014",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(40.dp),
        )
        Slider(
            value = (value ?: 5).toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.weight(2f),
        )
    }
}
