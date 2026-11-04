package com.android.androidAndroid.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.androidAndroid.R

/**
 * Chat 页面消息列表适配器（气泡式展示）。
 *
 * 职责概览（给后续 code agent / LLM）：
 * - 渲染 `ChatMessage` 列表项：消息正文 + 时间。
 * - 根据 `ChatMessage.mode` 控制气泡对齐方向：
 *   - `mode=1` -> 左侧（系统/Agent）
 *   - 其他值 -> 右侧（用户）
 * - 将气泡宽度限制为屏幕宽度的 80%，保持类似聊天界面的视觉层次。
 *
 * 数据约定：
 * - `entries` 由外部通过 `submitEntries` 提交完整列表.
 * - 本 Adapter 不持有业务状态，只负责将当前数据映射到视图。
 */

class ChatMessageAdapter(
    private var entries: List<ChatMessage>
) : RecyclerView.Adapter<ChatMessageAdapter.EntryViewHolder>() {

    fun submitEntries(entries: List<ChatMessage>) {
        this.entries = entries
        notifyDataSetChanged()
    }

    fun notifyEntryInserted(position: Int) {
        notifyItemInserted(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return EntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        val item = entries[position]
        holder.textView.text = item.message
        holder.timeView.text = item.time.orEmpty()

        val params = holder.container.layoutParams as FrameLayout.LayoutParams
        params.width = (holder.itemView.resources.displayMetrics.widthPixels * 0.8f).toInt()
        params.gravity = if (item.mode == 1) Gravity.START else Gravity.END
        holder.container.layoutParams = params
    }

    override fun getItemCount(): Int = entries.size

    class EntryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: LinearLayout = view.findViewById(R.id.bubbleContainer)
        val timeView: TextView = view.findViewById(R.id.textViewEntryTime)
        val textView: TextView = view.findViewById(R.id.textViewEntry)
    }
}
