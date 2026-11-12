package app.cupnlog.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
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
import app.cupnlog.data.CupRating
import app.cupnlog.data.CuppingPlan
import app.cupnlog.data.RatingField
import app.cupnlog.data.SavedSession
import app.cupnlog.data.SessionOrigin
import app.cupnlog.data.SessionStatus
import app.cupnlog.data.isRated
import app.cupnlog.data.scoreFor
import app.cupnlog.ui.components.BlindCoffeeListCard
import app.cupnlog.ui.components.CoffeeListCard
import app.cupnlog.ui.components.ConfirmDialog
import app.cupnlog.ui.components.formatDateTime

/** Read-only view of a saved session's coffee list, reachable from the history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(vm: CuppingViewModel, sessionId: String) {
    val history by vm.history.collectAsState()
    val myId by vm.myId.collectAsState()
    val session = history.firstOrNull { it.id == sessionId }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReveal by remember { mutableStateOf(false) }

    // The viewed session can disappear when deleted elsewhere.
    if (session == null) {
        LaunchedEffect(Unit) { vm.handleBack() }
        return
    }

    val planned = session.status == SessionStatus.PLANNED
    // Same header rule as the History rows: the session name leads when there
    // is one; unrevealed blind rows keep their neutral labelling.
    val nameHeader = session.plan.sessionName.isNotBlank() &&
        !(session.blind && session.revealedAtMs == null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (planned) "Saved setup" else "Past session") },
                navigationIcon = {
                    IconButton(onClick = { vm.handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete this session",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                if (nameHeader) session.plan.sessionName else formatDateTime(session.createdAtMs),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (nameHeader) {
                Text(
                    formatDateTime(session.createdAtMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (session.plan.coffees.isEmpty()) {
                // Quick-start ("Empty Cupping Session"): no cups were labelled.
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Empty Cupping Session — this session was started straight from " +
                            "the timer, so there is no coffee list.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else if (session.blind && session.revealedAtMs == null) {
                // Blind, not yet revealed: positions only — never name/colour/number.
                BlindCoffeeListCard(cupCount = session.plan.cupCount)
            } else {
                CoffeeListCard(coffees = session.plan.coffees)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (session.blind && session.revealedAtMs == null &&
                session.origin == SessionOrigin.OWN
            ) {
                // Explicit reveal (never automatic, not reversible): the detail
                // view offers it for any not-yet-revealed own blind session.
                Button(
                    onClick = { confirmReveal = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("Reveal identities", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (!planned && session.plan.cupCount > 0) {
                YourRatingsCard(session = session, myId = myId)
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (planned) {
                Button(
                    onClick = { vm.startPlannedRun(session.id, backTo = Screen.History) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run this setup", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete this session")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete session?",
            text = "Its coffee list will be gone for good.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteSession(session.id) },
            onDismiss = { confirmDelete = false },
        )
    }

    if (confirmReveal) {
        ConfirmDialog(
            title = "Reveal identities?",
            text = "Cup names, numbers and colours become visible. This cannot be undone.",
            confirmLabel = "Reveal",
            onConfirm = { vm.revealBlind(session.id); confirmReveal = false },
            onDismiss = { confirmReveal = false },
        )
    }
}

/**
 * Phase 2's minimal own-ratings view: this device's ratings for the session,
 * one row per cup (Overall + attribute line + notes), with a quiet
 * "No rating" slot so the row shape stays stable (phase 4 grows this into the
 * multi-rater comparison layout). Blind, un-revealed sessions label rows
 * "Cup N" — no name, colour or number.
 */
@Composable
private fun YourRatingsCard(session: SavedSession, myId: String) {
    val blind = session.blind && session.revealedAtMs == null
    val plan = session.plan
    val mine = session.ratings
        .filter { it.raterId == myId }
        .associateBy { it.cup }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Your ratings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            repeat(plan.cupCount) { cup ->
                YourRatingRow(cup = cup, plan = plan, blind = blind, rating = mine[cup])
                if (cup != plan.cupCount - 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun YourRatingRow(cup: Int, plan: CuppingPlan, blind: Boolean, rating: CupRating?) {
    val coffee = plan.coffees.getOrNull(cup)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (blind) "Cup ${cup + 1}" else coffee?.displayName ?: "Cup ${cup + 1}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (rating?.isRated == true) {
                Text(
                    "Overall ${rating.overall ?: "\u2014"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    "No rating",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (rating?.isRated == true) {
            val attributes = RatingField.entries
                .filter { it != RatingField.OVERALL }
                .mapNotNull { field -> rating.scoreFor(field)?.let { "${field.label} $it" } }
            if (attributes.isNotEmpty()) {
                Text(
                    attributes.joinToString(" \u00b7 "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (rating.notes.isNotBlank()) {
                Text(
                    rating.notes,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
