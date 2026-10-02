package software.rdd.apexperformance.ui.profile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.core.network.mapError
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.core.util.UiText
import software.rdd.apexperformance.model.ChangePasswordRequest
import software.rdd.apexperformance.model.ChangeUsernameRequest
import software.rdd.apexperformance.ui.components.FormSection
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.SaveAction
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

class ChangePasswordScreen : Screen() {

    private var newPassword by mutableStateOf("")
    private var confirmPassword by mutableStateOf("")
    private var isSaving by mutableStateOf(false)
    private var errorMessage by mutableStateOf<UiText?>(null)
    private var showNewPassword by mutableStateOf(false)
    private var showConfirmPassword by mutableStateOf(false)

    private val canSave: Boolean
        get() = !isSaving && newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && newPassword == confirmPassword

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(R.string.change_password)) {
                    SaveAction(enabled = canSave, isSaving = isSaving) { launch { save(navigator) } }
                }
            }
        ) {
            FormSection(header = stringResource(R.string.new_password)) {
                PasswordField(newPassword, { newPassword = it }, stringResource(R.string.enter_new_password), showNewPassword) {
                    showNewPassword = !showNewPassword
                }
            }
            FormSection(header = stringResource(R.string.confirm_password)) {
                PasswordField(confirmPassword, { confirmPassword = it }, stringResource(R.string.enter_confirm_password), showConfirmPassword) {
                    showConfirmPassword = !showConfirmPassword
                }
            }
            errorMessage?.let {
                FormSection { Text(it.resolve(), color = ApexColors.red, style = ApexText.body, modifier = Modifier.padding(vertical = 10.dp)) }
            }
        }
    }

    @Composable
    private fun PasswordField(value: String, onValueChange: (String) -> Unit, placeholder: String, isVisible: Boolean, onToggle: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlainTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            )
            IconButton(onClick = onToggle) {
                Icon(
                    if (isVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (isVisible) R.string.hide_password else R.string.show_password),
                    tint = ApexColors.main
                )
            }
        }
    }

    private suspend fun save(navigator: Navigator) {
        if (newPassword != confirmPassword) {
            errorMessage = UiText.Res(R.string.passwords_do_not_match)
            return
        }
        isSaving = true
        try {
            ApiClient.send("users/reset-password", HttpMethod.POST, ChangePasswordRequest(newPassword))
            ToastManager.show(R.string.password_changed_successfully, ToastType.SUCCESS)
            isSaving = false
            delay(800)
            navigator.pop()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = mapError(e)
            errorMessage = UiText.Raw(message)
            ToastManager.show(message, ToastType.ERROR)
        } finally {
            isSaving = false
        }
    }
}

class ChangeUsernameScreen : Screen() {

    private var newUsername by mutableStateOf("")
    private var isSaving by mutableStateOf(false)
    private var errorMessage by mutableStateOf<UiText?>(null)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(R.string.change_username)) {
                    SaveAction(enabled = !isSaving && newUsername.isNotBlank(), isSaving = isSaving) { launch { save(navigator) } }
                }
            }
        ) {
            FormSection(header = stringResource(R.string.new_username)) {
                PlainTextField(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    placeholder = stringResource(R.string.enter_new_username),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
            errorMessage?.let {
                FormSection { Text(it.resolve(), color = ApexColors.red, style = ApexText.body, modifier = Modifier.padding(vertical = 10.dp)) }
            }
        }
    }

    private suspend fun save(navigator: Navigator) {
        if (newUsername.isBlank()) {
            errorMessage = UiText.Res(R.string.username_cannot_be_empty)
            return
        }
        isSaving = true
        try {
            ApiClient.send("authentication/change-username", HttpMethod.POST, ChangeUsernameRequest(newUsername.trim()))
            ToastManager.show(R.string.username_changed_successfully, ToastType.SUCCESS)
            isSaving = false
            delay(800)
            navigator.pop()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = mapError(e)
            errorMessage = UiText.Raw(message)
            ToastManager.show(message, ToastType.ERROR)
        } finally {
            isSaving = false
        }
    }
}
