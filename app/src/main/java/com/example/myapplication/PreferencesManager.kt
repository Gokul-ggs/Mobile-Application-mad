package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("study_genius_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_QUIZ_HISTORY = "quiz_history_json"
        private const val KEY_FLASHCARD_DECKS = "flashcard_decks_json"
    }

    // --- API Key ---
    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    // --- Quiz History ---
    fun getQuizHistory(): List<QuizHistoryItem> {
        val json = prefs.getString(KEY_QUIZ_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<QuizHistoryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveQuizHistoryItem(item: QuizHistoryItem) {
        val current = getQuizHistory().toMutableList()
        current.add(0, item) // prepend newest
        // Keep max 50 items
        val trimmed = if (current.size > 50) current.take(50) else current
        prefs.edit().putString(KEY_QUIZ_HISTORY, gson.toJson(trimmed)).apply()
    }

    fun clearQuizHistory() {
        prefs.edit().remove(KEY_QUIZ_HISTORY).apply()
    }

    // --- Flashcard Decks ---
    fun getFlashcardDecks(): List<FlashcardDeck> {
        val json = prefs.getString(KEY_FLASHCARD_DECKS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<FlashcardDeck>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFlashcardDeck(deck: FlashcardDeck) {
        val current = getFlashcardDecks().toMutableList()
        current.add(0, deck)
        val trimmed = if (current.size > 30) current.take(30) else current
        prefs.edit().putString(KEY_FLASHCARD_DECKS, gson.toJson(trimmed)).apply()
    }

    fun deleteFlashcardDeck(deckId: String) {
        val current = getFlashcardDecks().filterNot { it.id == deckId }
        prefs.edit().putString(KEY_FLASHCARD_DECKS, gson.toJson(current)).apply()
    }
}
