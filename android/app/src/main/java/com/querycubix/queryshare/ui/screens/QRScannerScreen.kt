package com.querycubix.queryshare.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.querycubix.queryshare.data.model.QrPayload
import com.querycubix.queryshare.data.model.parseQrPayload
import com.querycubix.queryshare.viewmodel.MainViewModel
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onConnected: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val uiState by viewModel.uiState.collectAsState()
    val networkGuidance by viewModel.networkGuidance.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val scanMessage = remember { mutableStateOf("Point the camera at the server QR code") }
    // Track whether a successful scan has been handled to avoid duplicate connect attempts
    val scanCompleted = remember { mutableStateOf(false) }

    LaunchedEffect(uiState, connectionError) {
        when (uiState) {
            MainViewModel.UIState.CONNECTED -> onConnected()
            MainViewModel.UIState.FAILED -> {
                scanMessage.value = "Connection failed. Check the desktop Hotspot QR and try again."
                errorMessage.value = connectionError ?: "Connection failed. Check the hotspot and scan again."
                // allow rescanning after a failure
                scanCompleted.value = false
            }
            MainViewModel.UIState.SCANNING, MainViewModel.UIState.DISCOVERED -> {
                // reset state so user can scan again
                scanCompleted.value = false
                errorMessage.value = null
                scanMessage.value = "Point the camera at the server QR code"
            }
            else -> {}
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Scan QR") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = scanMessage.value,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                networkGuidance?.let { message ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Connect to hotspot first", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!cameraPermissionGranted) {
                        Text(
                            text = "Camera permission is required to scan pairing QR codes.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        // Camera preview with scanning overlay and clearer feedback
                        CameraPreviewScanner(
                            modifier = Modifier.fillMaxSize(),
                            lifecycleOwner = lifecycleOwner,
                            onQrCodeDetected = { rawValue ->
                                if (scanCompleted.value) return@CameraPreviewScanner
                                // mark handled immediately to prevent duplicate attempts
                                scanCompleted.value = true
                                Log.d("QRScannerScreen", "QR_DETECTED: $rawValue")
                                val payload = parseQrPayload(rawValue)
                                if (payload == null) {
                                    Log.e("QRScannerScreen", "QR_PARSE_FAILED for: $rawValue")
                                    scanMessage.value = "Invalid QR payload. Please scan a valid Tumira code."
                                    errorMessage.value = "Invalid QR payload"
                                    scanCompleted.value = false
                                    return@CameraPreviewScanner
                                }
                                scanMessage.value = "Found ${payload.deviceName}. Connecting..."
                                errorMessage.value = null
                                Log.d("QRScannerScreen", "QR_PAYLOAD_VALID: calling connectWithQrPayload")
                                viewModel.connectWithQrPayload(payload)
                            }
                        )

                        // Scanning frame overlay to guide users where to place QR
                        ScanningFrame(modifier = Modifier.align(Alignment.Center))

                        // Small scanning indicator overlay
                        if (uiState == MainViewModel.UIState.SCANNING || uiState == MainViewModel.UIState.DISCOVERED) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Scanning...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Status area with clearer labels and connection progress
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) {
                    if (uiState == MainViewModel.UIState.CONNECTING) {
                        CircularProgressIndicator(modifier = Modifier.padding(bottom = 8.dp))
                    }

                    Text(
                        text = when (uiState) {
                            MainViewModel.UIState.SCANNING -> "Scanning"
                            MainViewModel.UIState.CONNECTING -> "Connecting"
                            MainViewModel.UIState.CONNECTED -> "Connected"
                            MainViewModel.UIState.FAILED -> "Failed"
                            MainViewModel.UIState.DISCOVERED -> "Devices found"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = when (uiState) {
                            MainViewModel.UIState.CONNECTED -> Color(0xFF4CAF50)
                            MainViewModel.UIState.FAILED -> Color(0xFFC62828)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Text(
                        text = scanMessage.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    errorMessage.value?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanningFrame(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(260.dp)
            .border(2.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        // subtle inner corners
        Box(modifier = Modifier.matchParentSize())
    }
}

@Composable
private fun CameraPreviewScanner(
    modifier: Modifier,
    lifecycleOwner: LifecycleOwner,
    onQrCodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val onQrCodeDetectedState = rememberUpdatedState(onQrCodeDetected)
    val scanInProgress = remember { AtomicBoolean(false) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()

                    val scanner = BarcodeScanning.getClient(options)

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy: ImageProxy ->
                        if (scanInProgress.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanInProgress.set(true)
                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                                        ?.rawValue
                                        ?.let { rawValue ->
                                            onQrCodeDetectedState.value(rawValue)
                                        }
                                }
                                .addOnFailureListener { exception ->
                                    Log.w("QRScanner", "Barcode scan failed", exception)
                                }
                                .addOnCompleteListener {
                                    scanInProgress.set(false)
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (error: Exception) {
                    Log.e("QRScanner", "Camera initialization failed", error)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
