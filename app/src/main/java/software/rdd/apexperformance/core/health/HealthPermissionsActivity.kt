package software.rdd.apexperformance.core.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.util.AppLanguage
import software.rdd.apexperformance.ui.components.ApexPrimaryButton
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import software.rdd.apexperformance.ui.theme.ApexTheme

// Why the app reads Health Connect, with the privacy policy. Health Connect
// opens it from its permission screen ("Read privacy policy"), and Android 14+
// from Settings → Health Connect → App permissions → Apex Performance.
class HealthPermissionsActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ApexTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ApexColors.groupedBackground)
                        .systemBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        stringResource(R.string.health_rationale_title),
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = ApexColors.label
                    )
                    Text(stringResource(R.string.health_daily_connect_hint), style = ApexText.body, color = ApexColors.label)
                    Text(stringResource(R.string.health_rationale_heart_rate), style = ApexText.body, color = ApexColors.label)
                    Text(stringResource(R.string.health_rationale_daily), style = ApexText.body, color = ApexColors.label)
                    Text(stringResource(R.string.health_rationale_rest), style = ApexText.body, color = ApexColors.label)

                    val language = stringResource(R.string.app_language_code)
                    ApexPrimaryButton(
                        stringResource(R.string.privacy_policy),
                        icon = Icons.AutoMirrored.Filled.OpenInNew
                    ) {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl(language))))
                        } catch (e: android.content.ActivityNotFoundException) {
                            // No browser.
                        }
                    }
                    ApexPrimaryButton(stringResource(R.string.close), icon = null) { finish() }
                }
            }
        }
    }

    companion object {
        // The web privacy policy (same as on Google Play), in the app's language.
        fun privacyPolicyUrl(language: String): String {
            val suffix = when (language) {
                "hr", "it", "de" -> "_$language"
                else -> ""
            }
            return "https://apex-performance.fit/assets/documents/privacy_policy$suffix.html"
        }
    }
}
