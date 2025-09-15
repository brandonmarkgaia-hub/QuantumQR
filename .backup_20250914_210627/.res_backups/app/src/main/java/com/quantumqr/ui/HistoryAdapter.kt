package com.quantumqr.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class HistoryItem(val title: String, val subtitle: String? = null)

class HistoryAdapter(
    private val items: MutableList<HistoryItem> = mutableListOf()
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    inner class VH(val titleView: TextView) : RecyclerView.ViewHolder(titleView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.titleView.text = it.title + (it.subtitle?.let { s -> " — $s" } ?: "")
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<HistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }    fun removeAt(position: Int) {
        if (position in 0 until items.size) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}
