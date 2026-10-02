package software.rdd.apexperformance.ui.components

import android.graphics.BitmapFactory
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
@Composable
fun ProfilePicture(profile: Profile?, size: Dp = 60.dp) {
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
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
            .background(ApexColors.main.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = ApexColors.main, modifier = Modifier.size(size / 2.5f))
        }
    }
}
