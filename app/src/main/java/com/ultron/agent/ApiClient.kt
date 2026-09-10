package com.ultron.agent

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiClient(private val apiKey: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    data class Action(
        val actionType: String,
        val targetIdentifier: String,
        val inputText: String? = null
    )

    suspend fun sendRequest(transcribedText: String, uiJson: String): Action {
        val requestBody = mapOf(
            "model" to "deepseek/deepseek-r1:free",
            "messages" to listOf(
                mapOf(
                    "role" to "system",
                    "content" to "You are an Android UI agent. Output strict JSON with: action_type (CLICK, TYPE, SWIPE, LAUNCH), target_identifier, and input_text."
                ),
                mapOf(
                    "role" to "user",
                    "content" to "Command: $transcribedText\nUI: $uiJson"
                )
            ),
            "response_format" to mapOf("type" to "json_object")
        )

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val response = client.newCall(request).execute()
        val jsonResponse = gson.fromJson(response.body?.string(), JsonObject::class.java)
        val content = jsonResponse.getAsJsonArray("choices")[0].asJsonObject
            .getAsJsonObject("message").get("content").asString

        val actionJson = gson.fromJson(content, JsonObject::class.java)
        return Action(
            actionType = actionJson.get("action_type")?.asString ?: "UNKNOWN",
            targetIdentifier = actionJson.get("target_identifier")?.asString ?: "",
            inputText = actionJson.get("input_text")?.asString
        )
    }
}
