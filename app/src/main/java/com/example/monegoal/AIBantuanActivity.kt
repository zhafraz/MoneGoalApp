package com.example.monegoal

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.monegoal.service.GeminiService
import kotlinx.coroutines.launch

class AIBantuanActivity : AppCompatActivity() {
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessageInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var adapter: MessagesAdapter
    private val messages = mutableListOf<Message>()

    private val TYPING_INDICATOR = "Sedang mengetik..."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aibantuan)

        rvMessages = findViewById(R.id.rvMessages)
        etMessageInput = findViewById(R.id.etMessageInput)
        btnSend = findViewById(R.id.btnSend)

        adapter = MessagesAdapter(messages)
        val layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.layoutManager = layoutManager
        rvMessages.adapter = adapter

        btnSend.setOnClickListener {
            submitUserMessage()
        }

        etMessageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitUserMessage()
                true
            } else false
        }

        // Pesan pembuka AI
        adapter.addMessage(Message("Halo, saya AI MoneGoal. Ada yang bisa dibantu?", isUser = false))
        if (adapter.itemCount > 0) rvMessages.scrollToPosition(adapter.itemCount - 1)
    }

    private fun submitUserMessage() {
        val text = etMessageInput.text.toString().trim()
        if (text.isEmpty()) return

        // Tambah pesan user ke list
        adapter.addMessage(Message(text, isUser = true))
        etMessageInput.setText("")
        if (adapter.itemCount > 0) rvMessages.scrollToPosition(adapter.itemCount - 1)

        // Tambah indikator typing AI
        addTypingIndicator()

        // Non-aktifkan tombol kirim agar user tidak spam
        setSendingState(true)

        // Panggil GeminiService secara asinkron (fungsi sekarang menerima 1 argumen)
        lifecycleScope.launch {
            try {
                // Panggil tanpa systemPrompt karena GeminiService sudah memakai SYSTEM_PROMPT internal
                val reply = GeminiService.getChatResponse(text)

                // Hapus indikator typing dan tampilkan jawaban AI
                removeTypingIndicator()
                adapter.addMessage(Message(reply, isUser = false))
                if (adapter.itemCount > 0) rvMessages.scrollToPosition(adapter.itemCount - 1)
            } catch (e: Exception) {
                // Hapus indikator typing, tampilkan error friendly
                removeTypingIndicator()
                val errMsg = when {
                    e.message?.contains("quota", true) == true -> "Kuota habis. Coba lagi besok atau hubungi admin."
                    e.message?.contains("401", true) == true || e.message?.contains("API key", true) == true -> "Masalah otentikasi. Periksa API key."
                    else -> "Terjadi kesalahan: ${e.localizedMessage ?: "Tidak diketahui"}"
                }
                adapter.addMessage(Message(errMsg, isUser = false))
                if (adapter.itemCount > 0) rvMessages.scrollToPosition(adapter.itemCount - 1)
            } finally {
                // Selalu enable tombol lagi
                setSendingState(false)
            }
        }
    }

    private fun addTypingIndicator() {
        // hindari duplikat indikator
        if (messages.any { !it.isUser && it.text == TYPING_INDICATOR }) return
        adapter.addMessage(Message(TYPING_INDICATOR, isUser = false))
        if (adapter.itemCount > 0) rvMessages.scrollToPosition(adapter.itemCount - 1)
    }

    private fun removeTypingIndicator() {
        val index = messages.indexOfLast { !it.isUser && it.text == TYPING_INDICATOR }
        if (index >= 0) {
            messages.removeAt(index)
            adapter.notifyItemRemoved(index)
        }
    }

    private fun setSendingState(isSending: Boolean) {
        btnSend.isEnabled = !isSending
        etMessageInput.isEnabled = !isSending
        btnSend.alpha = if (isSending) 0.6f else 1.0f
    }
}