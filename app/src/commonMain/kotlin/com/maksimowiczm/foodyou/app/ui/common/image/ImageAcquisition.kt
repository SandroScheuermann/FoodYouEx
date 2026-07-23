package com.maksimowiczm.foodyou.app.ui.common.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.maksimowiczm.foodyou.ai.domain.entity.AiImage

@Stable
internal class ImageAcquisitionActions(
    val isSupported: Boolean,
    val takePhoto: () -> Unit,
    val pickImage: () -> Unit,
)

internal sealed interface ImageAcquisitionError {
    data object PermissionDenied : ImageAcquisitionError

    data object Unavailable : ImageAcquisitionError

    data object UnsupportedType : ImageAcquisitionError

    data object TooLarge : ImageAcquisitionError

    data object InvalidImage : ImageAcquisitionError

    data object CannotRead : ImageAcquisitionError
}

@Composable
internal expect fun rememberImageAcquisitionActions(
    onImage: (AiImage) -> Unit,
    onError: (ImageAcquisitionError) -> Unit,
): ImageAcquisitionActions

@Composable
internal expect fun AiImagePreview(
    image: AiImage,
    contentDescription: String?,
    modifier: Modifier = Modifier,
)
