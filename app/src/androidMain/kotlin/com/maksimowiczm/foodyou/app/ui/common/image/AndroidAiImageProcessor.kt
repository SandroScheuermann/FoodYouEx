package com.maksimowiczm.foodyou.app.ui.common.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import com.maksimowiczm.foodyou.ai.domain.entity.AiImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal suspend fun prepareAiImage(contentResolver: ContentResolver, uri: Uri): AiImage =
    withContext(Dispatchers.IO) {
        val declaredSize =
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) it.getLong(0) else -1L
            } ?: -1L
        if (declaredSize > MAX_IMAGE_BYTES) throw ImagePreparationException.TooLarge

        val sourceBytes =
            contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMAGE_BYTES) throw ImagePreparationException.TooLarge
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: throw ImagePreparationException.CannotRead
        if (sourceBytes.isEmpty()) throw ImagePreparationException.InvalidImage

        val source = ImageDecoder.createSource(ByteBuffer.wrap(sourceBytes))
        val bitmap =
            try {
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = info.size.width
                    val height = info.size.height
                    if (width <= 0 || height <= 0) throw ImagePreparationException.InvalidImage
                    val largest = maxOf(width, height)
                    if (largest > MAX_DIMENSION) {
                        val scale = MAX_DIMENSION.toDouble() / largest
                        decoder.setTargetSize(
                            (width * scale).roundToInt(),
                            (height * scale).roundToInt(),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (error: ImagePreparationException) {
                throw error
            } catch (_: Exception) {
                throw ImagePreparationException.InvalidImage
            }

        val output = ByteArrayOutputStream()
        val hasAlpha = bitmap.hasAlpha()
        val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val quality = if (hasAlpha) 100 else 85
        if (!bitmap.compress(format, quality, output)) throw ImagePreparationException.InvalidImage
        bitmap.recycle()
        val bytes = output.toByteArray()
        if (bytes.size > MAX_IMAGE_BYTES) throw ImagePreparationException.TooLarge
        AiImage(bytes = bytes, mimeType = if (hasAlpha) "image/png" else "image/jpeg")
    }

internal sealed class ImagePreparationException : Exception() {
    data object TooLarge : ImagePreparationException()

    data object InvalidImage : ImagePreparationException()

    data object CannotRead : ImagePreparationException()
}

private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
private const val MAX_DIMENSION = 1800
