package software.rdd.apexperformance.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.Profile
import software.rdd.apexperformance.model.UpdateProfileRequest
import software.rdd.apexperformance.ui.components.ConfirmDialog
import software.rdd.apexperformance.ui.components.FormSection
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.ProfilePicture
import software.rdd.apexperformance.ui.components.SaveAction
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Called with the latest profile after saving data or changing the picture.
class EditProfileScreen(
    profile: Profile,
    private val onProfileChanged: (Profile) -> Unit
) : Screen() {

    private var profile by mutableStateOf(profile)
    private var firstName by mutableStateOf(profile.firstName.orEmpty())
    private var lastName by mutableStateOf(profile.lastName.orEmpty())
    private var phone by mutableStateOf(profile.phone.orEmpty())
    private var email by mutableStateOf(profile.email)
    private var isSaving by mutableStateOf(false)
    private var isUpdatingPicture by mutableStateOf(false)
    private var showRemovePictureDialog by mutableStateOf(false)

    private val isValid: Boolean
        get() = email.isNotBlank() &&
            (!profile.hasPersonalData || (firstName.isNotBlank() && lastName.isNotBlank())) &&
            (!profile.hasPhone || phone.isNotBlank())

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val context = LocalContext.current

        val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) launch { uploadPicture(context, uri) }
        }

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(R.string.edit_profile)) {
                    SaveAction(enabled = isValid && !isSaving, isSaving = isSaving) { launch { save(navigator) } }
                }
            }
        ) {
            FormSection {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ProfilePicture(profile, size = 96.dp)
                        if (isUpdatingPicture) CircularProgressIndicator(color = ApexColors.main)
                    }

                    TextButton(
                        enabled = !isUpdatingPicture,
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Text(
                            stringResource(if (profile.hasProfilePicture) R.string.change_profile_picture else R.string.upload_profile_picture),
                            color = ApexColors.main,
                            style = ApexText.body
                        )
                    }

                    if (profile.hasProfilePicture) {
                        TextButton(enabled = !isUpdatingPicture, onClick = { showRemovePictureDialog = true }) {
                            Text(stringResource(R.string.remove_profile_picture), color = ApexColors.red, style = ApexText.body)
                        }
                    }
                }
            }

            if (profile.hasPersonalData) {
                FormSection(header = stringResource(R.string.personal_info)) {
                    Field(firstName, { firstName = it }, stringResource(R.string.first_name))
                    Divider()
                    Field(lastName, { lastName = it }, stringResource(R.string.last_name))
                    if (profile.hasPhone) {
                        Divider()
                        Field(phone, { phone = it }, stringResource(R.string.mobile_phone), KeyboardType.Phone)
                    }
                }
            }

            FormSection(header = stringResource(R.string.contact)) {
                Field(email, { email = it }, stringResource(R.string.email), KeyboardType.Email)
            }
        }

        if (showRemovePictureDialog) {
            ConfirmDialog(
                title = stringResource(R.string.remove_profile_picture_question),
                message = null,
                confirmText = stringResource(R.string.remove_profile_picture),
                onConfirm = { launch { removePicture() } },
                onDismiss = { showRemovePictureDialog = false }
            )
        }
    }

    @Composable
    private fun Divider() = HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)

    @Composable
    private fun Field(value: String, onValueChange: (String) -> Unit, placeholder: String, keyboardType: KeyboardType = KeyboardType.Text) {
        PlainTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = keyboardType == KeyboardType.Text),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )
    }

    private suspend fun save(navigator: Navigator) {
        isSaving = true
        try {
            val request = UpdateProfileRequest(
                email = email.trim(),
                firstName = if (profile.hasPersonalData) firstName.trim() else null,
                lastName = if (profile.hasPersonalData) lastName.trim() else null,
                phone = if (profile.hasPhone) phone.trim() else null
            )
            val updated: Profile = ApiClient.request("profile", HttpMethod.PUT, request)
            profile = updated
            onProfileChanged(updated)
            ToastManager.show(R.string.profile_updated_successfully, ToastType.SUCCESS)
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }

    private suspend fun uploadPicture(context: Context, uri: Uri) {
        isUpdatingPicture = true
        try {
            val jpeg = withContext(Dispatchers.Default) { resizedJpeg(context, uri) }
            if (jpeg == null) {
                ToastManager.show(R.string.invalid_profile_picture, ToastType.ERROR)
                return
            }
            if (jpeg.size > MAX_UPLOADED_PICTURE_BYTES) {
                ToastManager.show(R.string.profile_picture_too_large, ToastType.ERROR)
                return
            }

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "profile-picture.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
                .build()
            ApiClient.requestData("profile/picture", HttpMethod.PUT, body)

            reloadProfile()
            ToastManager.show(R.string.profile_picture_updated_successfully, ToastType.SUCCESS)
        } catch (e: Exception) {
            showError(e)
        } finally {
            isUpdatingPicture = false
        }
    }

    private suspend fun removePicture() {
        isUpdatingPicture = true
        try {
            ApiClient.requestData("profile/picture", HttpMethod.DELETE)
            reloadProfile()
            ToastManager.show(R.string.profile_picture_removed_successfully, ToastType.SUCCESS)
        } catch (e: Exception) {
            showError(e)
        } finally {
            isUpdatingPicture = false
        }
    }

    // Picture changes are saved right away, so the profile screen
    // gets the new picture even if the form is not saved.
    private suspend fun reloadProfile() {
        try {
            val updated: Profile = ApiClient.get("profile")
            profile = updated
            onProfileChanged(updated)
        } catch (e: Exception) {
            showError(e)
        }
    }

    private companion object {
        // Same limits as the web app: longer side at most 512 px,
        // API accepts pictures up to 2 MB.
        const val PICTURE_MAX_DIMENSION = 512
        const val MAX_UPLOADED_PICTURE_BYTES = 2 * 1024 * 1024

        /** Scale picture down so the longer side is at most 512 px and
         *  encode it as JPEG. Transparent areas are filled with white. */
        fun resizedJpeg(context: Context, uri: Uri): ByteArray? {
            val source = runCatching { decode(context, uri) }.getOrNull() ?: return null
            if (source.width <= 0 || source.height <= 0) return null

            val scale = min(1f, PICTURE_MAX_DIMENSION.toFloat() / max(source.width, source.height))
            val width = (source.width * scale).roundToInt().coerceAtLeast(1)
            val height = (source.height * scale).roundToInt().coerceAtLeast(1)

            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(output).apply {
                drawColor(Color.WHITE)
                drawBitmap(source, null, Rect(0, 0, width, height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            }

            return ByteArrayOutputStream().use { stream ->
                output.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                stream.toByteArray()
            }
        }

        // Applies the photo's rotation, so portrait photos aren't sideways.
        private fun decode(context: Context, uri: Uri): Bitmap =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
    }
}
