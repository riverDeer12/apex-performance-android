package software.rdd.apexperformance.ui.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.mapError
import software.rdd.apexperformance.model.Client
import software.rdd.apexperformance.model.ClientPlan
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScreenHeader
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SettingsRow
import software.rdd.apexperformance.ui.components.TintedButton
import software.rdd.apexperformance.ui.components.ToolbarIcon
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

class ClientsScreen : Screen() {

    private var clients by mutableStateOf<List<Client>>(emptyList())
    private var isLoading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var searchText by mutableStateOf("")
    // null shows clients of all plans.
    private var planFilter by mutableStateOf<ClientPlan?>(null)
    private var showPlanMenu by mutableStateOf(false)

    private val filteredClients: List<Client>
        get() {
            val q = searchText.trim().lowercase()
            val byPlan = planFilter?.let { plan -> clients.filter { it.plan == plan.value } } ?: clients
            if (q.isEmpty()) return byPlan
            return byPlan.filter { c ->
                "${c.firstName} ${c.lastName}".lowercase().contains(q) || c.email.orEmpty().lowercase().contains(q)
            }
        }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val focusManager = LocalFocusManager.current

        LaunchedEffect(Unit) { loadData() }

        ScrollScreen(
            topBar = {
                TopBar {
                    Box {
                        ToolbarIcon(
                            if (planFilter == null) Icons.Filled.FilterList else Icons.Filled.FilterListOff,
                            stringResource(R.string.plan)
                        ) { showPlanMenu = true }
                        DropdownMenu(
                            expanded = showPlanMenu,
                            onDismissRequest = { showPlanMenu = false },
                            containerColor = ApexColors.background
                        ) {
                            PlanMenuItem(stringResource(R.string.all_plans), planFilter == null) { planFilter = null }
                            ClientPlan.entries.forEach { plan ->
                                PlanMenuItem(stringResource(plan.title), planFilter == plan) { planFilter = plan }
                            }
                        }
                    }
                    ToolbarIcon(Icons.Filled.Add, stringResource(R.string.new_client)) {
                        navigator.push(CreateClientScreen())
                    }
                }
            },
            showLoading = isLoading && clients.isEmpty(),
            onRefresh = { loadData() }
        ) {
            ScreenHeader(stringResource(R.string.clients), stringResource(R.string.manage_your_clients))

            // Search
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApexColors.secondaryGroupedBackground)
                    .padding(vertical = 10.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TintedButton(text = null, icon = Icons.Filled.Search)
                PlainTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = stringResource(R.string.search_clients),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.weight(1f)
                )
                if (searchText.isNotEmpty()) {
                    TintedButton(text = null, icon = Icons.Filled.Close, contentDescription = stringResource(R.string.clear)) {
                        searchText = ""
                    }
                }
            }

            errorMessage?.let {
                Text(it, color = ApexColors.red, style = ApexText.body, modifier = Modifier.padding(horizontal = 20.dp))
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                val list = filteredClients
                Column {
                    list.forEach { client ->
                        ClientRow(client) {
                            focusManager.clearFocus()
                            navigator.push(ClientDetailsScreen(client))
                        }
                        if (client.id != list.last().id) RowDivider()
                    }
                }
            }
        }
    }

    @Composable
    private fun PlanMenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
        DropdownMenuItem(
            text = { Text(text) },
            onClick = {
                showPlanMenu = false
                onClick()
            },
            trailingIcon = if (selected) {
                { Icon(Icons.Filled.Check, contentDescription = null, tint = ApexColors.main) }
            } else null
        )
    }

    @Composable
    private fun ClientRow(client: Client, onClick: () -> Unit) {
        // Credits and, when known, the client's plan.
        val credits = stringResource(R.string.credits_remaining, client.credits ?: 0)
        val plan = ClientPlan.from(client.plan)?.let { stringResource(it.title) }

        SettingsRow(
            icon = Icons.Outlined.Person,
            iconTint = ApexColors.main,
            title = "${client.firstName} ${client.lastName}",
            subtitle = if (plan != null) "$credits · $plan" else credits,
            showChevron = true,
            badge = if (client.isOutOfCredits) stringResource(R.string.out_of_credits) else null,
            onClick = onClick
        )
    }

    private suspend fun loadData() {
        isLoading = true
        try {
            clients = ApiClient.get("clients")
            errorMessage = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = mapError(e)
        } finally {
            isLoading = false
        }
    }
}
