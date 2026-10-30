package software.rdd.apexperformance.ui.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.core.navigation.Navigator
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ChatStore
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.NotificationRouter
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.model.ChatApi
import software.rdd.apexperformance.model.ChatConversation
import software.rdd.apexperformance.model.ChatContact
import software.rdd.apexperformance.ui.components.ApexPrimaryButton
import software.rdd.apexperformance.ui.components.ApexScreenHeader
import software.rdd.apexperformance.ui.components.CardView
import software.rdd.apexperformance.ui.components.EmptyText
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.RowDivider
import software.rdd.apexperformance.ui.components.ScrollScreen
import software.rdd.apexperformance.ui.components.SmallProgress
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import software.rdd.apexperformance.ui.components.ShowMoreItems

// Chat, opened from the floating button: conversations between a coach and
// their clients, newest activity first. Either side can start a chat; only
// the coach can turn it off, then it stays read only for both.
class ChatListScreen(private val onClose: (() -> Unit)? = null) : Screen() {

    private var isStarting by mutableStateOf(false)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val isCoach = AuthManager.hasRole(Roles.COACH)

        // A tapped message push opens its conversation.
        LaunchedEffect(NotificationRouter.pendingConversationId) {
            val id = NotificationRouter.pendingConversationId ?: return@LaunchedEffect
            NotificationRouter.pendingConversationId = null
            navigator.popToRoot()
            navigator.push(ChatConversationScreen(id))
        }

        ScrollScreen(
            topBar = { TopBar(showBack = onClose != null || navigator.canPop, onBack = onClose) },
            onRefresh = { ChatStore.refresh() }
        ) {
            ApexScreenHeader(stringResource(R.string.chat_title), stringResource(R.string.chat_encrypted_info))

            ApexPrimaryButton(
                stringResource(R.string.chat_new),
                modifier = Modifier.padding(horizontal = 20.dp),
                icon = Icons.Filled.Add,
                isLoading = isStarting
            ) {
                navigator.push(
                    ChatContactListScreen(if (isCoach) R.string.chat_choose_client else R.string.chat_choose_coach) { contact ->
                        navigator.pop()
                        startChat(navigator, contact, isCoach)
                    }
                )
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                val conversations = ChatStore.conversations
                when {
                    conversations.isEmpty() && !ChatStore.hasLoaded ->
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            SmallProgress()
                        }
                    conversations.isEmpty() ->
                        EmptyText(stringResource(if (isCoach) R.string.chat_empty_coach else R.string.chat_empty_client))
                    else -> Column {
                        ShowMoreItems(conversations) { conversation ->
                            ConversationRow(conversation) { navigator.push(ChatConversationScreen(conversation.id)) }
                            if (conversation.id != conversations.last().id) RowDivider(startIndent = 60.dp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ConversationRow(conversation: ChatConversation, onClick: () -> Unit) {
        val other = conversation.otherParticipant(ChatStore.userId)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatAvatar(other.fullName)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        other.fullName,
                        style = ApexText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!conversation.isEnabled) {
                        Icon(
                            Icons.Outlined.PauseCircle,
                            contentDescription = stringResource(R.string.chat_disabled),
                            tint = ApexColors.secondaryLabel,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Box(modifier = Modifier.weight(1f))
                    conversation.lastMessageAt?.let {
                        Text(shortDate(it), style = ApexText.caption, color = ApexColors.secondaryLabel)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lastMessageText(conversation),
                        style = ApexText.caption,
                        color = ApexColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (conversation.unreadCount > 0) {
                        Text(
                            if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                            style = ApexText.caption.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = ApexColors.onAccent,
                            modifier = Modifier
                                .background(ApexColors.accent, RoundedCornerShape(50))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun lastMessageText(conversation: ChatConversation): String {
        val message = conversation.lastMessage ?: return stringResource(R.string.chat_no_messages)
        val content = when {
            message.isImage -> stringResource(if (message.attachment?.isGif == true) R.string.chat_gif else R.string.chat_photo)
            message.isWorkout -> stringResource(R.string.chat_shared_workout) +
                (message.workout?.let { ": " + it.name.localized } ?: "")
            else -> message.content ?: stringResource(R.string.chat_unreadable)
        }
        return if (message.senderUserId.equals(ChatStore.userId, ignoreCase = true)) {
            stringResource(R.string.chat_you) + ": " + content
        } else {
            content
        }
    }

    // Opens the chat with the contact, starting it when there is none yet.
    // An existing chat opens as it is, also when the coach turned it off.
    private fun startChat(navigator: Navigator, contact: ChatContact, isCoach: Boolean) {
        contact.conversationId?.let {
            navigator.push(ChatConversationScreen(it))
            return
        }
        launch {
            isStarting = true
            try {
                val conversation = if (isCoach) {
                    ChatApi.setConversation(contact.id, isEnabled = true)
                } else {
                    ChatApi.start(contact.id)
                }
                ChatStore.upsert(conversation)
                navigator.push(ChatConversationScreen(conversation.id))
            } catch (e: Exception) {
                showError(e)
            } finally {
                isStarting = false
            }
        }
    }
}

// Time today, date otherwise.
fun shortDate(instant: java.time.Instant): String {
    val zoned = instant.atZone(ZoneId.systemDefault())
    return if (zoned.toLocalDate() == LocalDate.now()) {
        zoned.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    } else {
        DateFormats.date(instant)
    }
}

// Round initials of a participant.
@Composable
fun ChatAvatar(name: String, size: Dp = 44.dp) {
    val initials = name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        modifier = Modifier
            .size(size)
            .background(ApexColors.accent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials.ifEmpty { "?" },
            style = ApexText.subheadline.copy(fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp),
            color = ApexColors.onAccent
        )
    }
}

// People the user can chat with (clients for a coach, coaches for a client);
// tapping one picks them.
class ChatContactListScreen(
    @StringRes private val title: Int,
    private val onPick: (ChatContact) -> Unit
) : Screen() {

    private var contacts by mutableStateOf<List<ChatContact>>(emptyList())
    private var searchText by mutableStateOf("")
    private var isLoading by mutableStateOf(false)

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) {
            if (contacts.isEmpty()) load()
        }

        ScrollScreen(
            topBar = { TopBar(title = stringResource(title)) },
            showLoading = isLoading && contacts.isEmpty()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .apexCard()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                PlainTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = stringResource(R.string.search),
                    textStyle = ApexText.subheadline,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            CardView(modifier = Modifier.padding(horizontal = 20.dp)) {
                val query = searchText.trim()
                val list = if (query.isEmpty()) contacts else contacts.filter { it.fullName.contains(query, ignoreCase = true) }
                if (list.isEmpty() && !isLoading) {
                    EmptyText(stringResource(R.string.chat_no_contacts))
                } else {
                    Column {
                        ShowMoreItems(list) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(contact) }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ChatAvatar(contact.fullName, size = 34.dp)
                                Text(contact.fullName, style = ApexText.body, modifier = Modifier.weight(1f))
                                if (contact.conversationId != null) {
                                    Icon(
                                        if (contact.isEnabled) Icons.Filled.Forum else Icons.Outlined.PauseCircle,
                                        contentDescription = null,
                                        tint = ApexColors.secondaryLabel,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (contact.id != list.last().id) RowDivider(startIndent = 46.dp)
                        }
                    }
                }
            }
        }
    }

    private suspend fun load() {
        isLoading = true
        try {
            contacts = ChatApi.contacts()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoading = false
        }
    }
}

// Round chat button floating over the tabs, with the unread count.
@Composable
fun ChatFloatingButton(unreadCount: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(8.dp, CircleShape)
                .background(ApexColors.accent, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Forum, contentDescription = stringResource(R.string.tab_chat), tint = ApexColors.onAccent)
        }
        if (unreadCount > 0) {
            Text(
                if (unreadCount > 99) "99+" else unreadCount.toString(),
                style = ApexText.caption.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(ApexColors.red, RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
