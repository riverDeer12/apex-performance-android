package software.rdd.apexperformance.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import software.rdd.apexperformance.R

// Intro video shown over the app when it starts, like SplashView on iOS.
// The system splash has the video's background color, so the switch is
// invisible; the video plays once without sound and fades out to the app.
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val context = LocalContext.current
    val opacity = remember { Animatable(1f) }
    val ended = remember { CompletableDeferred<Unit>() }
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // Null when the video can't be opened, then only the fade is shown.
    val player = remember {
        if (reduceMotion) null else runCatching { MediaPlayer.create(context, R.raw.splash) }.getOrNull()
    }

    DisposableEffect(player) {
        player?.setVolume(0f, 0f)
        player?.setOnCompletionListener { ended.complete(Unit) }
        player?.setOnErrorListener { _, _, _ -> ended.complete(Unit); true }
        onDispose { player?.release() }
    }

    LaunchedEffect(Unit) {
        if (player == null) {
            delay(300)
        } else {
            // Until the video ends, or the timeout when it doesn't play.
            withTimeoutOrNull(4_000) { ended.await() }
        }
        opacity.animateTo(0f, tween(350))
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(opacity.value)
                .background(colorResource(R.color.splash_background))
        )

        if (player != null) {
            AndroidView(
                factory = { viewContext ->
                    TextureView(viewContext).apply {
                        isOpaque = false
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                                runCatching {
                                    player.setSurface(Surface(texture))
                                    player.start()
                                }.onFailure { ended.complete(Unit) }
                            }

                            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}

                            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true

                            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
                        }
                    }
                },
                // Faded with the view's own alpha, next to the background above.
                update = { it.alpha = opacity.value },
                // Square video, as wide as the screen.
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        }
    }
}
