package com.quantumqr.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.quantumqr.data.ScanRepository
import com.quantumqr.databinding.ActivityHistoryBinding
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = HistoryAdapter(
            onCopy = { text -> copyToClipboard(text) },
            onClick = { text -> openLink(text) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
        }

        binding.btnClearAll.setOnClickListener {
            showClearAllConfirmation()
        }

        observeHistory()
    }

    private fun observeHistory() {
        val repo = ScanRepository.get(this)
        lifecycleScope.launch {
            repo.history().collect { list ->
                adapter.submitList(list)
                binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("QR", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun openLink(text: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text)))
        } catch (_: Exception) {
            Toast.makeText(this, "Not a valid link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClearAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear History")
            .setMessage("Are you sure you want to delete all scans?")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    ScanRepository.get(this@HistoryActivity).clear()
                    Toast.makeText(this@HistoryActivity, "History cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
