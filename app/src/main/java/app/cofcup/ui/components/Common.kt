package app.cofcup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cofcup.data.CoffeeEntry
import app.cofcup.data.CuppingMath
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

/** The coffee identification list: colour + number + name per cup. */
@Composable
fun CoffeeListCard(coffees: List<CoffeeEntry>, title: String = "Coffee identification") {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SwatchCircle(
                        colorArgb = coffee.colorArgb,
                        insideText = coffee.displayNumber,
                        size = 40.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            coffee.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Position ${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (index != coffees.size - 1) {
                    Spacer(modifier = Modifier.height(10.dp))
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
