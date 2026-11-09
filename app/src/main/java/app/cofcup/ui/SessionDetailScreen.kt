package app.cofcup.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cofcup.data.CuppingMath
import app.cofcup.data.SavedSession
import app.cofcup.ui.components.CoffeeListCard
import app.cofcup.ui.components.ConfirmDialog
import app.cofcup.ui.components.StatLine
import app.cofcup.ui.components.formatDateTime

/** Read-only view of a saved session's coffee list, reachable from the history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(vm: CuppingViewModel, sessionId: String) {
    val history by vm.history.collectAsState()
    val session = history.firstOrNull { it.id == sessionId }
    var confirmDelete by remember { mutableStateOf(false) }

    // The viewed session can disappear when deleted elsewhere.
    if (session == null) {
        LaunchedEffect(Unit) { vm.handleBack() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Past session") },
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
                formatDateTime(session.createdAtMs),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SessionSummaryCard(session)
            Spacer(modifier = Modifier.height(16.dp))
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
            } else {
                CoffeeListCard(coffees = session.plan.coffees)
            }
            Spacer(modifier = Modifier.height(20.dp))
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
}

@Composable
private fun SessionSummaryCard(session: SavedSession) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val plan = session.plan
            StatLine(label = "Cups", value = "${plan.cupCount} × ${plan.cupSizeMl} ml")
            StatLine(label = "Water used", value = CuppingMath.formatWaterMl(CuppingMath.waterTotalMl(plan.cupSizeMl, plan.cupCount)))
            StatLine(label = "Coffee per cup", value = CuppingMath.formatGrams(CuppingMath.coffeePerCupG(plan.cupSizeMl)))
            StatLine(label = "Coffee total", value = CuppingMath.formatGrams(CuppingMath.coffeeTotalG(plan.cupSizeMl, plan.cupCount)))
        }
    }
}
