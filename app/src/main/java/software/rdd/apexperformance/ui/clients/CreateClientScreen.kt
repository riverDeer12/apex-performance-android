package software.rdd.apexperformance.ui.clients

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.ClientPlan
import software.rdd.apexperformance.model.Coach
import software.rdd.apexperformance.model.CreateClientRequest
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.EditableRow
import software.rdd.apexperformance.ui.components.LoadingRow
import software.rdd.apexperformance.ui.components.MenuPicker
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.SaveAction
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.ToggleRow
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.theme.ApexColors

class CreateClientScreen : Screen() {

    private var firstName by mutableStateOf("")
    private var lastName by mutableStateOf("")
    private var email by mutableStateOf("")
    private var phone by mutableStateOf("")
    private var credits by mutableStateOf("")
    private var plan by mutableStateOf(ClientPlan.PRIVATE_COACHING)
    private var isSaving by mutableStateOf(false)
    private var isLoadingCoaches by mutableStateOf(false)
    private var coaches by mutableStateOf<List<Coach>>(emptyList())
    private var selectedCoaches by mutableStateOf<List<String>>(emptyList())
    private var hasLoaded = false

    // API always adds the coach who creates the client as
    // the client's coach, so a coach doesn't pick coaches.
    private val canSelectCoaches: Boolean get() = !AuthManager.hasRole(Roles.COACH)

    private val isValid: Boolean get() = firstName.isNotBlank() && lastName.isNotBlank()

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        LaunchedEffect(Unit) {
            if (canSelectCoaches && !hasLoaded) {
                hasLoaded = true
                loadCoaches()
            }
        }

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(R.string.new_client)) {
                    SaveAction(enabled = isValid && !isSaving, isSaving = isSaving) { launch { save(navigator) } }
                }
            }
        ) {
            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.personal_info)) {
                Column {
                    EditableRow(stringResource(R.string.first_name)) { Field(firstName, { firstName = it }, stringResource(R.string.first_name)) }
                    HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                    EditableRow(stringResource(R.string.last_name)) { Field(lastName, { lastName = it }, stringResource(R.string.last_name)) }
                }
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.contact)) {
                Column {
                    EditableRow(stringResource(R.string.email)) { Field(email, { email = it }, stringResource(R.string.email), KeyboardType.Email) }
                    HorizontalDivider(color = ApexColors.separator, thickness = 0.5.dp)
                    EditableRow(stringResource(R.string.mobile_phone)) { Field(phone, { phone = it }, stringResource(R.string.mobile_phone), KeyboardType.Phone) }
                }
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.credits)) {
                EditableRow(stringResource(R.string.appointments_left)) {
                    Field(credits, { value -> credits = value.filter { it.isDigit() } }, "0", KeyboardType.Number)
                }
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.plan)) {
                MenuPicker(
                    label = stringResource(R.string.plan),
                    selectedText = stringResource(plan.title),
                    options = ClientPlan.entries,
                    optionText = { stringResource(it.title) },
                    onSelect = { plan = it }
                )
            }

            if (canSelectCoaches) {
                CardView(modifier = Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.select_coaches)) {
                    if (isLoadingCoaches) LoadingRow(stringResource(R.string.loading_coaches))
                    coaches.forEach { coach ->
                        ToggleRow(
                            title = coach.fullName ?: stringResource(R.string.unknown_coach),
                            checked = coach.id in selectedCoaches,
                            enabled = !isLoadingCoaches
                        ) { isOn ->
                            selectedCoaches = if (isOn) selectedCoaches + coach.id else selectedCoaches - coach.id
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RowScope.Field(value: String, onValueChange: (String) -> Unit, placeholder: String, keyboardType: KeyboardType = KeyboardType.Text) {
        PlainTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            textAlign = TextAlign.End,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = keyboardType == KeyboardType.Text),
            modifier = Modifier.weight(1f)
        )
    }

    private suspend fun loadCoaches() {
        isLoadingCoaches = true
        try {
            coaches = ApiClient.get("coaches/all")
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoadingCoaches = false
        }
    }

    private suspend fun save(navigator: Navigator) {
        isSaving = true
        try {
            val request = CreateClientRequest(
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                email = email.ifEmpty { null },
                phone = phone.ifEmpty { null },
                credits = credits.toIntOrNull(),
                coaches = if (canSelectCoaches) selectedCoaches else emptyList(),
                plan = plan.value
            )
            ApiClient.send("clients", HttpMethod.POST, request)
            ToastManager.show(R.string.client_created_successfully, ToastType.SUCCESS)
            isSaving = false
            delay(800)
            navigator.pop()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isSaving = false
        }
    }
}
