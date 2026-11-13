package app.cupnlog.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/**
 * Phase 3 join: the in-app QR scanner (user decision 7), with the paste-code
 * fallback always visible below — permission denial or a missing camera never
 * gates importing. Decoded payloads go through [CuppingViewModel.handleIncoming]
 * (same codec as deep links) and always land in the confirm preview; the
 * scanner itself never saves anything. Non-CofCup codes are ignored with a
 * plain hint and scanning continues.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(vm: CuppingViewModel) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> cameraGranted = granted }
    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan a setup code") },
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
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            if (cameraGranted) {
                ScannerView(vm)
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Camera access is needed to scan a code.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }) { Text("Allow camera") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Or paste a code",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            var pasted by remember { mutableStateOf("") }
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                placeholder = { Text("Paste the code here") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { vm.handleIncoming(pasted) },
                enabled = pasted.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import") }

            val hint by vm.scanHint.collectAsState()
            if (hint != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    hint.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** The zxing embedded surface: QR-only, scanning pauses once a CofCup payload lands. */
@Composable
private fun ScannerView(vm: CuppingViewModel) {
    val context = LocalContext.current
    val barcodeView = remember {
        DecoratedBarcodeView(context).apply {
            barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        }
    }
    val callback = remember {
        BarcodeCallback { result ->
            val text = result.text ?: return@BarcodeCallback
            // True → a CofCup payload (valid or its own error) took over:
            // the confirm preview opened, so stop scanning. False → not a
            // CofCup code; the plain hint shows below and scanning continues.
            if (vm.handleIncoming(text)) barcodeView.pause()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // decodeContinuous() only sets the decode mode; the camera
                    // is opened and the preview started by resume() alone
                    // (CameraPreview's contract: both must be called).
                    barcodeView.resume()
                    barcodeView.decodeContinuous(callback)
                }
                Lifecycle.Event.ON_PAUSE -> barcodeView.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            barcodeView.pause()
        }
    }
    AndroidView(
        factory = { barcodeView },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    )
}
