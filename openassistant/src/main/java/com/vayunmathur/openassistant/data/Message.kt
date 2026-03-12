package com.vayunmathur.openassistant.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vayunmathur.library.util.DatabaseItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class ToolCall(
    val name: String,
    val parameters: Map<String, JsonElement>
)


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

    // Define the system instruction for tool use
    val toolSystemPrompt = """
        You have access to the following functions:
        ${Tools.ALL_TOOLS.joinToString("\n") { it.systemDescription() }}
        If you decide to invoke any of the function(s),
        you MUST put it in the format of
        {"name": function name, "parameters": {dictionary of argument name and its value}}
        Even if there are no parameters, it must be an empty object
        You must include nothing else in your response, including any formatting or markdown.
        You also may only call one function at a time
    """.trimIndent()

    // 1. Explicit System Turn
    builder.append("<start_of_turn>system\n")
    builder.append(toolSystemPrompt)
    builder.append("<end_of_turn>\n")

    this.forEach { message ->
        if(message.role == "tool") {
            builder.append("<start_of_turn>user\n")
        } else {
            builder.append("<start_of_turn>${message.role}\n")
        }

        // Handle Images
        message.images.forEach { _ ->
            builder.append("<image_soft_token>")
        }

        // Handle Tool Calls
        if (message.toolCalls.isNotEmpty()) {
            message.toolCalls.forEach { call ->
                // Outputting the JSON format we told the model to use
                builder.append(Json.encodeToString(call))
                builder.append("\n")
            }
        } else {
            builder.append(message.textContent.trim())
        }

        builder.append("<end_of_turn>\n")
    }

    // Preparation for the model's next response
    if (this.lastOrNull()?.role != "model") {
        builder.append("<start_of_turn>model\n")
    }

    println(builder.toString())

    return builder.toString()
}