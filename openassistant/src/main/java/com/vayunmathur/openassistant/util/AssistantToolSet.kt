package com.vayunmathur.openassistant.util
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import androidx.core.net.toUri
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.vayunmathur.library.intents.calendar.EventData
import com.vayunmathur.library.intents.contacts.ContactData
import com.vayunmathur.library.intents.findfamily.FamilyMemberData
import com.vayunmathur.library.intents.music.MusicSearchResult
import com.vayunmathur.library.intents.music.PlayMusicData
import com.vayunmathur.openassistant.MainActivity
import com.vayunmathur.library.intents.notes.NoteData
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.decodeFromString
import kotlin.reflect.KClass

class IntentToolSet(
    private val resultReceiver: ResultReceiver?,
    private val expectedSchema: String?
) : ToolSet {
    var extractionSuccessful: Boolean = false
        private set

    @Tool(description = "Return the structured JSON result of an intent request and finish.")
    fun return_intent_result(jsonResult: String): String {
        Log.i("IntentToolSet", "return_intent_result called with data size: ${jsonResult.length}")
        
        var sanitized = jsonResult.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Auto-fix: Remove leading/trailing spaces from keys
        try {
            val element = Json.parseToJsonElement(sanitized)
            sanitized = trimJsonKeys(element).toString()
            Log.d("IntentToolSet", "Auto-trimmed keys in JSON")
        } catch (e: Exception) {
            Log.w("IntentToolSet", "Initial parse for key trimming failed: ${e.message}")
        }
            
        Log.d("IntentToolSet", "Sanitized JSON Result: $sanitized")

        if (expectedSchema != null) {
            val validationError = validateJsonAgainstSchema(sanitized, expectedSchema)
            if (validationError != null) {
                Log.e("IntentToolSet", "Schema validation failed: $validationError")
                return "CRITICAL ERROR: Your JSON output is INVALID. Details: $validationError. You MUST fix this error and call 'return_intent_result' AGAIN immediately with the corrected JSON. Do NOT respond with text."
            }
        }

        if (resultReceiver == null) {
            Log.e("IntentToolSet", "ResultReceiver is null, cannot return result!")
            return "Error: Internal callback missing"
        }
        extractionSuccessful = true
        resultReceiver.send(0, Bundle().apply { putString("json_result", sanitized) })
        Log.d("IntentToolSet", "Result sent to receiver")
        return "Result returned successfully."
    }

    private fun trimJsonKeys(element: JsonElement): JsonElement {
        return when (element) {
            is JsonObject -> {
                val newMap = mutableMapOf<String, JsonElement>()
                element.forEach { (key, value) ->
                    newMap[key.trim()] = trimJsonKeys(value)
                }
                JsonObject(newMap)
            }
            is JsonArray -> {
                JsonArray(element.map { trimJsonKeys(it) })
            }
            else -> element
        }
    }

    private fun validateJsonAgainstSchema(jsonString: String, schemaString: String): String? {
        val json = try {
            Json.parseToJsonElement(jsonString)
        } catch (e: Exception) {
            Log.e("IntentToolSet", "JSON parsing failed: ${e.message}")
            return "Invalid JSON format: ${e.message}"
        }

        val schema = try {
            Json.parseToJsonElement(schemaString).jsonObject
        } catch (e: Exception) {
            Log.e("IntentToolSet", "Internal Error: Schema itself is invalid JSON", e)
            return null
        }

        return performValidation(json, schema)
    }

    private fun performValidation(data: JsonElement, schema: JsonObject, path: String = ""): String? {
        val pathPrefix = if (path.isEmpty()) "" else "at $path: "
        
        // 1. Basic Type Check
        val expectedType = schema["type"]?.jsonPrimitive?.content
        if (expectedType == "object" && data !is JsonObject) {
            val err = "${pathPrefix}Expected an object but got ${data::class.simpleName}"
            Log.e("IntentToolSet", "Validation Error: $err")
            return err
        }
        if (expectedType == "array" && data !is JsonArray) {
            val err = "${pathPrefix}Expected an array but got ${data::class.simpleName}"
            Log.e("IntentToolSet", "Validation Error: $err")
            return err
        }

        // 2. Object Validation
        if (data is JsonObject) {
            val properties = schema["properties"] as? JsonObject
            
            // Unexpected Fields Check
            data.keys.forEach { key ->
                if (properties == null || !properties.containsKey(key)) {
                    val fullPath = if (path.isEmpty()) key else "$path.$key"
                    val err = "Unexpected field found: '$fullPath'. Please only use EXACT field names defined in the schema (be careful of leading/trailing spaces in keys!)."
                    Log.e("IntentToolSet", "Validation Error: $err")
                    return err
                }
            }

            // Required Fields Check
            val required = schema["required"] as? JsonArray
            required?.forEach { req ->
                val fieldName = req.jsonPrimitive.content
                if (!data.containsKey(fieldName)) {
                    val fullPath = if (path.isEmpty()) fieldName else "$path.$fieldName"
                    val err = "Missing required field: '$fullPath'"
                    Log.e("IntentToolSet", "Validation Error: $err")
                    return err
                }
            }

            // Recurse into properties
            data.forEach { (key, value) ->
                val propSchema = properties?.get(key)?.jsonObject
                if (propSchema != null) {
                    val fullPath = if (path.isEmpty()) key else "$path.$key"

                    // Const check
                    val constValue = propSchema["const"]?.jsonPrimitive?.content
                    if (constValue != null && value.jsonPrimitive.content != constValue) {
                        val err = "Field '$fullPath' must be '$constValue' but got '${value.jsonPrimitive.content}'"
                        Log.e("IntentToolSet", "Validation Error: $err")
                        return err
                    }

                    // Enum check
                    val enumValues = propSchema["enum"] as? JsonArray
                    if (enumValues != null) {
                        val allowed = enumValues.map { it.jsonPrimitive.content }
                        if (value.jsonPrimitive.content !in allowed) {
                            val err = "Field '$fullPath' has invalid value '${value.jsonPrimitive.content}'. Allowed values: $allowed"
                            Log.e("IntentToolSet", "Validation Error: $err")
                            return err
                        }
                    }
                    
                    // RECURSE
                    val nestedError = performValidation(value, propSchema, fullPath)
                    if (nestedError != null) return nestedError
                }
            }
        }
        
        // 3. Array Validation
        if (data is JsonArray) {
            val itemSchema = schema["items"]?.jsonObject
            if (itemSchema != null) {
                data.forEachIndexed { index, element ->
                    val nestedError = performValidation(element, itemSchema, "$path[$index]")
                    if (nestedError != null) return nestedError
                }
            }
        }

        return null
    }
}

class AssistantToolSet(
    private val context: Context
) : ToolSet {

    @Tool(description = "Get a list of all notes")
    fun get_notes(): String {
        return try {
            val result: List<NoteData> = launchIntent(
                context,
                "com.vayunmathur.notes",
                "com.vayunmathur.notes.intents.GetIntent",
                Unit
            )
            result.toString()
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool(description = "Create a new note")
    fun create_note(title: String, content: String): String {
        return try {
            launchIntentU(
                context,
                "com.vayunmathur.notes",
                "com.vayunmathur.notes.intents.InsertIntent",
                NoteData(title, content)
            )
            "Success: Created note '$title'"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool(description = "Get a list of all contacts")
    fun get_contacts(): String {
        return try {
            val result: List<ContactData> = launchIntent(
                context,
                "com.vayunmathur.contacts",
                "com.vayunmathur.contacts.intents.GetIntent",
                Unit
            )
            result.toString()
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool(description = "Create a new contact")
    fun create_contact(name: String, phoneNumber: String): String {
        return try {
            launchIntentU(
                context,
                "com.vayunmathur.contacts",
                "com.vayunmathur.contacts.intents.InsertIntent",
                ContactData(name, phoneNumber)
            )
            "Success: Created contact '$name'"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool(description = "Get a list of calendar events")
    fun get_calendar_events(): String {
        return try {
            val result: List<EventData> = launchIntent(
                context,
                "com.vayunmathur.calendar",
                "com.vayunmathur.calendar.intents.GetIntent",
                Unit
            )
            result.toString()
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool(description = "Create a new calendar event")
    fun create_calendar_event(title: String, start: Double, end: Double, location: String = ""): String {
        return try {
            launchIntentU(
                context,
                "com.vayunmathur.calendar",
                "com.vayunmathur.calendar.intents.InsertIntent",
                EventData(title, start.toLong(), end.toLong(), location)
            )
            "Success: Created event '$title'"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool(description = "Get a list of family members and their current locations")
    fun get_family_locations(): String {
        return try {
            val result: List<FamilyMemberData> = launchIntent(
                context,
                "com.vayunmathur.findfamily",
                "com.vayunmathur.findfamily.intents.GetIntent",
                Unit
            )
            println(result)
            result.toString()
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool(description = "Search for music (songs, albums, artists, or playlists)")
    fun search_music(query: String): String {
        return try {
            val result: List<MusicSearchResult> = launchIntent(
                context,
                "com.vayunmathur.music",
                "com.vayunmathur.music.intents.SearchIntent",
                query
            )
            result.toString()
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool(description = "Play music given its id and type (song, album, artist, or playlist)")
    fun play_music(id: Double, type: String): String {
        return try {
            launchIntentU(
                context,
                "com.vayunmathur.music",
                "com.vayunmathur.music.intents.PlayIntent",
                PlayMusicData(id.toLong(), type),
            )
            "Success: Playing music"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool(description = "Get the current date and time in the local timezone")
    fun get_local_current_date_time(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val tzId = TimeZone.currentSystemDefault().id
        return "$tzId: $now"
    }

    @SuppressLint("QueryPermissionsNeeded")
    @Tool(description = "Get a list of installed apps on the device")
    fun get_app_list(): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.map { it.loadLabel(pm).toString() }.toString()
    }

    @Tool(description = "Open an app given its package id")
    fun open_app(@ToolParam(description = "package id") packageId: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(packageId)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Success: Opened $packageId"
        } else "Error: App not found"
    }

    @Tool(description = "Send a message")
    fun send_message(recipient: String, message: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "smsto:$recipient".toUri()
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened messaging app."
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool(description = "Make a phone call")
    fun make_phone_call(recipient: String): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = "tel:$recipient".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened dialer."
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    @Tool("Set title of current conversation. Mandatory for first response")
    fun set_conversation_title(newTitle: String): String {
        InferenceService.newTitle = newTitle
        return "Conversation title set successfully"
    }

    @Tool(description = "Get weather")
    fun get_weather(latitude: Double, longitude: Double): String = "Weather: 22°C, Sunny."
}

inline fun <reified Input : Any, reified Output : Any> launchIntent(
    context: Context,
    packageName: String,
    className: String,
    input: Input,
): Output = runBlocking {
    val stringOutput = MainActivity.intentLauncher.launch(context, packageName, className, serializer<Input>(), input)
    Json.decodeFromString(serializer<Output>(), stringOutput)
}
inline fun <reified Input : Any> launchIntentU(
    context: Context,
    packageName: String,
    className: String,
    input: Input,
): Unit = runBlocking {
    val stringOutput = MainActivity.intentLauncher.launch(context, packageName, className, serializer<Input>(), input)
    Json.decodeFromString(serializer<Unit>(), stringOutput)
}