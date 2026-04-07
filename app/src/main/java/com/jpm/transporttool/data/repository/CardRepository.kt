package com.jpm.transporttool.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.jpm.transporttool.data.model.CardType
import com.jpm.transporttool.data.model.HistoryEntry
import com.jpm.transporttool.data.model.TransportCard
import org.json.JSONArray
import org.json.JSONObject

class CardRepository(context: Context) {
    private val appPrefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val historyPrefs: SharedPreferences = context.getSharedPreferences("history_prefs", Context.MODE_PRIVATE)

    fun getSavedCards(): List<TransportCard> {
        val json = appPrefs.getString("cards", "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<TransportCard>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                TransportCard(
                    obj.getString("name"),
                    obj.getString("prefix"),
                    obj.getString("keyB"),
                    obj.optInt("color", -15102190),
                    CardType.valueOf(obj.optString("type", "UNKNOWN"))
                )
            )
        }
        return list
    }

    fun saveCardList(cards: List<TransportCard>) {
        val array = JSONArray()
        cards.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("prefix", it.uidPrefix)
            obj.put("keyB", it.keyB)
            obj.put("color", it.color)
            obj.put("type", it.type.name)
            array.put(obj)
        }
        appPrefs.edit().putString("cards", array.toString()).apply()
    }

    fun loadHistoryForUid(uid: String): List<HistoryEntry> {
        val json = historyPrefs.getString(uid, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<HistoryEntry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(HistoryEntry(obj.getLong("t"), obj.getDouble("b")))
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun addToHistory(uid: String, balance: Double) {
        val history = loadHistoryForUid(uid).toMutableList()
        history.add(0, HistoryEntry(System.currentTimeMillis(), balance))
        val array = JSONArray()
        history.take(30).forEach {
            val obj = JSONObject()
            obj.put("t", it.timestamp)
            obj.put("b", it.balance)
            array.put(obj)
        }
        historyPrefs.edit().putString(uid, array.toString()).apply()
    }

    fun deleteHistoryEntry(uid: String, entry: HistoryEntry) {
        val history = loadHistoryForUid(uid).filter { it != entry }
        if (history.isEmpty()) {
            historyPrefs.edit().remove(uid).apply()
        } else {
            val array = JSONArray()
            history.forEach {
                val obj = JSONObject()
                obj.put("t", it.timestamp)
                obj.put("b", it.balance)
                array.put(obj)
            }
            historyPrefs.edit().putString(uid, array.toString()).apply()
        }
    }

    fun deleteFullHistory(uid: String) {
        historyPrefs.edit().remove(uid).apply()
    }
    
    fun getAllHistoryUids(): List<String> {
        return historyPrefs.all.keys.toList().sortedByDescending { 
            loadHistoryForUid(it).firstOrNull()?.timestamp ?: 0L 
        }
    }

    fun isDisclaimerAccepted(): Boolean {
        return appPrefs.getBoolean("disclaimer_accepted", false)
    }

    fun setDisclaimerAccepted(accepted: Boolean) {
        appPrefs.edit().putBoolean("disclaimer_accepted", accepted).apply()
    }

    fun isProModeEnabled(): Boolean {
        return appPrefs.getBoolean("pro_mode", false)
    }

    fun setProModeEnabled(enabled: Boolean) {
        appPrefs.edit().putBoolean("pro_mode", enabled).apply()
    }
}
