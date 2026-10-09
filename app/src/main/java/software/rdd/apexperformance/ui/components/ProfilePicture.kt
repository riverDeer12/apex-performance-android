package software.rdd.apexperformance.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import software.rdd.apexperformance.R
import kotlin.math.abs
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.model.Profile
import software.rdd.apexperformance.ui.theme.ApexColors

// Profile picture of the logged user. Picture API needs the auth header,
// so it is loaded through ApiClient instead of an image loader.
// With `opensFullScreen` tapping it opens the picture full screen.
@Composable
fun ProfilePicture(profile: Profile?, size: Dp = 60.dp, opensFullScreen: Boolean = false) {
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var showFullScreen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(size / 6)

    LaunchedEffect(profile?.hasProfilePicture, profile?.profilePictureUpdatedAt) {
        if (profile == null || !profile.hasProfilePicture) {
            image = null
            return@LaunchedEffect
        }
        image = try {
            val data = ApiClient.requestData("profile/picture", headers = mapOf("Accept" to "image/*"))
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(ApexColors.main.copy(alpha = 0.25f))
            .then(
                if (opensFullScreen && image != null) Modifier.clickable { showFullScreen = true }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = ApexColors.main, modifier = Modifier.size(size / 2.5f))
        }
    }

    val bitmap = image
    if (showFullScreen && bitmap != null) {
        ProfilePictureViewer(bitmap) { showFullScreen = false }
    }
}

// Full screen profile picture with pinch and double tap to zoom,
// drag to move when zoomed, and swipe down or close to dismiss.
@Composable
private fun ProfilePictureViewer(image: ImageBitmap, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Vertical drag while not zoomed, used to dismiss.
    var dismissDrag by remember { mutableFloatStateOf(0f) }
    val maxScale = 4f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - minOf(abs(dismissDrag) / 1200f, 0.6f)))
        ) {
            Image(
                image,
                contentDescription = stringResource(R.string.profile_picture),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y + dismissDrag
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        })
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, maxScale)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                                dismissDrag += pan.y
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                            // Released: close after a long swipe, otherwise spring back.
                            if (abs(dismissDrag) > 360f) onDismiss() else dismissDrag = 0f
                        }
                    }
            )

            if (dismissDrag == 0f) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
            }
        }
    }
}
