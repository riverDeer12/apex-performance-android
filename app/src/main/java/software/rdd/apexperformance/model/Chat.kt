package software.rdd.apexperformance.model

import kotlinx.serialization.Serializable
import software.rdd.apexperformance.core.network.ApiClient
import software.rdd.apexperformance.core.network.HttpMethod
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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
    val readAt: IsoInstant? = null,
    // "text", "image" or "workout"; null from an API without pictures.
    val type: String? = null,
    val attachment: ChatAttachment? = null,
    // Workout a coach shared; null when it was deleted.
    val workout: Workout? = null
) {
    val isImage: Boolean get() = type == "image" && attachment != null
    val isWorkout: Boolean get() = type == "workout"

    // Caption or text, null when empty.
    val text: String? get() = content?.takeIf { it.isNotEmpty() }
}

// Picture of an image message, loaded from the API with the login token.
@Serializable
data class ChatAttachment(
    val id: String,
    val contentType: String = "image/jpeg",
    val width: Int? = null,
    val height: Int? = null
) {
    val isGif: Boolean get() = contentType == "image/gif"
}

@Serializable
data class ChatConversation(
    val id: String,
    val coach: ChatParticipant,
    val client: ChatParticipant,
    // Whether anyone can write: the coach enabled it and the client didn't block it.
    val isEnabled: Boolean = true,
    val lastMessageAt: IsoInstant? = null,
    val lastMessage: ChatMessage? = null,
    // Messages of the other participant the logged user hasn't read.
    val unreadCount: Int = 0,
    // The client blocked the chat: read only for both, the coach can't turn it on.
    val isBlockedByClient: Boolean = false
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
    // False when the coach turned the chat off or the client blocked it (read only).
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
    private data class SendRequest(val content: String, val workoutId: String? = null)

    @Serializable
    private data class StartRequest(val coachId: String)

    @Serializable
    private data class BlockRequest(val isBlocked: Boolean)

    @Serializable
    private data class ReportRequest(val reason: String?)

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

    // Client only: blocks or unblocks the chat with their coach. A blocked
    // chat is read only for both and the coach can't turn it back on.
    suspend fun block(conversationId: String, isBlocked: Boolean): ChatConversation =
        ApiClient.request("chat/conversations/$conversationId/block", HttpMethod.POST, BlockRequest(isBlocked))

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

    suspend fun send(content: String, conversationId: String, workoutId: String? = null): ChatMessage =
        ApiClient.request("chat/conversations/$conversationId/messages", HttpMethod.POST, SendRequest(content, workoutId))

    // Sends one picture (JPEG or GIF) as a message.
    suspend fun sendImage(image: ChatImageUpload, conversationId: String): ChatMessage {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("width", image.width.toString())
            .addFormDataPart("height", image.height.toString())
            .addFormDataPart("file", image.fileName, image.data.toRequestBody(image.contentType.toMediaType()))
            .build()
        return ApiClient.decode(ApiClient.requestData("chat/conversations/$conversationId/images", HttpMethod.POST, body))
    }

    // Bytes of an image message's picture.
    suspend fun attachment(id: String, conversationId: String): ByteArray =
        ApiClient.requestData("chat/conversations/$conversationId/attachments/$id")

    // Marks the other participant's messages as read.
    suspend fun markRead(conversationId: String) {
        ApiClient.requestData("chat/conversations/$conversationId/read", HttpMethod.POST, ApiClient.jsonBody(emptyMap<String, String>()))
    }

    // Reports the conversation to the administrator, who gets the reason
    // and the last messages by email.
    suspend fun report(conversationId: String, reason: String?) {
        ApiClient.requestData("chat/conversations/$conversationId/report", HttpMethod.POST, ApiClient.jsonBody(ReportRequest(reason)))
    }

    suspend fun unreadCount(): Int = ApiClient.get<UnreadCountResponse>("chat/unread-count").count
}

// Picture ready to send: photos shrunk to JPEG, GIFs as they are.
class ChatImageUpload(
    val data: ByteArray,
    val contentType: String,
    val fileName: String,
    val width: Int,
    val height: Int
)
