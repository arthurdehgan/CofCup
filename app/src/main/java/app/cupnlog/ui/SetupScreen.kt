package app.cupnlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cupnlog.data.CuppingDraft
import app.cupnlog.data.CuppingMath
import app.cupnlog.ui.components.StatLine
import app.cupnlog.ui.components.StepperRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(vm: CuppingViewModel) {
    val draft by vm.draft.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set-up Cupping Session") },
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Text(
            //     "Set up your cupping",
            //     style = MaterialTheme.typography.headlineSmall,
            //     fontWeight = FontWeight.Bold,
            //     modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            // )

            // Optional display name, shown for this session's saved setups.
            // Deliberately borderless — an "invisible" text field: the
            // placeholder explains what it is while it's empty, and once a
            // name is typed it grows to headline size, acting as the page
            // title above the rest of the form.
            BasicTextField(
                value = draft.sessionName,
                onValueChange = { vm.setSessionName(it) },
                singleLine = true,
                textStyle = (if (draft.sessionName.isEmpty()) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                }).copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.padding(vertical = 12.dp)) {
                        if (draft.sessionName.isEmpty()) {
                            Text(
                                "Session name",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            Text(
                "Cup size",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            StepperRow(
                label = "Cupping cup size",
                valueText = "${draft.cupSizeMl} ml",
                onDecrement = { vm.setCupSize(draft.cupSizeMl - 5) },
                onIncrement = { vm.setCupSize(draft.cupSizeMl + 5) },
                decrementEnabled = draft.cupSizeMl > CuppingDraft.MIN_CUP_SIZE,
                incrementEnabled = draft.cupSizeMl < CuppingDraft.MAX_CUP_SIZE,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // One chip per preset, each a quarter of the row width so the
            // row always fills the screen edge to edge.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(150, 200, 250, 300).forEach { size ->
                    FilterChip(
                        selected = draft.cupSizeMl == size,
                        onClick = { vm.setCupSize(size) },
                        label = {
                            Text(
                                "$size ml",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Number of coffees",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            StepperRow(
                label = "Coffees to cup",
                valueText = "${draft.cupCount}",
                helperText = "Between 1 and ${CuppingDraft.MAX_CUPS}",
                onDecrement = { vm.setCups(draft.cupCount - 1) },
                onIncrement = { vm.setCups(draft.cupCount + 1) },
                decrementEnabled = draft.cupCount > CuppingDraft.MIN_CUPS,
                incrementEnabled = draft.cupCount < CuppingDraft.MAX_CUPS,
            )

            Spacer(modifier = Modifier.height(12.dp))
            // Title and switch on one line; the explanation sits below at the
            // full width so it wraps naturally.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Blind tasting",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = draft.blind,
                    onCheckedChange = { vm.setBlind(it) },
                )
            }
            Text(
                "Each cup is labeled underneath and the cups get mixed, so you " +
                    "don't know which is which. Coffee names appear only after " +
                    "you identify each cup by its label.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    StatLine(
                        label = "Water to boil",
                        value = CuppingMath.formatWaterMl(
                            CuppingMath.waterTotalMl(draft.cupSizeMl, draft.cupCount)
                        ),
                    )
                    StatLine(
                        label = "Coffee per cup",
                        value = CuppingMath.formatGrams(
                            CuppingMath.coffeePerCupG(draft.cupSizeMl)
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Based on the SCA cupping ratio of 5.5g per 100ml",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { vm.openLabelCoffees() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Name and Label the Coffees")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
