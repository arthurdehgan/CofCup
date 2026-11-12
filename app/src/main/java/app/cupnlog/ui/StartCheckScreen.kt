package app.cupnlog.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cupnlog.data.CuppingMath
import app.cupnlog.ui.components.SwatchCircle
import app.cupnlog.ui.components.StatLine

/**
 * The gate before the session: exactly one big "Start cupping" button
 * and one smaller "Back" button, plus a printable summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartCheckScreen(vm: CuppingViewModel) {
    val draft by vm.draft.collectAsState()
    val plannedRunSource by vm.plannedRunSource.collectAsState()
    val plan = draft.toPlan()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ready to cup") },
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    StatLine(
                        label = "Coffees",
                        value = "${plan.cupCount}",
                    )
                    StatLine(
                        label = "Cup size",
                        value = "${plan.cupSizeMl} ml",
                    )
                    StatLine(
                        label = "Water to boil",
                        value = CuppingMath.formatWaterMl(
                            CuppingMath.waterTotalMl(plan.cupSizeMl, plan.cupCount)
                        ),
                    )
                    StatLine(
                        label = "Coffee per cup",
                        value = CuppingMath.formatGrams(
                            CuppingMath.coffeePerCupG(plan.cupSizeMl)
                        ),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Your cups on the table:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        plan.coffees.forEach { coffee ->
                            SwatchCircle(
                                colorArgb = coffee.colorArgb,
                                insideText = coffee.displayNumber,
                                size = 36.dp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Set the cups in a row, grind the coffee into each cup, and have the " +
                    "water heated and ready. Press \"Start cupping\" at the same moment " +
                    "water first hits the grounds of the first cup.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            Button(
                onClick = { vm.startLiveSession() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Start cupping", fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextButton(
                    onClick = { vm.handleBack() },
                ) {
                    Text("Back")
                }
                // Hidden when running an existing saved setup: that row already
                // exists and the run will convert it to done on completion.
                if (plannedRunSource == null) {
                    TextButton(
                        onClick = { vm.saveSetup() },
                    ) {
                        Text("Save setup")
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
