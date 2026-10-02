package software.rdd.apexperformance.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.SubcomposeAsyncImage
import software.rdd.apexperformance.ui.theme.ApexColors

object YouTubeVideo {
    // Same link formats the API accepts: watch?v=, youtu.be/, shorts/, embed/ and live/.
    private val pattern = Regex(
        """(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\.be/)([A-Za-z0-9_-]{6,})""",
        RegexOption.IGNORE_CASE
    )

    fun id(url: String?): String? = url?.let { pattern.find(it)?.groupValues?.get(1) }
}

// Plays a YouTube video inline through YouTube's embedded player.
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(videoId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val webView = remember(videoId) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(AndroidColor.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            // YouTube rejects embeds without a referrer (error 153),
            // so the player page is loaded under the app's own domain.
            loadDataWithBaseURL(EMBED_ORIGIN, html(videoId), "text/html", "utf-8", null)
        }
    }

    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }

    AndroidView(factory = { webView }, modifier = modifier)
}

private const val EMBED_ORIGIN = "https://apex-performance.fit"

// videoId only contains [A-Za-z0-9_-], so it is safe to put into the HTML.
private fun html(videoId: String) = """
    <!DOCTYPE html>
    <html>
    <head>
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <style>
    html, body { margin: 0; padding: 0; height: 100%; background: #000; overflow: hidden; }
    iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
    </style>
    </head>
    <body>
    <iframe src="https://www.youtube.com/embed/$videoId?playsinline=1&autoplay=1&rel=0"
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
            referrerpolicy="strict-origin-when-cross-origin"
            allowfullscreen></iframe>
    </body>
    </html>
""".trimIndent()

@Composable
fun WorkoutThumbnail(url: String?, modifier: Modifier = Modifier) {
    val placeholder = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ApexColors.main.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = ApexColors.main, modifier = Modifier.size(22.dp))
        }
    }

    Box(modifier = modifier) {
        if (url.isNullOrBlank()) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { placeholder() },
                error = { placeholder() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
