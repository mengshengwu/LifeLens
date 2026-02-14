package com.example.lifelens.camera

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    outputFile: File
): File = suspendCancellableCoroutine { cont ->
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    val executor = ContextCompat.getMainExecutor(context)

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                if (cont.isActive) cont.resume(outputFile)
            }

            override fun onError(exception: ImageCaptureException) {
                if (cont.isActive) cont.resumeWithException(exception)
            }
        }
    )

    cont.invokeOnCancellation {
        runCatching { if (outputFile.exists()) outputFile.delete() }
    }
}
