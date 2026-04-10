package com.jpm.transporttool.ui.viewmodel

import android.nfc.Tag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.jpm.transporttool.data.model.HistoryEntry
import com.jpm.transporttool.data.model.TransportCard
import com.jpm.transporttool.data.model.TravelRecord
import com.jpm.transporttool.data.repository.CardRepository
import android.util.Log

class MainViewModel(private val repository: CardRepository) : ViewModel() {

    var currentScreen by mutableStateOf("home")
    var focusedUid by mutableStateOf("")

    var savedCards by mutableStateOf<List<TransportCard>>(emptyList())
    var historyUids by mutableStateOf<List<String>>(emptyList())
    var displayUids by mutableStateOf<List<String>>(emptyList())
    var travelHistory by mutableStateOf<List<TravelRecord>>(emptyList())

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
    var showSettingsDialog by mutableStateOf(false)

    var isCardCorrupted by mutableStateOf(false)
    var isNormalizing by mutableStateOf(false)
    var isRepairSuccess by mutableStateOf(false)

    var showTravelHistoryOnly by mutableStateOf(true)

    var refreshTrigger by mutableStateOf(0)

    var isProMode by mutableStateOf(false)
        private set

    fun toggleProMode() {
        isProMode = !isProMode
        repository.setProModeEnabled(isProMode)
    }

    fun exportData(): String = repository.exportData()

    fun importData(json: String): Boolean {
        val success = repository.importData(json)
        if (success) loadDataAndRefreshDisplay()
        return success
    }

    fun clearAllData() {
        repository.clearAllData()
        loadDataAndRefreshDisplay()
    }

    init {
        isProMode = repository.isProModeEnabled()
        loadDataAndRefreshDisplay()
    }

    fun loadDataAndRefreshDisplay() {
        val cards = repository.getSavedCards()
        val allUids = repository.getAllHistoryUids()

        val displayList = mutableListOf<String>()
        // Primero las tarjetas guardadas
        cards.forEach { card ->
            val fullUid = allUids.find { it.startsWith(card.uidPrefix) } ?: card.uidPrefix
            if (!displayList.contains(fullUid)) displayList.add(fullUid)
        }
        // Luego los UIDs que solo están en el historial
        allUids.forEach { uid ->
            val isSaved = cards.any { uid.startsWith(it.uidPrefix) }
            if (!isSaved && !displayList.contains(uid)) displayList.add(uid)
        }
        
        savedCards = cards
        historyUids = allUids
        displayUids = displayList
        refreshTrigger++ // Aseguramos que se dispare la recomposición
    }

    fun saveNewCard(card: TransportCard) {
        val existingIndex = savedCards.indexOfFirst { it.uidPrefix == card.uidPrefix }
        val newList = if (existingIndex != -1) {
            savedCards.toMutableList().apply {
                this[existingIndex] = card
            }
        } else {
            savedCards + card
        }
        repository.saveCardList(newList)
        loadDataAndRefreshDisplay()
    }

    fun loadHistoryForUid(uid: String): List<HistoryEntry> {
        return repository.loadHistoryForUid(uid)
    }

    fun loadTravelsFromDb(uid: String) {
        val savedTravels = repository.loadTravelHistory(uid)
        travelHistory = savedTravels.sortedByDescending { it.timestamp }.toList()
    }

    fun updateTravelHistory(uid: String, history: List<TravelRecord>) {
        Log.d("UI_DEBUG", "=== ENVIANDO VIAJES A LA PANTALLA ===")
        history.forEach { record ->
            val formatter = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val dateStr = formatter.format(java.util.Date(record.timestamp))

            Log.d("UI_DEBUG", "Viaje Pintado -> Fecha: $dateStr | Medio: ${if(record.isMetro) "Metro" else "Bus"} | Importe: -${record.amountPaid}€")
        }
        Log.d("UI_DEBUG", "=======================================")

        travelHistory = history.sortedByDescending { it.timestamp }.toList()

        repository.saveTravelHistory(uid, history)
    }

    fun addToHistory(uid: String, balance: Double) {
        android.util.Log.d("MainViewModel", "addToHistory para $uid: balance=$balance")
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

    fun deleteCardConfig(uidPrefix: String, deleteHistory: Boolean = true) {
        val newList = savedCards.filter { it.uidPrefix != uidPrefix }
        repository.saveCardList(newList)
        if (deleteHistory) {
            // También eliminamos el historial asociado para que desaparezca del carrusel por completo
            val allUids = repository.getAllHistoryUids()
            allUids.filter { it.startsWith(uidPrefix) }.forEach {
                repository.deleteFullHistory(it)
            }
        }
        loadDataAndRefreshDisplay()
    }

    fun migrateHistory(oldUid: String, newUid: String) {
        repository.migrateHistory(oldUid, newUid)
        loadDataAndRefreshDisplay()
    }

    fun isDisclaimerAccepted(): Boolean = repository.isDisclaimerAccepted()
    
    fun setDisclaimerAccepted(accepted: Boolean) = repository.setDisclaimerAccepted(accepted)

    fun normalizeCard() {
        isNormalizing = true
    }
}

// Simple property delegate for Float since mutableFloatStateOf is not always available in older Compose
private fun mutableFloatStateOf(value: Float) = mutableStateOf(value)
