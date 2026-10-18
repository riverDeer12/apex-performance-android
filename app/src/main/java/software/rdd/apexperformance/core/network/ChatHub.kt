package software.rdd.apexperformance.core.network

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import software.rdd.apexperformance.BuildConfig
import software.rdd.apexperformance.core.AppEnvironment
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.model.ChatApi
import software.rdd.apexperformance.model.ChatConversation
import software.rdd.apexperformance.model.ChatMessage
import software.rdd.apexperformance.model.ChatMessagesRead
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// Listens to the chat SignalR hub (api/hubs/chat) over a WebSocket with the
// SignalR JSON protocol. The hub has no methods to call, the app only gets
// "MessageReceived", "MessagesRead" and "ConversationUpdated" events, so a
// small client is enough and the app needs no SignalR package.
class ChatHub(private val onEvent: (Event) -> Unit) {

    sealed class Event {
        data class MessageReceived(val message: ChatMessage) : Event()
        data class MessagesRead(val event: ChatMessagesRead) : Event()
        object ConversationUpdated : Event()
        // Connected again after a drop; events missed meanwhile aren't replayed.
        object Reconnected : Event()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null
    private var socket: WebSocket? = null
    private var hasConnectedBefore = false

    // The server drops a client that sends nothing for 30 seconds.
    private val client = ApiClient.http.newBuilder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    fun start() {
        if (loop != null) return
        loop = scope.launch {
            var attempt = 0
            while (isActive) {
                val connected = runConnection()
                if (!isActive) return@launch
                attempt = if (connected) 0 else attempt + 1
                delay(RETRY_DELAYS[minOf(attempt, RETRY_DELAYS.lastIndex)])
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        socket?.close(1000, null)
        socket = null
        hasConnectedBefore = false
    }

    // One connection until it closes. True when the handshake succeeded.
    private suspend fun runConnection(): Boolean {
        val token = AuthManager.token ?: return false
        val url = AppEnvironment.apiUrl("hubs/chat")
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "?access_token=" + URLEncoder.encode(token, "UTF-8")

        val records = Channel<String?>(Channel.UNLIMITED)
        val webSocket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("{\"protocol\":\"json\",\"version\":1}$RECORD_SEPARATOR")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                text.split(RECORD_SEPARATOR).filter { it.isNotEmpty() }.forEach { records.trySend(it) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                records.trySend(null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (BuildConfig.DEBUG) Log.w("ChatHub", "Chat hub disconnected", t)
                records.trySend(null)
            }
        })
        socket = webSocket

        var handshakeDone = false
        try {
            for (record in records) {
                record ?: break
                val json = runCatching { ApiClient.json.parseToJsonElement(record).jsonObject }.getOrNull() ?: continue

                if (!handshakeDone) {
                    // "{}" or {"error": "..."}
                    if (json["error"] != null) break
                    handshakeDone = true
                    if (hasConnectedBefore) onEvent(Event.Reconnected)
                    hasConnectedBefore = true
                    continue
                }

                when ((json["type"] as? JsonPrimitive)?.intOrNull) {
                    1 -> handleInvocation(json)
                    7 -> break // Close, connect again.
                    else -> Unit // Pings and others.
                }
            }
        } finally {
            webSocket.cancel()
            records.close()
        }
        return handshakeDone
    }

    private fun handleInvocation(json: JsonObject) {
        val target = (json["target"] as? JsonPrimitive)?.contentOrNull ?: return
        val argument: JsonElement = (json["arguments"] as? JsonArray)?.firstOrNull() ?: return

        when (target) {
            "MessageReceived" -> decode<ChatMessage>(argument)?.let { onEvent(Event.MessageReceived(it)) }
            "MessagesRead" -> decode<ChatMessagesRead>(argument)?.let { onEvent(Event.MessagesRead(it)) }
            "ConversationUpdated" -> onEvent(Event.ConversationUpdated)
        }
    }

    private inline fun <reified T> decode(element: JsonElement): T? =
        runCatching { ApiClient.json.decodeFromJsonElement<T>(element) }.getOrNull()

    private companion object {
        const val RECORD_SEPARATOR = "\u001e"
        val RETRY_DELAYS = listOf(1_000L, 3_000L, 10_000L, 30_000L)
    }
}

// Chat state shared by the tab badge, the conversation list and the
// open conversation. Started after login for coaches and clients.
object ChatStore {

    var conversations by mutableStateOf<List<ChatConversation>>(emptyList())
        private set
    var unreadCount by mutableStateOf(0)
        private set
    var hasLoaded by mutableStateOf(false)
        private set

    // Conversation on screen: its pushes aren't shown and its messages are read.
    @Volatile
    var openConversationId: String? = null

    // Hub events for the open conversation screen.
    var onMessage: ((ChatMessage) -> Unit)? = null
    var onMessagesRead: ((ChatMessagesRead) -> Unit)? = null
    var onReconnected: (() -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val hub = ChatHub(::handle)
    private var isStarted = false

    val userId: String? get() = AuthManager.userId

    fun start() {
        if (isStarted) return
        isStarted = true
        hub.start()
        scope.launch { refresh() }
    }

    fun stop() {
        isStarted = false
        hub.stop()
        conversations = emptyList()
        unreadCount = 0
        hasLoaded = false
        openConversationId = null
    }

    suspend fun refresh() {
        try {
            conversations = ChatApi.conversations()
            hasLoaded = true
            recountUnread()
        } catch (e: Exception) {
            // The badge waits for the next refresh.
        }
    }

    // Puts a conversation from the API (e.g. after turning it on) into the list.
    fun upsert(conversation: ChatConversation) {
        // Responses of changes come without the last message and unread count.
        val existing = conversations.firstOrNull { it.id == conversation.id }
        val merged = if (conversation.lastMessage == null && existing != null) {
            conversation.copy(lastMessage = existing.lastMessage, unreadCount = existing.unreadCount)
        } else {
            conversation
        }
        conversations = sorted(conversations.filter { it.id != conversation.id } + merged)
        recountUnread()
    }

    // Own message sent from this device.
    fun didSend(message: ChatMessage) = apply(message)

    // The open conversation was read on this device.
    fun markReadLocally(conversationId: String) {
        conversations = conversations.map { if (it.id == conversationId) it.copy(unreadCount = 0) else it }
        recountUnread()
    }

    private fun handle(event: ChatHub.Event) {
        when (event) {
            is ChatHub.Event.MessageReceived -> {
                val message = event.message
                if (conversations.none { it.id == message.conversationId }) scope.launch { refresh() }
                apply(message)
                onMessage?.invoke(message)
            }
            is ChatHub.Event.MessagesRead -> {
                // Another device of this user read them.
                if (event.event.readerUserId.equals(userId, ignoreCase = true)) {
                    markReadLocally(event.event.conversationId)
                }
                onMessagesRead?.invoke(event.event)
            }
            ChatHub.Event.ConversationUpdated -> scope.launch { refresh() }
            ChatHub.Event.Reconnected -> {
                scope.launch { refresh() }
                onReconnected?.invoke()
            }
        }
    }

    // Last message and unread count of the message's conversation.
    private fun apply(message: ChatMessage) {
        val current = conversations.firstOrNull { it.id == message.conversationId } ?: return
        // The same message can come from the POST and the hub.
        if (current.lastMessage?.id == message.id) return

        val isUnread = !message.senderUserId.equals(userId, ignoreCase = true) &&
            openConversationId != message.conversationId
        val updated = current.copy(
            lastMessage = message,
            lastMessageAt = message.createdAt,
            unreadCount = current.unreadCount + if (isUnread) 1 else 0
        )
        conversations = sorted(conversations.map { if (it.id == updated.id) updated else it })
        recountUnread()
    }

    private fun sorted(list: List<ChatConversation>) =
        list.sortedByDescending { it.lastMessageAt ?: java.time.Instant.EPOCH }

    private fun recountUnread() {
        unreadCount = conversations.sumOf { it.unreadCount }
    }
}
