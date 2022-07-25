package com.example.cardscanner

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "ScanResult"
typealias TextListener = (String) -> Unit

class MainActivity : AppCompatActivity() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private val imageAnalyzer by lazy {
        ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
            .also { analyzer ->
                analyzer.setAnalyzer(
                    cameraExecutor,
                    ImageAnalyzer { text ->
                        Log.i(TAG, text)
                    }
                )
            }
    }
    private var preview: PreviewView? = null

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var showPreview by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val cameraPermissionState = rememberPermissionState(
                        android.Manifest.permission.CAMERA
                    )

                    when (cameraPermissionState.status) {
                        is PermissionStatus.Denied -> {
                            Button(
                                onClick = {
                                    cameraPermissionState.launchPermissionRequest()
                                }
                            ) {
                                Text(text = "Ask permission")
                            }
                        }
                        PermissionStatus.Granted -> {
                            Button(
                                onClick = {
                                    showPreview = true
                                    startScanning()
                                }
                            ) {
                                Text(text = "Scan text")
                            }
                        }
                    }
                }
                if (showPreview) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center),
                            factory = { context ->
                                PreviewView(context)
                            },
                            update = { view ->
                                preview = view
                            }
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .padding(8.dp)
                                .align(Alignment.Center),
                            border = BorderStroke(3.dp, Color.White),
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {

                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp)
                                .align(Alignment.TopCenter),
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Scan Card")
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_close_24),
                                    contentDescription = null
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxHeight(0.3f)
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            color = Color.White
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Text(text = "Some Card scanning tip")
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(text = "Some Card scanning tip")
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(text = "Some Card scanning tip")
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(text = "Some Card scanning tip")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startScanning() {
        val cameraProvider = ProcessCameraProvider.getInstance(this)
        cameraProvider.addListener(
            {
                val preview = Preview.Builder()
                    .build()
                    .also { prev ->
                        if (preview == null) Log.e(TAG, "Preview is Null")
                        prev.setSurfaceProvider(preview?.surfaceProvider)
                    }
                cameraProvider.get().bind(preview, imageAnalyzer)
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun ProcessCameraProvider.bind(
        preview: Preview,
        imageAnalyzer: ImageAnalysis
    ) = try {
        unbindAll()
        bindToLifecycle(
            this@MainActivity,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalyzer
        )
    } catch (e: IllegalStateException) {
        Log.e(TAG, "Binding failed", e)
    }

    private inner class ImageAnalyzer(
        private val textFoundListener: TextListener
    ) : ImageAnalysis.Analyzer {
        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            try {
                /**Code below scanned whole available screen**/
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image =
                        InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    recognizer.process(image)
                        .addOnSuccessListener { text ->
                            processTextFromImage(text, imageProxy)
                            imageProxy.close()
                        }.addOnFailureListener {
                            imageProxy.close()
                            throw it
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "${e.message}")
            }
        }

        private fun processTextFromImage(visionText: Text, imageProxy: ImageProxy) {
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    textFoundListener(line.text)
                }
            }
        }
    }
}