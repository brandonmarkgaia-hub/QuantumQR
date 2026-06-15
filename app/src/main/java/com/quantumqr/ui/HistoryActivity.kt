package com.quantumqr.ui
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import com.quantumqr.data.ScanRepository
import com.quantumqr.databinding.ActivityHistoryBinding
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = HistoryAdapter()
        recyclerView.adapter = adapter

        ItemTouchHelper(
            SwipeDecor { position, _ ->
                adapter.removeAt(position)
                lifecycleScope.launch {
                    val repo = ScanRepository.get(this@HistoryActivity)
                    val current = repo.history().first().toMutableList()
                    if (position in current.indices) {
                        adapter.removeAt(position)
/* repo.set(current) // TODO: persist delete in DAO */
                    }
                }
            }
        ).attachToRecyclerView(binding.recyclerView)

        val repo = ScanRepository.get(this)
        lifecycleScope.launch {
            repo.history().collect { list -> adapter.submitList(list.map { HistoryItem(it.content, it.format) }) }
        }
    }
}



