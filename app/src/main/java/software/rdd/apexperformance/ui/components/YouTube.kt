package software.rdd.apexperformance.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.SelfImprovement
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.SubcomposeAsyncImage
import software.rdd.apexperformance.model.WorkoutCategory
import software.rdd.apexperformance.ui.theme.ApexColors

object YouTubeVideo {
    // Same link formats the API accepts: watch?v=, youtu.be/, shorts/, embed/ and live/.
    private val pattern = Regex(
        """(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\.be/)([A-Za-z0-9_-]{6,})""",
        RegexOption.IGNORE_CASE
    )

    fun id(url: String?): String? = url?.let { pattern.find(it)?.groupValues?.get(1) }
}

// Plays a YouTube video inline through YouTube's embedded player. Its
// fullscreen button shows the video over the whole screen in landscape,
// until the button, back or the system's back gesture closes it.
@Composable
fun YouTubePlayer(videoId: String, modifier: Modifier = Modifier) {
    // YouTube rejects embeds without a referrer (error 153),
    // so the player page is loaded under the app's own domain.
    WebPlayer(html = html(videoId), baseUrl = EMBED_ORIGIN, modifier = modifier)
}

// A web page with a player (the YouTube embed) whose fullscreen requests are
// shown over the activity.
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebPlayer(html: String, baseUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fullscreen = remember { FullscreenVideo(context.findActivity()) }
    val webView = remember(html) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(AndroidColor.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // The player's fullscreen button asks the web view for a custom
            // view; without these callbacks the button does nothing.
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    fullscreen.show(view, callback)
                }

                override fun onHideCustomView() {
                    fullscreen.hide()
                }
            }
            loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            fullscreen.hide()
            webView.destroy()
        }
    }

    AndroidView(factory = { webView }, modifier = modifier)
}

// The web view's fullscreen video over the activity: system bars hidden and
// landscape (turning with the phone) while it is shown.
private class FullscreenVideo(private val activity: Activity?) {
    private var view: View? = null
    // Back leaves the fullscreen video, not the screen: added while it is
    // shown, so it comes before the screens' own back handling.
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = exit()
    }
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    fun show(view: View, callback: WebChromeClient.CustomViewCallback) {
        val activity = activity
        if (activity == null || this.view != null) {
            callback.onCustomViewHidden()
            return
        }
        this.view = view
        this.callback = callback
        view.setBackgroundColor(AndroidColor.BLACK)
        (activity.window.decorView as ViewGroup).addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        (activity as? ComponentActivity)?.onBackPressedDispatcher?.addCallback(backCallback)
    }

    // Closed by the player (its button) or when the screen goes away.
    fun hide() {
        val view = view ?: return
        this.view = null
        callback = null
        backCallback.remove()
        activity?.let { activity ->
            (view.parent as? ViewGroup)?.removeView(view)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = previousOrientation
        }
    }

    // Closed from the app (back): the player is told so it leaves fullscreen too.
    fun exit() {
        val callback = callback
        hide()
        callback?.onCustomViewHidden()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
    <iframe src="https://www.youtube.com/embed/$videoId?playsinline=1&autoplay=1&rel=0&fs=1"
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
            referrerpolicy="strict-origin-when-cross-origin"
            allowfullscreen></iframe>
    </body>
    </html>
""".trimIndent()

@Composable
fun WorkoutThumbnail(url: String?, modifier: Modifier = Modifier, category: WorkoutCategory? = null) {
    // Without a picture: an icon on a soft colour of the workout's category.
    val (icon, tint) = when (category) {
        WorkoutCategory.MOBILITY_FLEXIBILITY -> Icons.Outlined.SelfImprovement to ApexColors.teal
        WorkoutCategory.STRENGTH_CONDITIONING -> Icons.Filled.FitnessCenter to ApexColors.orange
        null -> Icons.Filled.FitnessCenter to ApexColors.accent
    }
    val placeholder = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
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
