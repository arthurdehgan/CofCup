package app.cupnlog.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cupnlog.data.ShareCodec
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Phase 3 share: the saved setup as a QR encoding the https App Link (the
 * whole payload rides in the link — no backend). Falls back to share-as-text /
 * copy-link / bare code when the link exceeds the ~2 KB QR guard. The screen
 * never shows cup identity for blind setups: the payload itself carries none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSetupScreen(vm: CuppingViewModel, sessionId: String) {
    val history by vm.history.collectAsState()
    val myName by vm.myName.collectAsState()
    val session = history.firstOrNull { it.id == sessionId }

    // Keep the screen on while the QR shows (the shell only runs for a live
    // session; a shared setup is shown from Home, so the screen holds it here).
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share set-up") },
                navigationIcon = {
                    IconButton(onClick = { vm.handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        val context = LocalContext.current
        if (session == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                Text("This set-up no longer exists.")
            }
            return@Scaffold
        }

        val encode = remember(sessionId, session.plan, myName) {
            ShareCodec.encodeSetup(session, myName)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            // Summary line above the QR — names/colors never leak here: the
            // blind payload carries none.
            Text(
                if (session.blind) {
                    "Blind set-up · ${session.plan.cupCount} cups (names stay hidden until after the cupping)"
                } else {
                    "Full set-up · ${session.plan.cupCount} cups"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (session.plan.sessionName.isNotBlank()) {
                Text(
                    session.plan.sessionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (encode.fitsQr) {
                // White card with quiet zone; the QR carries the whole link.
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val qr = remember(encode.url) { qrBitmap(encode.url) }
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "QR code with this set-up",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(16.dp),
                    )
                }
            } else {
                // ~2 KB guard: an over-long link risks unscannable density.
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "This set-up is too large for a QR code. " +
                            "Share it as text or copy the link instead.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, encode.url)
                    }
                    context.startActivity(Intent.createChooser(send, "Share set-up"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Share as text") }
            Spacer(modifier = Modifier.height(8.dp))

            var copiedLink by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = {
                    copyToClipboard(context, encode.url)
                    copiedLink = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (copiedLink) "Link copied" else "Copy link") }
            Spacer(modifier = Modifier.height(8.dp))

            var copiedCode by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = {
                    copyToClipboard(context, encode.code)
                    copiedCode = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (copiedCode) "Code copied" else "Copy plain code") }

            Spacer(modifier = Modifier.height(16.dp))
            // The raw payload: the offline fallback when chat apps don't
            // linkify — the receiver pastes it into the import field.
            Text(
                "Or copy the plain code",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    encode.code,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { vm.handleBack() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Done") }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** Renders the QR with a built-in quiet zone, black on white. */
private fun qrBitmap(content: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 4),
    )
    val pixels = IntArray(size * size)
    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()
    for (y in 0 until size) {
        val offset = y * size
        for (x in 0 until size) {
            pixels[offset + x] = if (matrix.get(x, y)) black else white
        }
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("CofCup", text))
}

