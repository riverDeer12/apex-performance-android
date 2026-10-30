package software.rdd.apexperformance.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Straighten
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.text.format.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import software.rdd.apexperformance.core.util.NotificationRouter
import software.rdd.apexperformance.model.IsoInstant
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.Instant
import software.rdd.apexperformance.ui.components.ShowMoreItems

// Push notification kept for the user (docs/NOTIFICATIONS_API.md in the backend).
@Serializable
data class AppNotification(
    val id: String,
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val data: Map<String, String> = emptyMap(),
    val createdAt: IsoInstant,
    val readAt: IsoInstant? = null
) {
    val isRead: Boolean get() = readAt != null

    val icon: ImageVector
        get() = when (type) {
            "appointment_request", "appointment_updated" -> Icons.Outlined.CalendarMonth
            "body_measurement" -> Icons.Outlined.Straighten
            "client_goal" -> Icons.Outlined.GpsFixed
            "monthly_review", "monthly_review_reminder" -> Icons.Outlined.Description
            "training" -> Icons.Outlined.FitnessCenter
            else -> Icons.Outlined.Notifications
        }
}

// Notifications of the logged user and the unread count for the bell.
object NotificationInbox {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var notifications by mutableStateOf<List<AppNotification>>(emptyList())
        private set
    var unreadCount by mutableStateOf(0)
        private set
    var hasLoaded by mutableStateOf(false)
        private set

    @Serializable
    private data class CountResponse(val count: Int = 0)

    // Fire and forget, e.g. when a push arrives or the app comes back.
    fun refreshCountLater() {
        if (AuthManager.token == null) return
        scope.launch { refreshCount() }
    }

    suspend fun refreshCount() {
        runCatching { ApiClient.get<CountResponse>("notifications/unread-count") }
            .onSuccess { unreadCount = it.count }
    }

    suspend fun load() {
        notifications = ApiClient.get("notifications")
        hasLoaded = true
        unreadCount = notifications.count { !it.isRead }
    }

    suspend fun markRead(notification: AppNotification) {
        if (notification.isRead) return
        notifications = notifications.map { if (it.id == notification.id) it.copy(readAt = Instant.now()) else it }
        unreadCount = maxOf(0, unreadCount - 1)
        runCatching { ApiClient.requestData("notifications/${notification.id}/read", HttpMethod.POST) }
    }

    suspend fun markAllRead() {
        ApiClient.requestData("notifications/read-all", HttpMethod.POST)
        val now = Instant.now()
        notifications = notifications.map { if (it.readAt == null) it.copy(readAt = now) else it }
        unreadCount = 0
    }

    suspend fun clearRead() {
        ApiClient.requestData("notifications/read", HttpMethod.DELETE)
        notifications = notifications.filter { !it.isRead }
    }

    // After logout.
    fun reset() {
        notifications = emptyList()
        unreadCount = 0
        hasLoaded = false
    }
}

// Bell with the number of unread notifications; opens the list.
@Composable
fun NotificationBell() {
    val navigator = LocalNavigator.current
    LaunchedEffect(Unit) { NotificationInbox.refreshCount() }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable { navigator.push(NotificationsScreen()) },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.notifications), tint = ApexColors.main)
        val count = NotificationInbox.unreadCount
        if (count > 0) {
            Text(
                if (count > 99) "99+" else count.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 9.dp, y = (-9).dp)
                    .widthIn(min = 16.dp)
                    .background(ApexColors.red, RoundedCornerShape(50))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

// Notifications newest first; tapping one opens what it is about.
class NotificationsScreen : Screen() {

    private var isLoading by mutableStateOf(false)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        var menuOpen by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { load() }

        ScrollScreen(
            topBar = {
                TopBar(title = stringResource(R.string.notifications)) {
                    Box {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.notification_options),
                            tint = ApexColors.main,
                            modifier = Modifier
                                .padding(12.dp)
                                .clickable { menuOpen = true }
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mark_all_read)) },
                                enabled = NotificationInbox.unreadCount > 0,
                                onClick = {
                                    menuOpen = false
                                    launch { runCatching { NotificationInbox.markAllRead() }.onFailure(::showError) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_read_notifications), color = ApexColors.red) },
                                enabled = NotificationInbox.notifications.any { it.isRead },
                                onClick = {
                                    menuOpen = false
                                    launch { runCatching { NotificationInbox.clearRead() }.onFailure(::showError) }
                                }
                            )
                        }
                    }
                }
            },
            showLoading = isLoading && !NotificationInbox.hasLoaded,
            onRefresh = { load() }
        ) {
            val notifications = NotificationInbox.notifications
            CardView(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) {
                if (notifications.isEmpty() && NotificationInbox.hasLoaded) {
                    EmptyText(stringResource(R.string.no_notifications))
                } else {
                    Column {
                        ShowMoreItems(notifications) { notification ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        launch {
                                            NotificationInbox.markRead(notification)
                                            navigator.pop()
                                            NotificationRouter.handle(
                                                notification.type,
                                                notification.data["conversationId"],
                                                notification.data["trainingId"]
                                            )
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(ApexColors.main.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(notification.icon, contentDescription = null, tint = ApexColors.main, modifier = Modifier.size(18.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        notification.title,
                                        style = ApexText.subheadline.copy(
                                            fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold
                                        ),
                                        color = ApexColors.label
                                    )
                                    Text(notification.body, style = ApexText.footnote, color = ApexColors.secondaryLabel)
                                    Text(
                                        DateUtils.getRelativeTimeSpanString(notification.createdAt.toEpochMilli()).toString(),
                                        style = ApexText.caption,
                                        color = ApexColors.tertiaryLabel
                                    )
                                }
                                if (!notification.isRead) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(8.dp)
                                            .background(ApexColors.main, CircleShape)
                                    )
                                }
                            }
                            if (notification.id != notifications.last().id) RowDivider(startIndent = 44.dp)
                        }
                    }
                }
            }
        }
    }

    private suspend fun load() {
        isLoading = true
        try {
            NotificationInbox.load()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoading = false
        }
    }
}
