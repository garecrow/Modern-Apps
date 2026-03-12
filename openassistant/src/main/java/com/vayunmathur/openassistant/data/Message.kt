package com.vayunmathur.openassistant.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vayunmathur.library.util.DatabaseItem
import kotlinx.serialization.Serializable

@Serializable
data class ToolCall(
    val id: String,
    val type: String,
    val function: Function
) {
    @Serializable
    data class Function(
        val name: String,
        val arguments: String
    )
}


@Entity(
    foreignKeys = [ForeignKey(
        entity = Conversation::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["conversationId"])]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    override val id: Long = 0,
    val conversationId: Long, // Foreign key to Conversation
    val role: String,
    val textContent: String,
    val displayContent: String? = null, // if available, show instead of text content
    val images: List<String>,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall> = listOf(),
    val timestamp: Long = System.currentTimeMillis()
): DatabaseItem

fun List<Message>.toStreamedText(): String {
    val builder = StringBuilder()
    builder.append("<bos>")

    this.forEach { message ->
        builder.append("<start_of_turn>${message.role}\n")

        // 1. Handle Images: Gemma 3n expects a soft token placeholder for each image
        message.images.forEach { _ ->
            builder.append("<image_soft_token>")
        }

        // 2. Handle Tool Calls
        if (message.toolCalls.isNotEmpty()) {
            message.toolCalls.forEach { call ->
                builder.append("call:${call.function.name}(${call.function.arguments})\n")
            }
        } else {
            // 3. Handle Text Content
            builder.append(message.textContent.trim())
        }

        builder.append("<end_of_turn>\n")
    }

    // Preparation for the next model response
    if (this.lastOrNull()?.role != "model") {
        builder.append("<start_of_turn>model\n")
    }

    return builder.toString()
}