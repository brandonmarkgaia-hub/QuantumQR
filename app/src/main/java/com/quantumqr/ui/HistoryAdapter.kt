package com.quantumqr.ui

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.quantumqr.R
import com.quantumqr.data.Scan
import java.util.*

class HistoryAdapter(
    private val onCopy: (String) -> Unit,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private var items = mutableListOf<Scan>()

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.txtContent)
        val timestamp: TextView = view.findViewById(R.id.txtTimestamp)
        val btnCopy: ImageButton = view.findViewById(R.id.btnCopy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val scan = items[position]
        holder.content.text = scan.content
        
        val date = Date(scan.timestamp)
        holder.timestamp.text = DateFormat.format("MMM dd, yyyy HH:mm", date)

        holder.btnCopy.setOnClickListener { onCopy(scan.content) }
        holder.itemView.setOnClickListener { onClick(scan.content) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<Scan>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getScanAt(position: Int): Scan = items[position]
}
