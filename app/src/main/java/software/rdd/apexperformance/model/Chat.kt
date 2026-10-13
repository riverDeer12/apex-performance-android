package software.rdd.apexperformance.model

import kotlinx.serialization.Serializable
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import java.net.URLEncoder
import java.time.Instant

// Coach or client in a chat (docs/CHAT_API.md in the backend).
@Serializable
data class ChatParticipant(
    // Coach id or client id.
    val id: String,
    val userId: String,
    val fullName: String = ""
)

@Serializable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderUserId: String,
    // Null when the message can't be decrypted.
    val content: String? = null,
    val createdAt: IsoInstant,
    // When the other participant read it.
    val readAt: IsoInstant? = null
)

@Serializable
data class ChatConversation(
    val id: String,
    val coach: ChatParticipant,
    val client: ChatParticipant,
    val isEnabled: Boolean = true,
    val lastMessageAt: IsoInstant? = null,
    val lastMessage: ChatMessage? = null,
    // Messages of the other participant the logged user hasn't read.
    val unreadCount: Int = 0
) {
    // The other side: the client for a coach, the coach for a client.
    fun otherParticipant(userId: String?): ChatParticipant =
        if (coach.userId.equals(userId, ignoreCase = true)) client else coach
}

// Someone the logged user can chat with: a client for a coach,
// a coach for a client.
@Serializable
data class ChatContact(
    // Client id for a coach, coach id for a client.
    val id: String,
    val userId: String,
    val fullName: String = "",
    // Null when no chat was started yet.
    val conversationId: String? = null,
    // False when the coach turned the chat off (read only).
    val isEnabled: Boolean = true
)

// "MessagesRead" hub event.
@Serializable
data class ChatMessagesRead(
    val conversationId: String,
    val readerUserId: String,
    val readAt: IsoInstant
)

object ChatApi {

    @Serializable
    private data class SetConversationRequest(val clientId: String, val isEnabled: Boolean)

    @Serializable
    private data class SendRequest(val content: String)

    @Serializable
    private data class StartRequest(val coachId: String)

    @Serializable
    private data class UnreadCountResponse(val count: Int = 0)

    // Conversations of the logged user, newest activity first.
    suspend fun conversations(): List<ChatConversation> = ApiClient.get("chat/conversations")

    // People the logged user can start a chat with, by name.
    suspend fun contacts(): List<ChatContact> = ApiClient.get("chat/contacts")

    // Client only: opens the chat with one of their coaches, creating it when needed.
    // A chat the coach turned off comes back read only.
    suspend fun start(coachId: String): ChatConversation =
        ApiClient.request("chat/conversations/start", HttpMethod.POST, StartRequest(coachId))

    // Coach only: creates the conversation with the client if needed and turns it on or off.
    suspend fun setConversation(clientId: String, isEnabled: Boolean): ChatConversation =
        ApiClient.request("chat/conversations", HttpMethod.POST, SetConversationRequest(clientId, isEnabled))

    // Newest messages, or the ones before the given time; oldest first.
    suspend fun messages(conversationId: String, before: Instant? = null, take: Int = 50): List<ChatMessage> {
        val query = buildString {
            append("take=$take")
            if (before != null) append("&before=" + URLEncoder.encode(before.toString(), "UTF-8"))
        }
        return ApiClient.get("chat/conversations/$conversationId/messages?$query")
    }

    suspend fun send(content: String, conversationId: String): ChatMessage =
        ApiClient.request("chat/conversations/$conversationId/messages", HttpMethod.POST, SendRequest(content))

    // Marks the other participant's messages as read.
    suspend fun markRead(conversationId: String) {
        ApiClient.requestData("chat/conversations/$conversationId/read", HttpMethod.POST, ApiClient.jsonBody(emptyMap<String, String>()))
    }

    suspend fun unreadCount(): Int = ApiClient.get<UnreadCountResponse>("chat/unread-count").count
}
