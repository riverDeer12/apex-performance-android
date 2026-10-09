package software.rdd.apexperformance.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.rdd.apexperformance.R
import software.rdd.apexperformance.ui.theme.ApexColors

// Animated logo shown over the app when it starts, like SplashView on iOS:
// the logo pulses and zooms out to reveal the app.
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scale = remember { Animatable(1f) }
    val opacity = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val reduceMotion = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f

        if (reduceMotion) {
            delay(300)
            opacity.animateTo(0f, tween(300))
            onFinished()
            return@LaunchedEffect
        }

        // Short pause so the first frame matches the launch screen.
        delay(150)

        // Logo "breathes in".
        scale.animateTo(0.88f, tween(350, easing = EaseInOut))

        // Logo zooms towards the user while the splash fades away.
        launch { scale.animateTo(6f, tween(450, easing = EaseIn)) }
        opacity.animateTo(0f, tween(450, easing = EaseIn))

        onFinished()
    }

    // Launch background: white, black in dark mode.
    val background = if (ApexColors.isDark) Color.Black else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(opacity.value)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painterResource(R.drawable.logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ApexColors.label),
            modifier = Modifier
                .size(width = 320.dp, height = 160.dp)
                .scale(scale.value)
        )
    }
}
