package app.cupnlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cupnlog.data.SavedSession
import app.cupnlog.data.SessionStatus
import app.cupnlog.ui.components.ConfirmDialog
import app.cupnlog.ui.components.formatDateTime
import app.cupnlog.ui.components.sessionSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: CuppingViewModel) {
    val history by vm.history.collectAsState()
    var pendingDelete by remember { mutableStateOf<SavedSession?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = { vm.handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No sessions yet.\nFinished cupping sessions will appear here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp,
                    vertical = 12.dp,
                ),
            ) {
                items(history.size) { index ->
                    val session = history[index]
                    HistoryItem(
                        session = session,
                        onClick = { vm.openSessionDetail(session.id) },
                        onDelete = { pendingDelete = session },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    pendingDelete?.let { session ->
        val empty = session.plan.coffees.isEmpty()
        ConfirmDialog(
            title = "Delete session?",
            text = if (empty) {
                "Remove the Empty Cupping Session from ${formatDateTime(session.createdAtMs)}?"
            } else {
                "Remove the session from ${formatDateTime(session.createdAtMs)} " +
                    "(${sessionSummary(session.plan.cupCount, session.plan.cupSizeMl)})? " +
                    "Its coffee list will be gone for good."
            },
            confirmLabel = "Delete",
            onConfirm = {
                vm.deleteSession(session.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun HistoryItem(session: SavedSession, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val blindHidden = session.blind && session.revealedAtMs == null
                val hasName = session.plan.sessionName.isNotBlank() && !blindHidden
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Header: the session name when there is one, otherwise the date.
                    // Both use the same header style.
                    Text(
                        if (hasName) session.plan.sessionName else formatDateTime(session.createdAtMs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (session.status == SessionStatus.PLANNED) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Planned",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.small,
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (hasName) {
                    Spacer(modifier = Modifier.height(2.dp))
                    // With a name as the header, the date demotes to a subtitle.
                    Text(
                        formatDateTime(session.createdAtMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    // Quick-start sessions are stored with no cups and no coffees;
                    // blind rows show a neutral label until an explicit reveal.
                    when {
                        session.plan.coffees.isEmpty() -> "Empty Cupping Session"
                        session.status == SessionStatus.PLANNED && blindHidden -> {
                            "Blind setup · ${session.plan.cupCount} cup" +
                                if (session.plan.cupCount == 1) "" else "s"
                        }
                        blindHidden -> {
                            "Blind cupping · ${session.plan.cupCount} cup" +
                                if (session.plan.cupCount == 1) "" else "s"
                        }
                        else -> sessionSummary(session.plan.cupCount, session.plan.cupSizeMl)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.plan.coffees.isNotEmpty() && !blindHidden) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        session.plan.coffees.forEach { coffee ->
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Color(coffee.colorArgb))
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete session",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
