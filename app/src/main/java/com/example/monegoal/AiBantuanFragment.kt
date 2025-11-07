package com.example.monegoal

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.monegoal.service.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.ViewGroup as VG
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast

class AiBantuanFragment : Fragment() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessageInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<Message>()

    // quick question views (found dynamically)
    private var quickViews: List<View> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ai_bantuan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvMessages = view.findViewById(R.id.rvMessages)
        etMessageInput = view.findViewById(R.id.etMessageInput)
        btnSend = view.findViewById(R.id.btnSend)

        setupRecyclerView()
        setupSendButton()

        // find GridLayout for quick questions:
        val grid = findGridLayout(view)
        if (grid != null) {
            // collect children as clickable quick items (usually 4)
            val tmp = mutableListOf<View>()
            for (i in 0 until grid.childCount) {
                tmp.add(grid.getChildAt(i))
            }
            quickViews = tmp
            bindQuickClicks()
        }

        // greeting
        showAiMessageWithTyping("Halo! Aku Aimo, teman AI keuanganmu. Mau tanya apa tentang uang hari ini? 😊")
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messageList)
        rvMessages.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = messageAdapter
        }
    }

    private fun setupSendButton() {
        btnSend.setOnClickListener {
            val userMessage = etMessageInput.text.toString().trim()
            if (userMessage.isNotEmpty()) {
                sendUserMessage(userMessage)
                etMessageInput.text.clear()
            }
        }
    }

    private fun sendUserMessage(text: String) {
        // tambahkan pesan user
        messageAdapter.addMessage(Message(text, isUser = true))
        rvMessages.scrollToPosition(messageAdapter.itemCount - 1)

        // tambahkan placeholder pesan AI kosong (akan di-update)
        messageAdapter.addMessage(Message("", isUser = false))
        rvMessages.scrollToPosition(messageAdapter.itemCount - 1)

        // panggil API di background
        lifecycleScope.launch {
            try {
                // panggil GeminiService di IO
                val rawResponse = withContext(Dispatchers.IO) {
                    GeminiService.getChatResponse(text)
                }
                // tampilkan secara typing
                showAiMessageTyping(rawResponse)
            } catch (e: Exception) {
                // jika error, update placeholder menjadi pesan error
                messageAdapter.updateLastMessage("Maaf, terjadi kesalahan: ${e.localizedMessage}")
                rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
            }
        }
    }

    private fun showAiMessageWithTyping(text: String) {
        // tambahkan placeholder dulu
        messageAdapter.addMessage(Message("", isUser = false))
        rvMessages.scrollToPosition(messageAdapter.itemCount - 1)

        lifecycleScope.launch {
            showAiMessageTyping(text)
        }
    }

    private suspend fun showAiMessageTyping(fullText: String) {
        val cleaned = fullText.trim()
        if (cleaned.isEmpty()) {
            messageAdapter.updateLastMessage("Maaf, aku tidak mengerti. Coba tanya lain ya.")
            rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
            return
        }

        // kita ketik per karakter untuk efek; `delayPerChar` bisa disesuaikan
        val delayPerChar = 8L

        val sb = StringBuilder()
        for (i in cleaned.indices) {
            sb.append(cleaned[i])
            messageAdapter.updateLastMessage(sb.toString())
            rvMessages.scrollToPosition(messageAdapter.itemCount - 1)

            val ch = cleaned[i]
            val extra = if (ch == '.' || ch == ',' || ch == '?' || ch == '!') 40L else 0L
            // buat efek sedikit acak agar natural (opsional): tetap sederhana di sini
            delay(delayPerChar + extra)
        }

        // pastikan final exact
        messageAdapter.updateLastMessage(cleaned)
        rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
    }

    // Mencari GridLayout di view tree (rekursif) — gunakan id "gridQuickQuestions" di XML untuk lebih aman.
    private fun findGridLayout(root: View): GridLayout? {
        // cepat cek id jika ada
        val byId = root.findViewById<GridLayout?>(resources.getIdentifier("gridQuickQuestions", "id", requireContext().packageName))
        if (byId != null) return byId

        fun search(v: View): GridLayout? {
            if (v is GridLayout) return v
            if (v is VG) {
                for (i in 0 until v.childCount) {
                    val child = v.getChildAt(i)
                    val res = search(child)
                    if (res != null) return res
                }
            }
            return null
        }
        return search(root)
    }

    private fun bindQuickClicks() {
        if (quickViews.isEmpty()) return

        // contoh mapping: ubah sesuai teks di layout jika perlu
        val quickTexts = listOf(
            "Cara mengatur uang jajan untuk anak sekolah?",
            "Bagaimana investasi yang aman untuk pelajar?",
            "Tips menabung efektif untuk tujuan sekolah",
            "Bagaimana caranya melunasi hutang kecil secara bertahap?"
        )

        for (i in quickViews.indices) {
            val v = quickViews[i]
            val text = if (i < quickTexts.size) quickTexts[i] else quickTexts[0]
            v.isClickable = true
            v.setOnClickListener {
                // sebagai UX: tambahkan teks ke input dan langsung kirim
                sendUserMessage(text)
            }
        }
    }
}