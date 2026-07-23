package com.maksimowiczm.foodyou.app.ui.common.image

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.maksimowiczm.foodyou.ai.domain.entity.AiImage
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

@Composable
internal actual fun rememberImageAcquisitionActions(
    onImage: (AiImage) -> Unit,
    onError: (ImageAcquisitionError) -> Unit,
): ImageAcquisitionActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnImage by rememberUpdatedState(onImage)
    val latestOnError by rememberUpdatedState(onError)
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun process(uri: Uri) {
        scope.launch {
            try {
                latestOnImage(prepareAiImage(context.contentResolver, uri))
            } catch (error: CancellationException) {
                throw error
            } catch (error: ImagePreparationException) {
                latestOnError(
                    when (error) {
                        ImagePreparationException.TooLarge -> ImageAcquisitionError.TooLarge
                        ImagePreparationException.InvalidImage -> ImageAcquisitionError.InvalidImage
                        ImagePreparationException.CannotRead -> ImageAcquisitionError.CannotRead
                    }
                )
            } catch (_: Exception) {
                latestOnError(ImageAcquisitionError.CannotRead)
            } finally {
                cameraFile?.delete()
                cameraFile = null
                cameraUri = null
            }
        }
    }

    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = cameraUri
            if (success && uri != null) process(uri)
            else {
                cameraFile?.delete()
                cameraFile = null
                cameraUri = null
            }
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) cameraUri?.let(takePicture::launch)
            else latestOnError(ImageAcquisitionError.PermissionDenied)
        }
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) process(uri)
        }

    fun launchCamera() {
        try {
            val directory = File(context.cacheDir, "ai-images").apply { mkdirs() }
            val file = File.createTempFile("photo-", ".jpg", directory)
            val uri =
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraFile = file
            cameraUri = uri
            if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            ) {
                takePicture.launch(uri)
            } else {
                permission.launch(Manifest.permission.CAMERA)
            }
        } catch (_: Exception) {
            latestOnError(ImageAcquisitionError.Unavailable)
        }
    }

    DisposableEffect(Unit) { onDispose { cameraFile?.delete() } }

    return ImageAcquisitionActions(
        isSupported = true,
        takePhoto = ::launchCamera,
        pickImage = {
            gallery.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
    )
}

@Composable
internal actual fun AiImagePreview(
    image: AiImage,
    contentDescription: String?,
    modifier: Modifier,
) {
    val bitmap = remember(image) { BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
