package com.example.monegoal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.monegoal.service.GeminiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AiBantuanFragment : Fragment() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessageInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<Message>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ai_bantuan, container, false)

        rvMessages = view.findViewById(R.id.rvMessages)
        etMessageInput = view.findViewById(R.id.etMessageInput)
        btnSend = view.findViewById(R.id.btnSend)

        setupRecyclerView()
        setupSendButton()

        return view
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
                addMessage(userMessage, isUser = true)
                etMessageInput.text.clear()
                getAiResponse(userMessage)
            }
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        val message = Message(text, isUser)
        messageAdapter.addMessage(message)
        rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
    }

    private fun getAiResponse(userMessage: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = GeminiService.getChatResponse(userMessage)
                addMessage(response, isUser = false)
            } catch (e: Exception) {
                addMessage("Maaf, terjadi kesalahan. Coba lagi ya.", isUser = false)
            }
        }
    }

}