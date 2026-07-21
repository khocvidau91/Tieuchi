package com.xiaozhi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import java.io.File

// Data class Message được gộp vào đây
data class Message(val type: Int, val content: String, val isImage: Boolean = false) {
    companion object {
        const val TYPE_USER = 0
        const val TYPE_ASSISTANT = 1
        const val TYPE_SYSTEM = 2
    }
}

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val messages = mutableListOf<Message>()

    fun addMessage(msg: Message) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isImage) 3 else messages[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            3 -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
                ImageViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
                ViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is ImageViewHolder -> {
                Glide.with(holder.imageView.context)
                    .load(File(msg.content))
                    .into(holder.imageView)
                val params = holder.card.layoutParams as FrameLayout.LayoutParams
                params.gravity = android.view.Gravity.END
                holder.card.layoutParams = params
            }
            is ViewHolder -> {
                holder.textView.text = msg.content
                val params = holder.card.layoutParams as FrameLayout.LayoutParams
                when (msg.type) {
                    Message.TYPE_USER -> {
                        params.gravity = android.view.Gravity.END
                        holder.card.setCardBackgroundColor(0xFF1D4ED8.toInt())
                        holder.textView.setTextColor(0xFFFFFFFF.toInt())
                    }
                    Message.TYPE_ASSISTANT -> {
                        params.gravity = android.view.Gravity.START
                        holder.card.setCardBackgroundColor(0xFF1E293B.toInt())
                        holder.textView.setTextColor(0xFFE2E8F0.toInt())
                    }
                    else -> {
                        params.gravity = android.view.Gravity.CENTER
                        holder.card.setCardBackgroundColor(0xFF334155.toInt())
                        holder.textView.setTextColor(0xFFCBD5E1.toInt())
                    }
                }
                holder.card.layoutParams = params
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.message_card)
        val textView: TextView = itemView.findViewById(R.id.message_text)
    }

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.message_card)
        val imageView: ImageView = itemView.findViewById(R.id.message_image)
    }
}