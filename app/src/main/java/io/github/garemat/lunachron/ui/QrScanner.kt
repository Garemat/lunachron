package io.github.garemat.lunachron.ui

import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScanner(
    onResult: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var isScanningComplete by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val reader = MultiFormatReader()
                val mainExecutor = ContextCompat.getMainExecutor(ctx)
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    if (!isScanningComplete) {
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            // Extract Y (luminance) plane, stripping any row-stride padding
                            val plane = mediaImage.planes[0]
                            val buffer = plane.buffer
                            val rowStride = plane.rowStride
                            val width = mediaImage.width
                            val height = mediaImage.height
                            val yBytes = ByteArray(width * height)
                            if (rowStride == width) {
                                buffer.get(yBytes)
                            } else {
                                val rowData = ByteArray(rowStride)
                                for (row in 0 until height) {
                                    buffer.get(rowData, 0, rowStride)
                                    rowData.copyInto(yBytes, row * width, 0, width)
                                }
                            }

                            // Rotate Y-plane bytes to match display orientation.
                            // PlanarYUVLuminanceSource.rotateCounterClockwise() is not overridden
                            // in ZXing 3.x, so we rotate the raw bytes instead.
                            val (rotatedBytes, rotatedWidth, rotatedHeight) = rotateYCW(
                                yBytes, width, height, imageProxy.imageInfo.rotationDegrees
                            )

                            val source = PlanarYUVLuminanceSource(
                                rotatedBytes, rotatedWidth, rotatedHeight,
                                0, 0, rotatedWidth, rotatedHeight, false
                            )

                            try {
                                val result = reader.decode(BinaryBitmap(HybridBinarizer(source)))
                                // Dispatch to main thread: onResult may trigger navigation
                                mainExecutor.execute {
                                    isScanningComplete = true
                                    onResult(result.text)
                                }
                            } catch (_: NotFoundException) {
                                // No barcode in this frame — keep scanning
                            } finally {
                                reader.reset()
                            }
                        }
                    }
                    imageProxy.close()
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("QrScanner", "Use case binding failed", e)
                }
            }, executor)

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Rotates a flat Y-plane byte array clockwise by [degrees] (0/90/180/270).
 * Returns the rotated bytes plus the new width and height.
 */
private fun rotateYCW(
    data: ByteArray,
    width: Int,
    height: Int,
    degrees: Int,
): Triple<ByteArray, Int, Int> {
    if (degrees == 0) return Triple(data, width, height)
    var src = data; var w = width; var h = height
    repeat((degrees / 90) % 4) {
        val dst = ByteArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                // 90° clockwise: dst[x][h-1-y] = src[y][x]
                dst[x * h + (h - 1 - y)] = src[y * w + x]
            }
        }
        val newW = h; h = w; w = newW
        src = dst
    }
    return Triple(src, w, h)
}
