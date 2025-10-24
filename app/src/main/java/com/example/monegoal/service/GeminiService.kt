package com.example.monegoal.service

import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object GeminiService {

    private const val API_KEY = "AI......."
    private val gson = Gson()

    private val client by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getChatResponse(prompt: String, systemPrompt: String? = null): String =
        withContext(Dispatchers.IO) {

            val effectivePrompt = buildString {
                if (!systemPrompt.isNullOrBlank()) append(systemPrompt).append("\n\n")
                append(prompt)
            }

            val requestJson = mapOf(
                "contents" to listOf(
                    mapOf(
                        "parts" to listOf(
                            mapOf("text" to effectivePrompt)
                        )
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.7,
                    "maxOutputTokens" to 512
                )
            )

            val jsonBody = gson.toJson(requestJson)
            val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$API_KEY"

            val req = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(req).execute().use { resp ->
                val respStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    throw Exception("Gemini API error ${resp.code}: $respStr")
                }
                return@withContext parseTextFromJsonSafe(respStr)
            }
        }

    private fun parseTextFromJsonSafe(raw: String): String {
        if (raw.isBlank()) return "Maaf, server tidak merespons."
        return try {
            val json = gson.fromJson(raw, JsonElement::class.java)
            if (json.isJsonObject) {
                val obj = json.asJsonObject

                // Cek di beberapa field umum
                fun tryGet(vararg keys: String): String? {
                    for (k in keys) {
                        if (obj.has(k)) {
                            val el = obj.get(k)
                            if (el.isJsonPrimitive) return el.asString
                            if (el.isJsonArray && el.asJsonArray.size() > 0) {
                                val first = el.asJsonArray[0]
                                if (first.isJsonPrimitive) return first.asString
                                if (first.isJsonObject && first.asJsonObject.has("content")) {
                                    return first.asJsonObject.get("content").asString
                                }
                            }
                        }
                    }
                    return null
                }

                tryGet("text") ?:
                tryGet("output", "outputs", "candidates") ?:
                raw
            } else raw
        } catch (e: Exception) {
            raw
        }
    }
}