package app.cupnlog.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cupnlog.data.CoffeeEntry
import app.cupnlog.data.CupRating
import app.cupnlog.data.CuppingMath
import app.cupnlog.data.RatingField
import app.cupnlog.data.isRated
import app.cupnlog.data.scoreFor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A row of "label: value" used in summary cards. */
@Composable
fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

/** " -  +" stepper row with a label on the left and the value in the middle. */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    helperText: String? = null,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
    decreaseContentDescription: String = "Decrease",
    increaseContentDescription: String = "Increase",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (helperText != null) {
                Text(
                    helperText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedIconButton(
            onClick = onDecrement,
            enabled = decrementEnabled,
        ) {
            Icon(Icons.Default.Remove, contentDescription = decreaseContentDescription)
        }
        Text(
            valueText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(84.dp),
        )
        OutlinedIconButton(
            onClick = onIncrement,
            enabled = incrementEnabled,
        ) {
            Icon(Icons.Default.Add, contentDescription = increaseContentDescription)
        }
    }
}

/** A round colour swatch, optionally showing a number inside, optionally clickable. */
@Composable
fun SwatchCircle(
    colorArgb: Long,
    modifier: Modifier = Modifier,
    insideText: String? = null,
    selected: Boolean = false,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
) {
    val color = Color(colorArgb)
    val textColor = if (color.luminance() > 0.55f) Color(0xFF231917) else Color.White
    var circleModifier = modifier
        .size(size)
        .clip(CircleShape)
        .background(color)
    if (selected) {
        circleModifier = circleModifier.border(
            width = 3.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
        )
    }
    Box(
        modifier = if (onClick != null) circleModifier.clickable(onClick = onClick) else circleModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (insideText != null) {
            Text(
                text = insideText,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The coffee identification card: one interactive line per cup — colour
 * (label inside), name and table position on the left, this rater's overall
 * score on the right. Tapping a scored line expands the six attribute squares
 * and notes below it; tapping again retracts (expansion is independent per
 * line). Unscored cups show a quiet dash and don't expand. [ratingsByCup]
 * maps cup position → this device's rating for that cup.
 */
@Composable
fun CoffeeListCard(
    coffees: List<CoffeeEntry>,
    ratingsByCup: Map<Int, CupRating> = emptyMap(),
    title: String = "Coffee identification",
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            coffees.forEachIndexed { index, coffee ->
                CoffeeLine(position = index, coffee = coffee, rating = ratingsByCup[index])
                if (index != coffees.size - 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

/** One identification line: identity on the left, overall on the right, and
 *  — when the cup was scored — the expandable detail (attribute squares +
 *  notes) below. */
@Composable
private fun CoffeeLine(position: Int, coffee: CoffeeEntry, rating: CupRating?) {
    val rated = rating?.isRated == true
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .then(if (rated) Modifier.clickable { expanded = !expanded } else Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SwatchCircle(
                colorArgb = coffee.colorArgb,
                insideText = coffee.displayLabel,
                size = 40.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    coffee.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Position ${position + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Overall ${rating?.overall ?: "\u2014"}",
                style = if (rated) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (rated) FontWeight.Bold else FontWeight.Normal,
                color = if (rated) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (expanded && rated && rating != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RatingField.entries
                    .filter { it != RatingField.OVERALL }
                    .forEach { field ->
                        ScoreSquare(
                            field = field,
                            score = rating.scoreFor(field),
                            modifier = Modifier.weight(1f),
                        )
                    }
            }
            if (rating.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(rating.notes, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** One attribute square: the score (or a quiet dash when unrated) with its
 *  category label underneath, sized so six of them divide the row width. */
@Composable
private fun ScoreSquare(field: RatingField, score: Int?, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.aspectRatio(1f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                score?.toString() ?: "\u2014",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                field.label,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Blind, un-identified coffee list: positions only ("Cup 1…N") — no names,
 *  colours or labels anywhere, including content descriptions. */
@Composable
fun BlindCoffeeListCard(cupCount: Int, title: String = "Coffee identification") {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            repeat(cupCount.coerceAtLeast(0)) { index ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SwatchCircle(
                        colorArgb = CoffeeEntry.FALLBACK_COLOR,
                        size = 40.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Cup ${index + 1}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/** A reusable confirmation dialog (used for discarding and deleting). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Formats a session date, e.g. "Sat 6 Nov 2026 · 14:32". */
fun formatDateTime(epochMs: Long): String {
    val dateTime = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm", Locale.getDefault())
    return dateTime.format(formatter)
}

/** Compact "N cups · 150 ml · 8.3 g per cup" summary for history rows. */
fun sessionSummary(cupCount: Int, cupSizeMl: Int): String =
    "$cupCount cup${if (cupCount == 1) "" else "s"} · $cupSizeMl ml · " +
        CuppingMath.formatGrams(CuppingMath.coffeePerCupG(cupSizeMl)) + " per cup"

/** Saved-setup displays show at most [max] characters of a session name; a cut
 *  name ends with an ellipsis so the total stays within [max] characters. */
fun displaySessionName(name: String, max: Int = 20): String =
    if (name.length <= max) name else name.take(max - 1) + "\u2026"
