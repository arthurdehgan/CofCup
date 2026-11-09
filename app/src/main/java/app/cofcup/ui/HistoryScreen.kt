package app.cofcup.ui

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
import app.cofcup.data.SavedSession
import app.cofcup.ui.components.ConfirmDialog
import app.cofcup.ui.components.formatDateTime
import app.cofcup.ui.components.sessionSummary

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
                Text(
                    formatDateTime(session.createdAtMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    // Quick-start sessions are stored with no cups and no coffees.
                    if (session.plan.coffees.isEmpty()) {
                        "Empty Cupping Session"
                    } else {
                        sessionSummary(session.plan.cupCount, session.plan.cupSizeMl)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.plan.coffees.isNotEmpty()) {
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
