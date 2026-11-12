package app.cupnlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cupnlog.data.CoffeeEntry
import app.cupnlog.data.SavedSession
import app.cupnlog.ui.components.ConfirmDialog
import app.cupnlog.ui.components.SwatchCircle

/**
 * Blind identification: the user lifts each cup, reads the label beneath it,
 * and assigns that label to the position the cup was cupped at. Ratings stay
 * keyed to cup positions; confirming a complete match reorders the saved
 * row's coffees (see `data/CupMatch.kt`) and reveals the names.
 *
 * The coffee name renders beside the label (user decision): the user assigned
 * both, so their pairing is already known — this screen captures the missing
 * label→position mapping, it does not hide the pairing. Pre-identification
 * blind surfaces (cupping, ratings, lists) still render positions only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyCupsScreen(vm: CuppingViewModel, sessionId: String) {
    val history by vm.history.collectAsState()
    val pendingMatch by vm.pendingMatch.collectAsState()
    val session = history.firstOrNull { it.id == sessionId }
    var pickingCup by remember { mutableStateOf<Int?>(null) }
    var confirmMatchDialog by remember { mutableStateOf(false) }

    // The viewed session can disappear when deleted elsewhere.
    if (session == null) {
        LaunchedEffect(Unit) { vm.handleBack() }
        return
    }

    val alreadyRevealed = session.revealedAtMs != null
    val cupCount = session.plan.cupCount
    val assigned = pendingMatch.filterKeys { it in 0 until cupCount }
    val complete = cupCount > 0 &&
        assigned.size == cupCount &&
        assigned.values.distinct().size == cupCount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identify the cups") },
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
            Text(
                "Look under each cup and read its label, then assign it to the " +
                    "position that cup was in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Text(
                "${assigned.size} of $cupCount identified",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            repeat(cupCount) { position ->
                CupAssignRow(
                    position = position,
                    coffees = session.plan.coffees,
                    assignedCoffee = assigned[position]?.let { session.plan.coffees.getOrNull(it) },
                    onClick = { pickingCup = position },
                    onClear = { vm.clearCupAssignment(position) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { confirmMatchDialog = true },
                enabled = complete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    if (alreadyRevealed) "Save matching" else "Confirm & reveal",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    pickingCup?.let { cup ->
        CoffeePickerDialog(
            session = session,
            cup = cup,
            pendingMatch = pendingMatch,
            onPick = { coffeeIndex ->
                vm.assignCupToCoffee(cup, coffeeIndex)
                pickingCup = null
            },
            onDismiss = { pickingCup = null },
        )
    }

    if (confirmMatchDialog) {
        ConfirmDialog(
            title = if (alreadyRevealed) "Update the identification?" else "Reveal the coffee names?",
            text = if (alreadyRevealed) {
                "The coffees are re-assigned to the cups. Ratings stay with their " +
                    "cup positions."
            } else {
                "Each name is attached to the cup it was in, and the names stay " +
                    "visible from now on."
            },
            confirmLabel = if (alreadyRevealed) "Save" else "Reveal",
            onConfirm = {
                confirmMatchDialog = false
                vm.confirmMatch(sessionId)
            },
            onDismiss = { confirmMatchDialog = false },
        )
    }
}

/** One cup position: "Cup N" with its assigned swatch + label, or an empty slot. */
@Composable
private fun CupAssignRow(
    position: Int,
    coffees: List<CoffeeEntry>,
    assignedCoffee: CoffeeEntry?,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
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
            Text(
                "Cup ${position + 1}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (assignedCoffee != null) {
                SwatchCircle(
                    colorArgb = assignedCoffee.colorArgb,
                    insideText = assignedCoffee.displayLabel,
                    size = 40.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    assignedCoffee.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    "Not identified",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClear, enabled = assignedCoffee != null) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Picks the coffee for one cup position: swatch (label inside) with the
 * coffee's name beside it. Coffees already assigned to another position are
 * dimmed and disabled — a label can sit under only one cup.
 */
@Composable
private fun CoffeePickerDialog(
    session: SavedSession,
    cup: Int,
    pendingMatch: Map<Int, Int>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which coffee was cup ${cup + 1}?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                session.plan.coffees.forEachIndexed { index, coffee ->
                    val usedElsewhere = pendingMatch.any { (pos, c) -> pos != cup && c == index }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (usedElsewhere) 0.4f else 1f)
                            .clickable(enabled = !usedElsewhere) { onPick(index) }
                            .padding(vertical = 8.dp),
                    ) {
                        SwatchCircle(
                            colorArgb = coffee.colorArgb,
                            insideText = coffee.displayLabel,
                            size = 36.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            if (usedElsewhere) {
                                "${coffee.displayName} (already assigned)"
                            } else {
                                coffee.displayName
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
