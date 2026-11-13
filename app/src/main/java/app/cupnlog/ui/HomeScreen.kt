package app.cupnlog.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import android.widget.ImageView
import androidx.compose.ui.viewinterop.AndroidView
import app.cupnlog.R
import app.cupnlog.data.SessionStatus
import app.cupnlog.data.SavedSession
import app.cupnlog.ui.components.displaySessionName

/**
 * The app's home: launcher icon, title, history, and the two entry points —
 * straight to the timer, or the full guided setup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: CuppingViewModel) {
    val history by vm.history.collectAsState()
    val plannedSetups = history.filter { it.status == SessionStatus.PLANNED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cup 'n Log") },
                actions = {
                    IconButton(onClick = { vm.openHistory() }) {
                        Icon(Icons.Default.History, contentDescription = "Session history")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The launcher icon is an adaptive icon (not a plain drawable), so a
                // classic ImageView is needed — painterResource() would crash on it.
                AndroidView(
                    modifier = Modifier.size(96.dp),
                    factory = { context ->
                        ImageView(context).apply { setImageResource(R.mipmap.ic_launcher) }
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Cup 'n Log",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Guided coffee cupping, step by step, with timers " +
                        "following the SCA protocol.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (plannedSetups.isNotEmpty()) {
                Text(
                    "Saved setups",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(plannedSetups.size) { index ->
                        PlannedSetupChip(
                            session = plannedSetups[index],
                            onRun = { vm.startPlannedRun(plannedSetups[index].id) },
                            onEdit = { vm.editPlannedSetup(plannedSetups[index].id) },
                            onShare = { vm.openShareSetup(plannedSetups[index].id) },
                            onDelete = { vm.deleteSession(plannedSetups[index].id) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = { vm.quickStartCupping() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Text(
                    "Start Cupping",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(
                onClick = { vm.openSetup() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    "Set up cupping",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { vm.openScanner() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Join a cupping — scan or paste a code",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Spacer(modifier = Modifier.height(8.dp))
            // Text(
            //     " " +
            //         "\u201cEmpty Cupping Session\u201d.",
            //     style = MaterialTheme.typography.bodySmall,
            //     color = MaterialTheme.colorScheme.onSurfaceVariant,
            //     textAlign = TextAlign.Center,
            //     modifier = Modifier.fillMaxWidth(),
            // )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** One row of the saved-setup popup: a tight icon + text row (M3's
 *  DropdownMenuItem has a built-in 112 dp minimum width, so the rows are
 *  hand-rolled to keep the popup close to its content). */
@Composable
private fun SavedSetupMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** One saved setup on Home: tap runs it straight to StartCheck; ⋮ offers
 *  edit / share (the QR) / delete. */
@Composable
private fun PlannedSetupChip(
    session: SavedSession,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val blind = session.blind && session.revealedAtMs == null
    val label = if (session.plan.sessionName.isNotBlank()) {
        // The session name never leaks cup identity, so blind setups show it too.
        displaySessionName(session.plan.sessionName)
    } else {
        "Unnamed setup"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.clickable(onClick = onRun),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Saved setup options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    // M3's DropdownMenu always opens downward from its anchor;
                    // this popup aligns to the box's bottom edge so the menu
                    // grows upwards from the ⋮ button instead. It must be gated
                    // on menuOpen — a Popup renders whenever it is composed.
                    if (menuOpen) {
                        Popup(
                            alignment = Alignment.BottomStart,
                            onDismissRequest = { menuOpen = false },
                            properties = PopupProperties(focusable = true),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shadowElevation = 3.dp,
                            ) {
                                Column {
                                    SavedSetupMenuItem(
                                        icon = Icons.Default.Edit,
                                        label = "Edit",
                                        onClick = { menuOpen = false; onEdit() },
                                    )
                                    SavedSetupMenuItem(
                                        icon = Icons.Default.Share,
                                        label = "Share",
                                        onClick = { menuOpen = false; onShare() },
                                    )
                                    SavedSetupMenuItem(
                                        icon = Icons.Default.Delete,
                                        label = "Delete",
                                        onClick = { menuOpen = false; onDelete() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${session.plan.cupCount} coffee" + if (session.plan.cupCount == 1) "" else "s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (blind) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.VisibilityOff,
                        contentDescription = "Blind setup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
