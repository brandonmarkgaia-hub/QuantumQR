package com.quantumqr.util


import com.quantumqr.R

import android.app.AlertDialog
import android.content.Context

object HistoryStore {
    private const val PREF = "scan_history"
    private const val KEY = "entries"

    fun add(context: Context, text: String) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val current = p.getString(KEY, "") ?: ""
        val items = if (current.isEmpty()) mutableListOf<String>() else current.split('\n').toMutableList()
        if (items.isEmpty() || items.first() != text) {
            items.add(0, text)
        }
        // keep last 50
        while (items.size > 50) items.removeLast()
        p.edit().putString(KEY, items.joinToString("\n")).apply()
    }

    fun showDialog(context: Context, onPick: (String) -> Unit = {}) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val items = (p.getString(KEY, "") ?: "")
            .split('\n')
            .filter { it.isNotBlank() }
        if (items.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("History")
                .setMessage("No scans yet.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        AlertDialog.Builder(context)
            .setTitle("History")
            .setItems(items.toTypedArray()) { _, i ->
                onPick(items[i])
            }
            .setNegativeButton("Close", null)
            .show()
    }
}