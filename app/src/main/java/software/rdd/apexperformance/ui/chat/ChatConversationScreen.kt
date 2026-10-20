package software.rdd.apexperformance.ui.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.navigation.Screen
import software.rdd.apexperformance.core.navigation.launch
import software.rdd.apexperformance.core.navigation.showError
import software.rdd.apexperformance.core.network.ChatStore
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.core.util.Roles
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.ChatApi
import software.rdd.apexperformance.model.ChatMessage
import software.rdd.apexperformance.ui.components.ConfirmDialog
import software.rdd.apexperformance.ui.components.PlainTextField
import software.rdd.apexperformance.ui.components.SmallProgress
import software.rdd.apexperformance.ui.components.TopBar
import software.rdd.apexperformance.ui.components.apexCard
import software.rdd.apexperformance.ui.theme.ApexColors
import software.rdd.apexperformance.ui.theme.ApexText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.Image
import androidx.compose.ui.platform.LocalContext
import software.rdd.apexperformance.core.navigation.LocalNavigator
import software.rdd.apexperformance.model.Workout

// Messages of one conversation with the field to write. New messages come
// live from the chat hub; "load older" fetches earlier ones. The coach can
// turn the chat off, then nobody can write but messages are kept.
class ChatConversationScreen(private val conversationId: String) : Screen() {

    private val messages = mutableStateListOf<ChatMessage>()
    private var draft by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var isLoadingOlder by mutableStateOf(false)
    private var hasOlder by mutableStateOf(false)
    private var isSending by mutableStateOf(false)
    private var isUpdating by mutableStateOf(false)
    private var showReport by mutableStateOf(false)
    private var showBlockDialog by mutableStateOf(false)
    private var reportReason by mutableStateOf("")
    private var hasLoaded = false
    // Message to bring into view: the newest after loading or sending.
    private var scrollToEnd by mutableStateOf(0)

    private val conversation get() = ChatStore.conversations.firstOrNull { it.id == conversationId }
    private fun isOwn(message: ChatMessage) = message.senderUserId.equals(ChatStore.userId, ignoreCase = true)

    @Composable
    override fun Content() {
        val conversation = conversation
        val isCoach = AuthManager.hasRole(Roles.COACH)
        val listState = rememberLazyListState()

        DisposableEffect(Unit) {
            attach()
            onDispose { detach() }
        }

        LaunchedEffect(Unit) {
            if (!hasLoaded) {
                hasLoaded = true
                loadNewest()
            }
        }

        LaunchedEffect(scrollToEnd) {
            val last = listState.layoutInfo.totalItemsCount - 1
            if (messages.isNotEmpty() && last >= 0) listState.animateScrollToItem(last)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ApexColors.groupedBackground)
                .imePadding()
        ) {
            TopBar(title = conversation?.otherParticipant(ChatStore.userId)?.fullName) {
                if (conversation != null) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        if (isUpdating) {
                            Box(modifier = Modifier.padding(12.dp)) { SmallProgress() }
                        } else {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.chat_options),
                                tint = ApexColors.main,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .clickable { menuOpen = true }
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            // A chat the client blocked can't be turned on by the coach.
                            if (isCoach && !conversation.isBlockedByClient) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_enabled)) },
                                    trailingIcon = {
                                        Switch(
                                            checked = conversation.isEnabled,
                                            onCheckedChange = null,
                                            colors = SwitchDefaults.colors(checkedTrackColor = ApexColors.accent)
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        setEnabled(!conversation.isEnabled)
                                    }
                                )
                            }
                            if (!isCoach) {
                                if (conversation.isBlockedByClient) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_unblock)) },
                                        onClick = {
                                            menuOpen = false
                                            setBlocked(false)
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_block), color = ApexColors.red) },
                                        onClick = {
                                            menuOpen = false
                                            showBlockDialog = true
                                        }
                                    )
                                }
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_report), color = ApexColors.red) },
                                onClick = {
                                    menuOpen = false
                                    reportReason = ""
                                    showReport = true
                                }
                            )
                        }
                    }
                }
            }

            if (showReport) {
                ReportDialog()
            }

            if (showBlockDialog) {
                ConfirmDialog(
                    title = stringResource(R.string.chat_block_question),
                    message = stringResource(R.string.chat_block_info),
                    confirmText = stringResource(R.string.chat_block),
                    onConfirm = { setBlocked(true) },
                    onDismiss = { showBlockDialog = false }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasOlder) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                if (isLoadingOlder) {
                                    SmallProgress()
                                } else {
                                    Text(
                                        stringResource(R.string.chat_load_older),
                                        style = ApexText.footnote.copy(fontWeight = FontWeight.SemiBold),
                                        color = ApexColors.main,
                                        modifier = Modifier.clickable { loadOlder() }
                                    )
                                }
                            }
                        }
                    }

                    if (messages.isEmpty() && !isLoading) {
                        item {
                            Text(
                                stringResource(R.string.chat_start_conversation),
                                style = ApexText.subheadline,
                                color = ApexColors.secondaryLabel,
                                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    val lastOwnId = messages.lastOrNull { isOwn(it) }?.id
                    itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                        Column {
                            val previous = messages.getOrNull(index - 1)
                            if (previous == null || day(previous.createdAt) != day(message.createdAt)) {
                                Text(
                                    DateFormats.date(message.createdAt),
                                    style = ApexText.caption.copy(fontWeight = FontWeight.SemiBold),
                                    color = ApexColors.secondaryLabel,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Bubble(message, showsStatus = message.id == lastOwnId)
                        }
                    }
                }

                if (isLoading && messages.isEmpty()) {
                    Box(modifier = Modifier.align(Alignment.Center)) { SmallProgress(size = 28.dp) }
                }
            }

            InputBar(
                isEnabled = conversation?.isEnabled != false,
                disabledText = when {
                    conversation?.isBlockedByClient != true -> R.string.chat_disabled_info
                    isCoach -> R.string.chat_blocked_by_client
                    else -> R.string.chat_blocked_by_you
                }
            )
        }
    }

    @Composable
    private fun Bubble(message: ChatMessage, showsStatus: Boolean) {
        val own = isOwn(message)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (own) 48.dp else 0.dp, end = if (own) 0.dp else 48.dp),
            horizontalAlignment = if (own) Alignment.End else Alignment.Start
        ) {
            val attachment = message.attachment
            if (message.isImage && attachment != null) {
                ChatImageBubble(attachment, conversationId)
            } else if (message.isWorkout) {
                ChatWorkoutCard(message.workout)
            }

            // Text, or the caption of a picture or workout.
            if (!(message.isImage || message.isWorkout) || message.text != null) {
                SelectionContainer {
                    Text(
                        message.content ?: stringResource(R.string.chat_unreadable),
                        style = ApexText.subheadline.copy(fontStyle = if (message.content == null) FontStyle.Italic else FontStyle.Normal),
                        color = if (own) ApexColors.onAccent else ApexColors.label,
                        modifier = Modifier
                            .padding(top = if (message.isImage || message.isWorkout) 4.dp else 0.dp)
                            .widthIn(min = 40.dp)
                            .background(if (own) ApexColors.accent else ApexColors.card, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            val time = message.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            val status = if (own && showsStatus) {
                " · " + stringResource(if (message.readAt == null) R.string.chat_sent else R.string.chat_read)
            } else ""
            Text(time + status, style = ApexText.caption, color = ApexColors.secondaryLabel, modifier = Modifier.padding(top = 2.dp))
        }
    }

    @Composable
    private fun InputBar(isEnabled: Boolean, @StringRes disabledText: Int) {
        if (!isEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ApexColors.card)
                    .navigationBarsPadding()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.PauseCircle, contentDescription = null, tint = ApexColors.secondaryLabel, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(disabledText),
                    style = ApexText.footnote,
                    color = ApexColors.secondaryLabel,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            return
        }

        val canSend = draft.isNotBlank() && !isSending
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) sendPicture(context, uri)
        }
        var attachMenuOpen by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Picture (photo or GIF) for everyone, a workout for coaches.
            Box {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(ApexColors.card, CircleShape)
                        .clickable(enabled = !isSending) { attachMenuOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_attach), tint = ApexColors.main)
                }
                DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { attachMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_photo_or_gif)) },
                        leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                        onClick = {
                            attachMenuOpen = false
                            pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    if (AuthManager.hasRole(Roles.COACH)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_share_workout)) },
                            leadingIcon = { Icon(Icons.Filled.FitnessCenter, contentDescription = null) },
                            onClick = {
                                attachMenuOpen = false
                                navigator.push(ChatWorkoutPickerScreen { workout -> shareWorkout(workout) })
                            }
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(ApexColors.card, RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                PlainTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = stringResource(R.string.chat_placeholder),
                    singleLine = false,
                    textStyle = ApexText.subheadline,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(ApexColors.accent.copy(alpha = if (canSend) 1f else 0.4f), CircleShape)
                    .clickable(enabled = canSend) { send() },
                contentAlignment = Alignment.Center
            ) {
                if (isSending) {
                    SmallProgress(color = ApexColors.onAccent)
                } else {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.chat_send), tint = ApexColors.onAccent)
                }
            }
        }
    }

    private fun day(instant: Instant) = instant.atZone(ZoneId.systemDefault()).toLocalDate()

    // MARK: Live events

    private fun attach() {
        ChatStore.openConversationId = conversationId
        ChatStore.onMessage = { message ->
            if (message.conversationId == conversationId) {
                append(message)
                if (!isOwn(message)) launch { markRead() }
            }
        }
        ChatStore.onMessagesRead = { event ->
            if (event.conversationId == conversationId && !event.readerUserId.equals(ChatStore.userId, ignoreCase = true)) {
                for (index in messages.indices) {
                    val message = messages[index]
                    if (isOwn(message) && message.readAt == null && !message.createdAt.isAfter(event.readAt)) {
                        messages[index] = message.copy(readAt = event.readAt)
                    }
                }
            }
        }
        ChatStore.onReconnected = { launch { loadNewest() } }
    }

    private fun detach() {
        // Another conversation may already be open.
        if (ChatStore.openConversationId != conversationId) return
        ChatStore.openConversationId = null
        ChatStore.onMessage = null
        ChatStore.onMessagesRead = null
        ChatStore.onReconnected = null
    }

    private fun append(message: ChatMessage) {
        if (messages.any { it.id == message.id }) return
        messages.add(message)
        scrollToEnd++
    }

    // MARK: API

    private suspend fun loadNewest() {
        isLoading = true
        try {
            val newest = ChatApi.messages(conversationId, take = PAGE_SIZE)
            val first = newest.firstOrNull()
            // Keep older pages that were already loaded.
            val older = if (first == null) emptyList() else messages.filter { it.createdAt.isBefore(first.createdAt) }
            messages.clear()
            messages.addAll(older + newest)
            if (older.isEmpty()) hasOlder = newest.size == PAGE_SIZE
            scrollToEnd++
            markRead()
        } catch (e: Exception) {
            showError(e)
        } finally {
            isLoading = false
        }
    }

    private fun loadOlder() {
        val first = messages.firstOrNull() ?: return
        if (isLoadingOlder) return
        launch {
            isLoadingOlder = true
            try {
                val older = ChatApi.messages(conversationId, before = first.createdAt, take = PAGE_SIZE)
                messages.addAll(0, older.filter { message -> messages.none { it.id == message.id } })
                hasOlder = older.size == PAGE_SIZE
            } catch (e: Exception) {
                showError(e)
            } finally {
                isLoadingOlder = false
            }
        }
    }

    private fun send() {
        val content = draft.trim()
        if (content.isEmpty()) return
        launch {
            isSending = true
            try {
                val message = ChatApi.send(content, conversationId)
                draft = ""
                append(message)
                ChatStore.didSend(message)
            } catch (e: Exception) {
                showError(e)
                // The coach may have just turned the chat off: show it read only.
                ChatStore.refresh()
            } finally {
                isSending = false
            }
        }
    }

    private suspend fun markRead() {
        val hasUnread = (conversation?.unreadCount ?: 0) > 0 || messages.any { !isOwn(it) && it.readAt == null }
        if (!hasUnread) return
        ChatStore.markReadLocally(conversationId)
        val now = Instant.now()
        for (index in messages.indices) {
            val message = messages[index]
            if (!isOwn(message) && message.readAt == null) messages[index] = message.copy(readAt = now)
        }
        runCatching { ChatApi.markRead(conversationId) }
    }

    @Composable
    private fun ReportDialog() {
        AlertDialog(
            onDismissRequest = { showReport = false },
            containerColor = ApexColors.background,
            title = { Text(stringResource(R.string.chat_report), style = ApexText.headline) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.chat_report_info), style = ApexText.subheadline, color = ApexColors.secondaryLabel)
                    PlainTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        placeholder = stringResource(R.string.chat_report_reason),
                        singleLine = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .apexCard()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showReport = false
                    report()
                }) {
                    Text(stringResource(R.string.chat_report_send), color = ApexColors.red, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReport = false }) {
                    Text(stringResource(R.string.cancel), color = ApexColors.main)
                }
            }
        )
    }

    private fun report() {
        val reason = reportReason.trim().ifEmpty { null }
        launch {
            try {
                ChatApi.report(conversationId, reason)
                ToastManager.show(R.string.chat_report_sent, ToastType.SUCCESS)
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    private fun sendPicture(context: Context, uri: Uri) {
        launch {
            isSending = true
            try {
                val upload = ChatImages.prepare(context, uri)
                if (upload == null) {
                    ToastManager.show(R.string.chat_picture_not_supported, ToastType.ERROR)
                    return@launch
                }
                val message = ChatApi.sendImage(upload, conversationId)
                append(message)
                ChatStore.didSend(message)
            } catch (e: Exception) {
                showError(e)
                ChatStore.refresh()
            } finally {
                isSending = false
            }
        }
    }

    private fun shareWorkout(workout: Workout) {
        launch {
            isSending = true
            try {
                val message = ChatApi.send("", conversationId, workoutId = workout.id)
                append(message)
                ChatStore.didSend(message)
            } catch (e: Exception) {
                showError(e)
                ChatStore.refresh()
            } finally {
                isSending = false
            }
        }
    }

    private fun setBlocked(isBlocked: Boolean) {
        launch {
            isUpdating = true
            try {
                ChatStore.upsert(ChatApi.block(conversationId, isBlocked))
                ToastManager.show(
                    if (isBlocked) R.string.chat_blocked_detail else R.string.chat_unblocked_detail,
                    ToastType.SUCCESS
                )
            } catch (e: Exception) {
                showError(e)
            } finally {
                isUpdating = false
            }
        }
    }

    private fun setEnabled(isEnabled: Boolean) {
        val conversation = conversation ?: return
        launch {
            isUpdating = true
            try {
                val updated = ChatApi.setConversation(conversation.client.id, isEnabled)
                ChatStore.upsert(updated)
                ToastManager.show(
                    if (isEnabled) R.string.chat_enabled_detail else R.string.chat_disabled_detail,
                    ToastType.SUCCESS
                )
            } catch (e: Exception) {
                showError(e)
            } finally {
                isUpdating = false
            }
        }
    }

    override fun onDispose() {
        detach()
        super.onDispose()
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
