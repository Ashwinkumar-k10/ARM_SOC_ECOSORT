package com.ecosort.edge.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ecosort.edge.ui.AppStrings
import com.ecosort.edge.ui.theme.EcoColors

@Composable
fun ScannerScreen(
    isProcessing: Boolean,
    strings: AppStrings,
    onImageCaptured: (Bitmap) -> Unit,
    onPickUri: (android.net.Uri) -> Unit,
    onPickSample: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPerm = granted
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPickUri(uri)
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm) permLauncher.launch(Manifest.permission.CAMERA)
    }

    var torchEnabled by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0E12))) {
        if (hasCameraPerm) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture
                        try {
                            provider.unbindAll()
                            val cam = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
                            cameraControl = cam.cameraControl
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.cameraPermNeeded, color = Color.White, fontSize = 15.sp)
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(strings.grantCameraPerm, color = Color.White)
                }
            }
        }

        // Minimalist Apple-style corner reticle
        Canvas(modifier = Modifier.size(230.dp).align(Alignment.Center)) {
            val len = 34.dp.toPx()
            val stroke = 3.dp.toPx()
            val c = Color.White.copy(alpha = 0.88f)
            // Top-left
            drawLine(c, Offset(0f, 0f), Offset(len, 0f), stroke)
            drawLine(c, Offset(0f, 0f), Offset(0f, len), stroke)
            // Top-right
            drawLine(c, Offset(size.width, 0f), Offset(size.width - len, 0f), stroke)
            drawLine(c, Offset(size.width, 0f), Offset(size.width, len), stroke)
            // Bottom-left
            drawLine(c, Offset(0f, size.height), Offset(len, size.height), stroke)
            drawLine(c, Offset(0f, size.height), Offset(0f, size.height - len), stroke)
            // Bottom-right
            drawLine(c, Offset(size.width, size.height), Offset(size.width - len, size.height), stroke)
            drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - len), stroke)
        }

        // Top Pill
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
        ) {
            Text(
                if (isProcessing) strings.runningExecuTorch else strings.alignWasteItem,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Bottom Controls + Sample Strip
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Instant Benchmark 30 Sample Strip (works in Emulator & Demo)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    strings.sampleBattery to "item_01_battery_01.jpg",
                    strings.samplePlastic to "item_25_plastic_bottle_02.jpg",
                    strings.sampleFood to "item_07_food_waste_01.jpg",
                    strings.sampleCardboard to "item_04_cardboard_01.jpg"
                ).forEach { (label, file) ->
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier.clickable(enabled = !isProcessing) { onPickSample(file) }
                    ) {
                        Text(
                            "${strings.testPrefix}: $label",
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
                }

                // Shutter
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(4.dp, EcoColors.EmeraldBg),
                    modifier = Modifier.size(68.dp).clickable(enabled = !isProcessing && hasCameraPerm) {
                        imageCapture?.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bmp = imageProxyToBitmap(image)
                                    image.close()
                                    onImageCaptured(bmp)
                                }

                                override fun onError(exception: ImageCaptureException) {}
                            }
                        )
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                    }
                }

                IconButton(onClick = {
                    torchEnabled = !torchEnabled
                    cameraControl?.enableTorch(torchEnabled)
                }) {
                    Icon(
                        Icons.Outlined.FlashlightOn,
                        contentDescription = "Flash",
                        tint = if (torchEnabled) EcoColors.EmeraldBg else Color.White
                    )
                }
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val bmp = image.toBitmap()
    val rot = image.imageInfo.rotationDegrees
    if (rot == 0) return bmp
    val matrix = Matrix().apply { postRotate(rot.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}
