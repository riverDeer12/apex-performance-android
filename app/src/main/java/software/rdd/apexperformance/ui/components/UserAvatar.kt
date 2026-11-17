package software.rdd.apexperformance.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Round profile picture of a client, coach or administrator, with their
// initials while it loads or when they have none. `pictureUrl` is the API's
// link ("users/{id}/picture?v=..."), it changes with the picture.
// With `opensFullScreen` tapping the picture opens it full screen.
@Composable
fun UserAvatar(
    name: String,
    pictureUrl: String?,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    opensFullScreen: Boolean = false
) {
    // Pictures seen before show at once, without the initials flashing.
    var image by remember(pictureUrl) { mutableStateOf(UserPictures.cached(pictureUrl)) }
    var showFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(pictureUrl) {
        if (image == null) image = UserPictures.load(pictureUrl)
    }

    val initials = name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
    val bitmap = image
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ApexColors.accent)
            .then(
                if (opensFullScreen && bitmap != null) Modifier.clickable { showFullScreen = true }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                initials.ifEmpty { "?" },
                style = ApexText.subheadline.copy(fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp),
                color = ApexColors.onAccent,
                maxLines = 1
            )
        }
    }

    if (showFullScreen && bitmap != null) {
        ProfilePictureViewer(bitmap) { showFullScreen = false }
    }
}

// Small round icon on the bottom right of an avatar, e.g. the status of an
// appointment or the type of a request.
@Composable
fun AvatarWithBadge(
    badgeIcon: ImageVector,
    badgeTint: Color,
    badgeSize: Dp = 18.dp,
    avatar: @Composable () -> Unit
) {
    Box {
        avatar()
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 3.dp, y = 3.dp)
                .size(badgeSize)
                .clip(CircleShape)
                .background(badgeTint)
                .border(1.5.dp, ApexColors.card, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(badgeIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(badgeSize * 0.6f))
        }
    }
}

// Pictures of several people (e.g. a group appointment), overlapping;
// the first `maxShown` are drawn, the rest as "+N".
@Composable
fun UserAvatarStack(people: List<Pair<String, String?>>, size: Dp = 40.dp, maxShown: Int = 3) {
    val shown = people.take(if (people.size > maxShown) maxShown - 1 else maxShown)
    val rest = people.size - shown.size
    // One person fills the whole size; more share it, overlapping.
    val itemSize = if (people.size > 1) size * 0.72f else size
    val ring = if (people.size > 1) 2.dp else 0.dp
    // Each next picture starts 60 % into the previous one.
    val step = itemSize * 0.6f
    Box {
        shown.forEachIndexed { index, (name, pictureUrl) ->
            UserAvatar(
                name, pictureUrl, size = itemSize,
                modifier = Modifier.padding(start = step * index).border(ring, ApexColors.card, CircleShape)
            )
        }
        if (rest > 0) {
            Box(
                modifier = Modifier
                    .padding(start = step * shown.size)
                    .size(itemSize)
                    .clip(CircleShape)
                    .background(ApexColors.systemGray5)
                    .border(2.dp, ApexColors.card, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+$rest",
                    style = ApexText.caption.copy(fontWeight = FontWeight.Bold, fontSize = (itemSize.value * 0.34f).sp),
                    color = ApexColors.secondaryLabel
                )
            }
        }
    }
}

// Picture and name of a person, e.g. a row of a client picker.
@Composable
fun UserNameLabel(name: String, pictureUrl: String?, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        UserAvatar(name, pictureUrl, size = size)
        Text(name, style = ApexText.body, color = ApexColors.label)
    }
}

// Profile pictures of other users loaded so far, kept while the app runs.
// Loaded with the login token (and the demo data in screenshot mode).
object UserPictures {
    private val images = LruCache<String, ImageBitmap>(200)
    private val loading = mutableMapOf<String, CompletableDeferred<ImageBitmap?>>()

    fun cached(pictureUrl: String?): ImageBitmap? = pictureUrl?.let { images.get(it) }

    // The picture, or null when there is none or it can't be loaded
    // (the initials stay then).
    suspend fun load(pictureUrl: String?): ImageBitmap? {
        if (pictureUrl == null) return null
        cached(pictureUrl)?.let { return it }

        val (deferred, isOwner) = synchronized(loading) {
            loading[pictureUrl]?.let { it to false }
                ?: CompletableDeferred<ImageBitmap?>().also { loading[pictureUrl] = it }.let { it to true }
        }
        if (!isOwner) return deferred.await()

        val image = try {
            val data = ApiClient.requestData(pictureUrl, headers = mapOf("Accept" to "image/*"))
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                synchronized(loading) { loading.remove(pictureUrl) }
                deferred.complete(null)
                throw e
            }
            null
        }
        image?.let { images.put(pictureUrl, it) }
        synchronized(loading) { loading.remove(pictureUrl) }
        deferred.complete(image)
        return image
    }
}
