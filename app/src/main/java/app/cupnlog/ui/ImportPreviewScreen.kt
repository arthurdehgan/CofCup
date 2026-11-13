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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cupnlog.data.CoffeeEntry
import app.cupnlog.data.ShareCodec
import app.cupnlog.ui.components.sessionSummary

/**
 * Phase 3 confirm preview: shows exactly what an incoming link / scan / paste
 * carries — host name when known, what would be saved — and asks. Nothing is
 * auto-saved, auto-started or auto-revealed, ever. Blind payloads display no
 * identity they don't carry: their preview is "Cup 1…N" only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(vm: CuppingViewModel) {
    val incoming by vm.incoming.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Incoming set-up") },
                navigationIcon = {
                    IconButton(onClick = { vm.dismissImport() }) {
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            when (val share = incoming) {
                null -> {
                    // Transient (navigating away); nothing to confirm.
                }

                is CuppingViewModel.IncomingShare.Error -> {
                    Text(
                        "Can't open this code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        share.error.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { vm.dismissImport() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("OK") }
                }

                is CuppingViewModel.IncomingShare.Setup -> {
                    val setup = share.setup
                    if (setup.hostName.isNotBlank()) {
                        Text(
                            "From ${setup.hostName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        setup.sessionName.ifBlank {
                            "Cupping · ${setup.cupCount} cup" + if (setup.cupCount == 1) "" else "s"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        sessionSummary(setup.cupCount, setup.cupSizeMl),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            if (setup.blind) {
                                // Blind payloads carry no identities at all —
                                // positions only, exactly what the joiner gets.
                                Text(
                                    "Blind set-up · ${setup.cupCount} cups (identities hidden)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                repeat(setup.cupCount) { index ->
                                    Text(
                                        "Cup ${index + 1}",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (index != setup.cupCount - 1) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            } else {
                                setup.coffees.forEachIndexed { index, coffee ->
                                    CupPreviewLine(index = index, coffee = coffee)
                                    if (index != setup.coffees.size - 1) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { vm.confirmImport() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Add to saved setups") }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.dismissImport() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancel") }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** One preview line: "Cup 1 — Ethiopia" (names as carried by the payload). */
@Composable
private fun CupPreviewLine(index: Int, coffee: CoffeeEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Cup ${index + 1}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(72.dp),
        )
        Text(
            if (coffee.name.isBlank()) "\u2014" else coffee.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (coffee.name.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
