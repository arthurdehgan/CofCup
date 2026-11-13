package app.cupnlog.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
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
 * Two entry modes, toggled with filter chips and defaulting to "By cup" at
 * the start of each session:
 *  - By cup: tap a cup, score all its categories in one editor.
 *  - By category: tap a category, see one slider per cup for comparing that
 *    attribute across cups (notes via the per-cup icon).
 * The chosen mode lives in the view model, so leaving this screen and coming
 * back keeps it. Both modes write the same per-cup `CupRating`s through the
 * view model, so switching modes never loses or duplicates an entry.
 *
 * Blind sessions render positions only — "Cup N" — with no names, colours or
 * labels anywhere (see `Blind.kt`); the session shell keeps chimes, the
 * screen-on flag and the Done save working while this screen is on top.
 */

/** Cup names at most this long stay on one line in the "By category" rows;
 *  longer names switch to the stacked name-above-slider layout. */
private const val COMPACT_NAME_MAX_CHARS = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingsScreen(vm: CuppingViewModel) {
    val live by vm.live.collectAsState()
    val elapsedMs by vm.elapsedMs.collectAsState()
    val ratings by vm.myRatings.collectAsState()
    val ratingMode by vm.ratingMode.collectAsState()
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
    // The entry mode itself lives in the view model (session-scoped). The
    // expanded category is screen-local: returning re-collapses the list.
    var expandedField by rememberSaveable { mutableStateOf<String?>(null) }
    var notesCup by remember { mutableStateOf<Int?>(null) }

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
        bottomBar = {
            // "Save ratings" behaves exactly like the back arrow: it leaves
            // the screen, and after Done the edits are persisted on the way
            // out. Pinned at the bottom so it stays reachable above the cup
            // rows on tall lists.
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = { vm.handleBack() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save ratings")
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
            // Cupping complete: no "Current step" and no clock any more —
            // the timer stopped when Done was reached.
            if (!current.isLast) {
                CompactTimerCard(elapsedSec = elapsedSec, current = current)
                Spacer(modifier = Modifier.height(8.dp))
            }
            // Rating entry mode. The choice is session-scoped VM state, so
            // it survives leaving and re-entering this screen; both modes
            // edit the same view-model ratings. Each chip takes half the
            // row width.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ratingMode == RatingMode.BY_CUP,
                    onClick = { vm.setRatingMode(RatingMode.BY_CUP) },
                    label = { Text(RatingMode.BY_CUP.label) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = ratingMode == RatingMode.BY_CATEGORY,
                    onClick = {
                        vm.setRatingMode(RatingMode.BY_CATEGORY)
                        expandedField = null
                    },
                    label = { Text(RatingMode.BY_CATEGORY.label) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (ratingMode == RatingMode.BY_CUP) "Tap a cup to score it."
                else "Tap a category to compare all cups.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (ratingMode == RatingMode.BY_CUP) {
                repeat(plan.cupCount) { cup ->
                    CupRow(
                        cup = cup,
                        plan = plan,
                        rating = ratings[cup],
                        onClick = { editingCup = cup },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                RatingField.entries.forEach { field ->
                    CategoryRow(
                        plan = plan,
                        field = field,
                        ratings = ratings,
                        expanded = expandedField == field.name,
                        onToggle = {
                            expandedField =
                                if (expandedField == field.name) null else field.name
                        },
                        onScore = { cup, value -> vm.setCupRatingScore(cup, field, value) },
                        onNotes = { notesCup = it },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
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

    notesCup?.let { cup ->
        NotesDialog(
            plan = plan,
            cup = cup,
            rating = ratings[cup],
            onNotes = { vm.setCupRatingNotes(cup, it) },
            onDismiss = { notesCup = null },
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
 *  label-less swatch; content descriptions carry no identity either. */
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
                    insideText = coffee?.displayLabel,
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
        // The window itself must not react to the keyboard: the system's
        // default pan/resize re-pans on every keystroke and visibly jolts
        // the slider list. With decorFitsSystemWindows off, the layout pads
        // above the IME once (imePadding below) and stays put while typing.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Scaffold(
            modifier = Modifier.imePadding(),
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
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * One category in "By category" mode. Collapsed, it shows the category name
 * and how many cups already have a score for it; expanding reveals one
 * slider per cup so a single attribute can be compared across cups. Only one
 * category is expanded at a time (accordion, tracked by the caller).
 */
@Composable
private fun CategoryRow(
    plan: CuppingPlan,
    field: RatingField,
    ratings: Map<Int, CupRating>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onScore: (cup: Int, value: Int) -> Unit,
    onNotes: (cup: Int) -> Unit,
) {
    val ratedCount = (0 until plan.cupCount).count { ratings[it]?.scoreFor(field) != null }
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    field.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (ratedCount == 0) {
                    Text(
                        "Not rated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            "$ratedCount/${plan.cupCount}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.Default.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                ) {
                    repeat(plan.cupCount) { cup ->
                        val coffee = plan.coffees.getOrNull(cup)
                        val name = if (plan.blind) "Cup ${cup + 1}"
                        else coffee?.displayName ?: "Cup ${cup + 1}"
                        CategoryCupSlider(
                            name = name,
                            value = ratings[cup]?.scoreFor(field),
                            onValueChange = { onScore(cup, it) },
                            onNotes = { onNotes(cup) },
                            swatch = {
                                // Blind rows keep the neutral, label-less swatch.
                                if (plan.blind) {
                                    SwatchCircle(CoffeeEntry.FALLBACK_COLOR, size = 32.dp)
                                } else {
                                    SwatchCircle(
                                        colorArgb = coffee?.colorArgb
                                            ?: CoffeeEntry.FALLBACK_COLOR,
                                        insideText = coffee?.displayLabel,
                                        size = 32.dp,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Notes for one cup, opened from the notes icon in "By category" mode (the
 * per-cup editor owns notes in "By cup" mode). Edits apply to the view model
 * immediately, like everywhere else on this screen.
 */
@Composable
private fun NotesDialog(
    plan: CuppingPlan,
    cup: Int,
    rating: CupRating?,
    onNotes: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val coffee = plan.coffees.getOrNull(cup)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                Text(
                    if (plan.blind) "Notes, cup ${cup + 1}"
                    else "Notes, ${coffee?.displayName ?: "cup ${cup + 1}"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = rating?.notes ?: "",
                    onValueChange = onNotes,
                    placeholder = { Text("Aroma, impressions\u2026") },
                    supportingText = { Text("${(rating?.notes ?: "").length}/500") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

/**
 * A 1–10 integer slider, optionally with leading (e.g. a cup swatch) and
 * trailing (e.g. the notes icon) slots for the "By category" per-cup rows.
 * The value text stays "—" until a score is set.
 */
@Composable
private fun RatingSlider(
    label: String,
    value: Int?,
    onValueChange: (Int) -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(8.dp))
        }
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
        ScoreSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(2f),
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.width(4.dp))
            trailing()
        }
    }
}

/** The shared 1–10 slider. Sits at 5 when no score exists yet; only an
 *  actual change records one. */
@Composable
private fun ScoreSlider(
    value: Int?,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = (value ?: 5).toFloat(),
        onValueChange = { onValueChange(it.roundToInt()) },
        valueRange = 1f..10f,
        steps = 8,
        modifier = modifier,
    )
}

/**
 * One cup's slider inside an expanded category, laid out to fit the name:
 * short names (blind "Cup N", short labels) stay on one compact line as
 * before; long names get a line of their own above the slider so they never
 * wrap and the slider keeps its full width.
 */
@Composable
private fun CategoryCupSlider(
    name: String,
    value: Int?,
    onValueChange: (Int) -> Unit,
    onNotes: () -> Unit,
    swatch: @Composable () -> Unit,
) {
    if (name.length <= COMPACT_NAME_MAX_CHARS) {
        RatingSlider(
            label = name,
            value = value,
            onValueChange = onValueChange,
            leading = swatch,
            trailing = {
                IconButton(onClick = onNotes) {
                    Icon(Icons.Default.Edit, contentDescription = "Notes, $name")
                }
            },
        )
    } else {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                swatch()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNotes) {
                    Icon(Icons.Default.Edit, contentDescription = "Notes, $name")
                }
            }
            // The slider indents to line up under the name, past the swatch.
            Row(
                modifier = Modifier.padding(start = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value?.toString() ?: "\u2014",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(40.dp),
                )
                ScoreSlider(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
