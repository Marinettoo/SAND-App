package com.jpm.transporttool.ui.viewmodel

import android.nfc.Tag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.jpm.transporttool.data.model.HistoryEntry
import com.jpm.transporttool.data.model.TransportCard
import com.jpm.transporttool.data.repository.CardRepository

class MainViewModel(private val repository: CardRepository) : ViewModel() {

    var currentScreen by mutableStateOf("home")
    var focusedUid by mutableStateOf("")

    var savedCards by mutableStateOf<List<TransportCard>>(emptyList())
    var historyUids by mutableStateOf<List<String>>(emptyList())
    var displayUids by mutableStateOf<List<String>>(emptyList())

    var lastDetectedTag: Tag? = null
    var prefilledPrefix by mutableStateOf("")

    var showSuccess by mutableStateOf(false)
    var finalAmount by mutableFloatStateOf(0f)
    var showNfcBubble by mutableStateOf(false)
    var isReading by mutableStateOf(false)

    var showCardOptions by mutableStateOf(false)
    var optionUid by mutableStateOf("")

    var isScanningForNewCard by mutableStateOf(false)
    var isWaitingToWrite by mutableStateOf(false)

    var showHelpDialog by mutableStateOf(false)
    var showLegalDialog by mutableStateOf(false)

    var refreshTrigger by mutableStateOf(0)

    var isProMode by mutableStateOf(false)
        private set

    fun toggleProMode() {
        isProMode = !isProMode
        repository.setProModeEnabled(isProMode)
    }

    init {
        isProMode = repository.isProModeEnabled()
        loadDataAndRefreshDisplay()
    }

    fun loadDataAndRefreshDisplay() {
        refreshTrigger++
        val cards = repository.getSavedCards()
        val allUids = repository.getAllHistoryUids()

        val displayList = mutableListOf<String>()
        cards.forEach { card ->
            val fullUid = allUids.find { it.startsWith(card.uidPrefix) } ?: card.uidPrefix
            if (!displayList.contains(fullUid)) displayList.add(fullUid)
        }
        allUids.forEach { uid ->
            val isSaved = cards.any { uid.startsWith(it.uidPrefix) }
            if (!isSaved && !displayList.contains(uid)) displayList.add(uid)
        }
        
        savedCards = cards
        historyUids = allUids
        displayUids = displayList
    }

    fun saveNewCard(card: TransportCard) {
        val newList = savedCards.filter { it.uidPrefix != card.uidPrefix } + card
        repository.saveCardList(newList)
        loadDataAndRefreshDisplay()
    }

    fun saveNewCardList(newList: List<TransportCard>) {
        repository.saveCardList(newList)
        loadDataAndRefreshDisplay()
    }

    fun loadHistoryForUid(uid: String): List<HistoryEntry> {
        return repository.loadHistoryForUid(uid)
    }

    fun addToHistory(uid: String, balance: Double) {
        repository.addToHistory(uid, balance)
        loadDataAndRefreshDisplay()
    }

    fun deleteHistoryItem(uid: String, entry: HistoryEntry) {
        repository.deleteHistoryEntry(uid, entry)
        loadDataAndRefreshDisplay()
    }

    fun deleteFullHistory(uid: String) {
        repository.deleteFullHistory(uid)
        loadDataAndRefreshDisplay()
    }

    fun deleteCardConfig(uidPrefix: String) {
        val newList = savedCards.filter { it.uidPrefix != uidPrefix }
        repository.saveCardList(newList)
        loadDataAndRefreshDisplay()
    }
    
    fun isDisclaimerAccepted(): Boolean = repository.isDisclaimerAccepted()
    
    fun setDisclaimerAccepted(accepted: Boolean) = repository.setDisclaimerAccepted(accepted)
}

// Simple property delegate for Float since mutableFloatStateOf is not always available in older Compose
private fun mutableFloatStateOf(value: Float) = mutableStateOf(value)
