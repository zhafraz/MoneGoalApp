package com.example.monegoal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView

class MessageAdapter(
    private val items: MutableList<Message>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_AI = 0
        private const val TYPE_USER = 1
    }

    // ViewHolder untuk AI
    class AiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAiMessage: TextView = itemView.findViewById(R.id.tvAiMessage)
    }

    // ViewHolder untuk User
    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUserMessage: TextView = itemView.findViewById(R.id.tvUserMessage)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isUser) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            val v = inflater.inflate(R.layout.item_message_user, parent, false)
            UserViewHolder(v)
        } else {
            val v = inflater.inflate(R.layout.item_message_ai, parent, false)
            AiViewHolder(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        if (holder is AiViewHolder) {
            holder.tvAiMessage.text = msg.text
        } else if (holder is UserViewHolder) {
            holder.tvUserMessage.text = msg.text
        }
    }

    override fun getItemCount(): Int = items.size

    // Helper untuk menambah pesan dan scroll pada Activity
    fun addMessage(message: Message) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }

    // Update pesan terakhir (dipakai untuk efek typing)
    fun updateLastMessage(text: String) {
        if (items.isEmpty()) return
        val lastIndex = items.size - 1
        // pastikan pesan terakhir adalah pesan AI (safety)
        val last = items[lastIndex]
        items[lastIndex] = Message(text, isUser = false)
        notifyItemChanged(lastIndex)
    }

    // Optional: clear semua pesan
    fun clearMessages() {
        val size = items.size
        items.clear()
        if (size > 0) notifyItemRangeRemoved(0, size)
    }
}