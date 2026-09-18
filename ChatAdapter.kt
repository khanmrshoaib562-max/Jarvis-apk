package com.jarvis.ai

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun addUserMessage(text: String) {
        addMessage(ChatMessage(text, isUser = true))
    }

    fun addAiMessage(text: String) {
        addMessage(ChatMessage(text, isUser = false))
    }

    fun getLastPosition(): Int = messages.size - 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val container: LinearLayout = itemView as LinearLayout

        fun bind(message: ChatMessage) {
            tvMessage.text = message.text
            if (message.isUser) {
                tvMessage.setBackgroundResource(R.drawable.bubble_user)
                container.gravity = Gravity.END
            } else {
                tvMessage.setBackgroundResource(R.drawable.bubble_ai)
                container.gravity = Gravity.START
            }
        }
    }
}
