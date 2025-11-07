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
import android.util.Log

object GeminiService {
    private const val API_KEY = "AIzaSyANJKbF-4wGLnXgkzAPeAt8j3k_pVSK3ck"
    private val gson = Gson()
    private const val TAG = "GeminiService"

    // System prompt (tetap seperti sebelumnya)
    private val SYSTEM_PROMPT = """
    Kamu adalah asisten keuangan untuk anak sekolah SD sampai SMA/SMK.
    Tugasmu menjelaskan:
    - cara menabung
    - mengatur uang jajan
    - tujuan keuangan pelajar
    - perbedaan kebutuhan dan keinginan
    - diskon dan harga barang
    - kebiasaan keuangan yang sehat
    
    Gunakan bahasa Indonesia yang sangat mudah dipahami oleh anak sekolah.
    Kalimat pendek dan jelas.
    Nada ramah dan menyemangati.
    
    Jika pengguna bertanya di luar topik uang atau keuangan pelajar:
    jawab hanya:
    "Maaf, aku hanya bisa membantu tentang keuangan pelajar. Apa kamu punya pertanyaan tentang keuangan?"
    
    Jangan bahas politik, agama, teknologi rumit, atau hal dewasa.
    Jangan gunakan istilah ekonomi sulit.
    """.trimIndent()

    private val client by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getChatResponse(prompt: String): String =
        withContext(Dispatchers.IO) {
            val effectivePrompt = "$SYSTEM_PROMPT\n\nPengguna: $prompt"

            val requestJson = mapOf(
                "contents" to listOf(
                    mapOf(
                        "parts" to listOf(
                            mapOf("text" to effectivePrompt)
                        )
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.6,
                    "maxOutputTokens" to 350
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
                Log.d(TAG, "Gemini raw resp: ${resp.code} / ${respStr.take(300)}")

                if (!resp.isSuccessful) {
                    throw Exception("Gemini error ${resp.code}: ${respStr.take(200)}")
                }

                return@withContext sanitizeOutput(parseTextFromJsonSafe(respStr))
            }
        }

    // parse text from JSON (sama seperti sebelumnya)
    private fun parseTextFromJsonSafe(raw: String): String {
        if (raw.isBlank()) return "Maaf, koneksi bermasalah. Coba lagi ya."
        return try {
            val json = gson.fromJson(raw, JsonElement::class.java)
            val found = mutableListOf<String>()

            fun scan(el: JsonElement?) {
                if (el == null) return
                when {
                    el.isJsonPrimitive -> {
                        val s = el.asString
                        if (s.isNotBlank()) found.add(s)
                    }
                    el.isJsonArray -> el.asJsonArray.forEach { scan(it) }
                    el.isJsonObject -> {
                        val obj = el.asJsonObject
                        listOf("candidates", "contents", "output", "outputs", "content", "parts", "text")
                            .filter { obj.has(it) }
                            .forEach { scan(obj.get(it)) }
                        obj.entrySet().forEach { (_, v) -> scan(v) }
                    }
                }
            }

            scan(json)

            found.firstOrNull { it.isNotBlank() }
                ?.take(1500)
                ?.trim()
                ?: "Maaf, ayo bahas tentang uang saja ya."
        } catch (e: Exception) {
            Log.e(TAG, "Parse fail: ${e.localizedMessage}")
            "Maaf, terjadi kesalahan. Coba ulangi ya."
        }
    }

    // Sanitize: hapus markdown emphasis, code fences, backticks, tiruan bullet-asterisks, dan whitespace berlebih
    private fun sanitizeOutput(input: String): String {
        var s = input

        // 1) hapus code fences ```...```
        s = s.replace(Regex("(?s)```.*?```"), " ")

        // 2) hapus single-line backticks `...`
        s = s.replace("`", " ")

        // 3) hapus bold/italic markers **, __, * , _
        s = s.replace(Regex("""\*\*|\*|__|_"""), " ")

        // 4) jika ada "- " bullet di awal baris, ubah jadi "• "
        s = s.replace(Regex("(?m)^\\s*-\\s+"), "• ")

        // 5) hapus > blockquote markers
        s = s.replace(Regex("(?m)^>\\s?"), "")

        // 6) collapse multiple spaces/newlines
        s = s.replace(Regex("[ \t]{2,}"), " ")
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        s = s.trim()

        // 7) Pastikan kalimat kapital depan huruf jika perlu (opsional) — disini kita tidak memaksakan terlalu banyak
        return s
    }
}