package software.rdd.apexperformance.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.core.network.isCancellation
import software.rdd.apexperformance.core.network.mapError
import software.rdd.apexperformance.ui.components.FormSection
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.SmallProgress
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

@Serializable
private data class ForgotPasswordRequest(val email: String)

// Sends the password reset email, same as "Forgot password?" on the web.
// The email links to the web app where the new password is set.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordSheet(onDismiss: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isSent by rememberSaveable { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val trimmedEmail = email.trim()
    val isValid = trimmedEmail.contains("@") && trimmedEmail.contains(".")

    fun send() {
        if (!isValid || isSending) return
        scope.launch {
            isSending = true
            errorMessage = null
            try {
                // The API answers the same whether the email exists or not.
                ApiClient.send("authentication/forgot-password", HttpMethod.POST, ForgotPasswordRequest(trimmedEmail))
                isSent = true
            } catch (e: Exception) {
                if (!e.isCancellation) errorMessage = mapError(e)
            } finally {
                isSending = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ApexColors.groupedBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Inline title with a close button, like the sheet's navigation bar on iOS.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(stringResource(R.string.close), color = ApexColors.main, style = ApexText.body)
                }
                Text(
                    stringResource(R.string.forgot_password),
                    style = ApexText.navigationTitle,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (isSent) {
                FormSection {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.MarkEmailUnread,
                            contentDescription = null,
                            tint = ApexColors.main,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(stringResource(R.string.forgot_password_sent), style = ApexText.body)
                    }
                }
            } else {
                FormSection(
                    footer = {
                        val error = errorMessage
                        if (error != null) {
                            Text(error, style = ApexText.footnote, color = ApexColors.red)
                        } else {
                            Text(
                                stringResource(R.string.forgot_password_hint),
                                style = ApexText.footnote,
                                color = ApexColors.secondaryLabel
                            )
                        }
                    }
                ) {
                    PlainTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = stringResource(R.string.email),
                        enabled = !isSending,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Send,
                            autoCorrectEnabled = false
                        ),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .semantics { contentType = ContentType.EmailAddress }
                    )
                }

                FormSection {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isValid && !isSending) { send() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSending) {
                            SmallProgress()
                        } else {
                            Text(
                                stringResource(R.string.send_reset_link),
                                style = ApexText.body.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isValid) ApexColors.main else ApexColors.tertiaryLabel
                            )
                        }
                    }
                }
            }
        }
    }
}
