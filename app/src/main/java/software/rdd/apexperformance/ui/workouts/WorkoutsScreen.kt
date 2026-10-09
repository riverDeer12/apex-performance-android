package software.rdd.apexperformance.ui.workouts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.AppEnvironment
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.ImportWorkoutsResponse
import software.rdd.apexperformance.model.Workout
import software.rdd.apexperformance.model.deleteWorkout
import software.rdd.apexperformance.ui.components.ApexScreenHeader
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.Chevron
import software.rdd.apexperformance.ui.components.ConfirmDialog
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.ToolbarIcon
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.WorkoutThumbnail
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText

// Workout library. Clients open it from home, limited by their plan
// through `workoutFilter`, with their own title and subtitle.
class WorkoutsScreen(
    private val workoutFilter: ((Workout) -> Boolean)? = null,
    @StringRes private val title: Int = R.string.workouts,
    @StringRes private val subtitle: Int = R.string.workouts_subtitle
) : Screen() {

    private var workouts by mutableStateOf<List<Workout>>(emptyList())
    private var searchText by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var isImporting by mutableStateOf(false)
    private var hasLoaded = false
    // Workout picked for deletion from the long-press menu.
    private var workoutToDelete by mutableStateOf<Workout?>(null)

    // Same audience as the web: coaches and administrators manage workouts.
    private val canManageWorkouts: Boolean get() = !AuthManager.hasRole(Roles.CLIENT)

    private val filteredWorkouts: List<Workout>
        get() = workouts
            .filter { workoutFilter?.invoke(it) ?: true }
            .filter { it.matches(searchText) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.localized })

    // The template is served by the web app on the same host as the API.
    private val importTemplateUrl = "${AppEnvironment.webUrl}/assets/templates/workouts-import-template.xlsx"

    fun updateWorkout(updated: Workout) {
        workouts = workouts.map { if (it.id == updated.id) updated else it }
    }

    private fun removeWorkout(id: String) {
        workouts = workouts.filter { it.id != id }
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val context = LocalContext.current

        val fileImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) launch { importWorkouts(context, uri) }
        }

        LaunchedEffect(Unit) {
            if (!hasLoaded) {
                hasLoaded = true
                loadWorkouts()
            }
        }

        ScrollScreen(
            topBar = {
                TopBar {
                    if (canManageWorkouts) {
                        ToolbarIcon(Icons.Filled.Add, stringResource(R.string.new_workout)) {
                            navigator.push(WorkoutEditScreen { created -> workouts = workouts + created })
                        }
                    }
                }
            },
            showLoading = isLoading && workouts.isEmpty(),
            onRefresh = { loadWorkouts() }
        ) {
            ApexScreenHeader(stringResource(title), stringResource(subtitle))

            if (canManageWorkouts) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ImportButton(
                        text = stringResource(R.string.import_workouts),
                        icon = Icons.Outlined.FileDownload,
                        filled = true,
                        isLoading = isImporting,
                        modifier = Modifier.weight(1f)
                    ) { fileImporter.launch(arrayOf(XLSX_MIME_TYPE)) }

                    ImportButton(
                        text = stringResource(R.string.download_template),
                        icon = Icons.Outlined.Description,
                        filled = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(importTemplateUrl)))
                    }
                }
            }

            SearchField()

            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                val list = filteredWorkouts
                if (list.isEmpty() && !isLoading) {
                    EmptyText(stringResource(if (searchText.isEmpty()) R.string.no_workouts else R.string.no_workouts_found))
                } else {
                    Column {
                        list.forEach { workout ->
                            WorkoutRow(workout) {
                                navigator.push(
                                    WorkoutDetailScreen(
                                        workout,
                                        canManageWorkouts,
                                        onSaved = ::updateWorkout,
                                        onDeleted = { removeWorkout(workout.id) }
                                    )
                                )
                            }
                            if (workout.id != list.last().id) RowDivider(startIndent = 108.dp)
                        }
                    }
                }
            }
        }

        workoutToDelete?.let { workout ->
            ConfirmDialog(
                title = stringResource(R.string.delete_workout_question),
                message = stringResource(R.string.can_not_be_undone),
                confirmText = stringResource(R.string.delete),
                onConfirm = { launch { delete(workout) } },
                onDismiss = { workoutToDelete = null }
            )
        }
    }

    @Composable
    private fun ImportButton(
        text: String,
        icon: ImageVector,
        filled: Boolean,
        modifier: Modifier = Modifier,
        isLoading: Boolean = false,
        onClick: () -> Unit
    ) {
        // The main color is white in dark mode, so filled buttons use
        // the background colour (white / black) for their content.
        val content = if (!filled) ApexColors.main else if (ApexColors.isDark) Color.Black else Color.White
        Row(
            modifier = modifier
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (filled) ApexColors.main else ApexColors.main.copy(alpha = 0.12f))
                .clickable(enabled = !isLoading, onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            }
            Text(text, color = content, style = ApexText.body.copy(fontWeight = FontWeight.SemiBold))
        }
    }

    @Composable
    private fun SearchField() {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(44.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
                .clip(RoundedCornerShape(12.dp))
                .background(ApexColors.background)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = ApexColors.secondaryLabel)
            PlainTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = stringResource(R.string.search_workouts),
                modifier = Modifier.weight(1f)
            )
            if (searchText.isNotEmpty()) {
                Icon(
                    Icons.Filled.Cancel,
                    contentDescription = stringResource(R.string.clear),
                    tint = ApexColors.secondaryLabel,
                    modifier = Modifier.clickable { searchText = "" }
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun WorkoutRow(workout: Workout, onClick: () -> Unit) {
        var showMenu by remember { mutableStateOf(false) }

        Box {
            WorkoutRowContent(
                workout,
                Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = if (canManageWorkouts) ({ showMenu = true }) else null
                )
            )
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = ApexColors.background
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_workout), color = ApexColors.red) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = ApexColors.red) },
                    onClick = {
                        showMenu = false
                        workoutToDelete = workout
                    }
                )
            }
        }
    }

    @Composable
    private fun WorkoutRowContent(workout: Workout, modifier: Modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(modifier)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                WorkoutThumbnail(workout.thumbnailUrl)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    workout.name.localized,
                    style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (workout.workoutTypes.isNotEmpty()) {
                    Text(
                        workout.workoutTypes.joinToString(", ") { it.name.localized },
                        style = ApexText.caption,
                        color = ApexColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Chevron()
        }
    }

    private suspend fun delete(workout: Workout) {
        try {
            deleteWorkout(workout.id)
            removeWorkout(workout.id)
            ToastManager.show(R.string.workout_deleted_successfully, ToastType.SUCCESS)
        } catch (e: Exception) {
            showError(e)
        }
    }

    private suspend fun loadWorkouts() {
        isLoading = true
        try {
            workouts = ApiClient.get("workouts")
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoading = false
        }
    }

    // Uploads the file only; the API validates and saves rows in a background
    // job and emails the result to the user, same as the web import.
    private suspend fun importWorkouts(context: Context, uri: Uri) {
        isImporting = true
        try {
            val (fileName, bytes) = withContext(Dispatchers.IO) {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: "workouts.xlsx"
                val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("Can't read file")
                name to data
            }

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, bytes.toRequestBody(XLSX_MIME_TYPE.toMediaType()))
                .build()

            val data = ApiClient.requestData("workouts/import", HttpMethod.POST, body)
            ApiClient.decode<ImportWorkoutsResponse>(data)

            ToastManager.show(R.string.workouts_import_started, ToastType.SUCCESS)
        } catch (e: Exception) {
            showError(e)
        } finally {
            isImporting = false
        }
    }

    private companion object {
        const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
