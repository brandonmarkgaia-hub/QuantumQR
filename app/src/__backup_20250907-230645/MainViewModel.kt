package com.quantumqr.main.java.com.quantumqr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quantumqr.data.AppDb
import com.quantumqr.data.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(val notes: List<Note> = emptyList())

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).notes()

    // Stream notes to UI
    val state = dao.getAll()
        .map { UiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    // Smoke insert: add a note
    fun addSample() = viewModelScope.launch {
        dao.insert(Note(text = "Hello Mzansi SplitPay!"))
    }
}
