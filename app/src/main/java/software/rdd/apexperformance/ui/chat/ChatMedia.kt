package software.rdd.apexperformance.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.model.ChatApi
import software.rdd.apexperformance.model.ChatAttachment
import software.rdd.apexperformance.model.ChatImageUpload
import software.rdd.apexperformance.model.Workout
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SmallProgress
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.components.WorkoutThumbnail
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import software.rdd.apexperformance.ui.workouts.WorkoutDetailScreen
import java.io.ByteArrayOutputStream

object ChatImages {
    // Same limit as the API.
    private const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_DIMENSION = 1600

    // Pictures of messages loaded so far, kept while the app runs.
    private val cache = object : LruCache<String, ByteArray>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    suspend fun load(attachment: ChatAttachment, conversationId: String): ByteArray {
        cache.get(attachment.id)?.let { return it }
        val data = ChatApi.attachment(attachment.id, conversationId)
        cache.put(attachment.id, data)
        return data
    }

    // Photos are shrunk to JPEG, GIFs are kept so they stay animated.
    // Null when the picture can't be read or is too large.
    suspend fun prepare(context: Context, uri: Uri): ChatImageUpload? = withContext(Dispatchers.IO) {
        val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        if (data.size >= 4 && String(data, 0, 4, Charsets.US_ASCII) == "GIF8") {
            if (data.size > MAX_BYTES) return@withContext null
            return@withContext ChatImageUpload(data, "image/gif", "image.gif", bounds.outWidth, bounds.outHeight)
        }

        val bitmap = decodePhoto(context, uri) ?: return@withContext null
        val jpeg = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)
            it.toByteArray()
        }
        if (jpeg.size > MAX_BYTES) return@withContext null
        ChatImageUpload(jpeg, "image/jpeg", "image.jpg", bitmap.width, bitmap.height)
    }

    // At most MAX_DIMENSION on the longer side, turned like the photo was taken.
    private fun decodePhoto(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = minOf(1f, MAX_DIMENSION.toFloat() / maxOf(info.size.width, info.size.height))
                if (scale < 1f) {
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val full = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            val scale = minOf(1f, MAX_DIMENSION.toFloat() / maxOf(full.width, full.height))
            if (scale < 1f) {
                Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true)
            } else full
        }
    }.getOrNull()
}

// Coil loader that also animates GIFs.
@Composable
private fun rememberChatImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
    }
}

@Composable
private fun ChatPicture(data: ByteArray, contentScale: ContentScale, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(data) { ImageRequest.Builder(context).data(data).build() },
        imageLoader = rememberChatImageLoader(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier
    )
}

// Picture of a message; tapping opens it full screen.
@Composable
fun ChatImageBubble(attachment: ChatAttachment, conversationId: String) {
    var data by remember(attachment.id) { mutableStateOf<ByteArray?>(null) }
    var failed by remember(attachment.id) { mutableStateOf(false) }
    var showsViewer by remember { mutableStateOf(false) }

    LaunchedEffect(attachment.id) {
        runCatching { ChatImages.load(attachment, conversationId) }
            .onSuccess { data = it }
            .onFailure { failed = true }
    }

    val ratio = if ((attachment.width ?: 0) > 0 && (attachment.height ?: 0) > 0) {
        attachment.width!!.toFloat() / attachment.height!!
    } else 4f / 3f
    val width = 240.dp

    Box(
        modifier = Modifier
            .size(width, width / ratio.coerceIn(0.5f, 2f))
            .clip(RoundedCornerShape(16.dp))
            .background(ApexColors.card)
            .clickable(enabled = data != null) { showsViewer = true },
        contentAlignment = Alignment.Center
    ) {
        val loaded = data
        when {
            loaded != null -> ChatPicture(loaded, ContentScale.Crop, Modifier.fillMaxSize())
            failed -> Icon(Icons.Outlined.Image, contentDescription = null, tint = ApexColors.secondaryLabel)
            else -> SmallProgress()
        }
    }

    val loaded = data
    if (showsViewer && loaded != null) {
        Dialog(
            onDismissRequest = { showsViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                ChatPicture(loaded, ContentScale.Fit, Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .clickable { showsViewer = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
            }
        }
    }
}

// Workout a coach shared; opens the workout.
@Composable
fun ChatWorkoutCard(workout: Workout?) {
    val navigator = LocalNavigator.current
    Row(
        modifier = Modifier
            .width(240.dp)
            .background(ApexColors.card, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = workout != null) {
                workout?.let { navigator.push(WorkoutDetailScreen(it, canEdit = false, onSaved = {})) }
            }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WorkoutThumbnail(
            workout?.thumbnailUrl,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.chat_shared_workout).uppercase(),
                style = ApexText.caption.copy(fontWeight = FontWeight.SemiBold),
                color = ApexColors.secondaryLabel
            )
            Text(
                workout?.name?.localized ?: stringResource(R.string.chat_workout_unavailable),
                style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = ApexColors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (workout != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ApexColors.secondaryLabel)
        }
    }
}

// Searchable workouts; tapping one shares it in the chat.
class ChatWorkoutPickerScreen(private val onPick: (Workout) -> Unit) : Screen() {

    private var workouts by mutableStateOf<List<Workout>>(emptyList())
    private var searchText by mutableStateOf("")
    private var isLoading by mutableStateOf(false)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        LaunchedEffect(Unit) {
            if (workouts.isEmpty()) load()
        }

        ScrollScreen(
            topBar = { TopBar(title = stringResource(R.string.chat_share_workout)) },
            showLoading = isLoading && workouts.isEmpty()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .apexCard()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                PlainTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = stringResource(R.string.search_workouts),
                    textStyle = ApexText.subheadline,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                val list = workouts.filter { it.matches(searchText) }
                Column {
                    list.forEach { workout ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navigator.pop()
                                    onPick(workout)
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WorkoutThumbnail(
                                workout.thumbnailUrl,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Text(workout.name.localized, style = ApexText.body, modifier = Modifier.weight(1f))
                        }
                        if (workout.id != list.last().id) RowDivider(startIndent = 56.dp)
                    }
                }
            }
        }
    }

    private suspend fun load() {
        isLoading = true
        try {
            workouts = ApiClient.get<List<Workout>>("workouts").sortedBy { it.name.localized.lowercase() }
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoading = false
        }
    }
}
