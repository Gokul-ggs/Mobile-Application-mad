package com.example.myapplication

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val gson = Gson()

    // --- API Key State ---
    private val _apiKey = MutableStateFlow(prefs.getApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    fun setShowSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun saveApiKey(newKey: String) {
        prefs.saveApiKey(newKey)
        _apiKey.value = newKey.trim()
    }

    private fun getActiveApiKey(): String {
        val key = _apiKey.value.ifBlank { ApiClient.defaultApiKey }
        return key.trim()
    }

    // --- Chat State ---
    val chatMessages = mutableStateListOf<ChatMessage>()
    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    // --- Quiz State ---
    val quizQuestions = mutableStateListOf<QuizQuestion>()
    val userAnswers = mutableStateMapOf<Int, Int>()

    private val _isQuizLoading = MutableStateFlow(false)
    val isQuizLoading: StateFlow<Boolean> = _isQuizLoading.asStateFlow()

    private val _isQuizSubmitted = MutableStateFlow(false)
    val isQuizSubmitted: StateFlow<Boolean> = _isQuizSubmitted.asStateFlow()

    private val _quizScore = MutableStateFlow(0)
    val quizScore: StateFlow<Int> = _quizScore.asStateFlow()

    private val _quizError = MutableStateFlow<String?>(null)
    val quizError: StateFlow<String?> = _quizError.asStateFlow()

    private val _quizTimerSeconds = MutableStateFlow<Int?>(null)
    val quizTimerSeconds: StateFlow<Int?> = _quizTimerSeconds.asStateFlow()

    private var timerJob: Job? = null
    private var currentQuizTopic = ""
    private var currentQuizDifficulty = QuizDifficulty.MEDIUM

    // --- Flashcards State ---
    private val _isFlashcardsLoading = MutableStateFlow(false)
    val isFlashcardsLoading: StateFlow<Boolean> = _isFlashcardsLoading.asStateFlow()

    private val _flashcardError = MutableStateFlow<String?>(null)
    val flashcardError: StateFlow<String?> = _flashcardError.asStateFlow()

    private val _currentDeck = MutableStateFlow<FlashcardDeck?>(null)
    val currentDeck: StateFlow<FlashcardDeck?> = _currentDeck.asStateFlow()

    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex.asStateFlow()

    private val _isCardFlipped = MutableStateFlow(false)
    val isCardFlipped: StateFlow<Boolean> = _isCardFlipped.asStateFlow()

    private val _masteredCount = MutableStateFlow(0)
    val masteredCount: StateFlow<Int> = _masteredCount.asStateFlow()

    private val _learningCount = MutableStateFlow(0)
    val learningCount: StateFlow<Int> = _learningCount.asStateFlow()

    private val _isDeckCompleted = MutableStateFlow(false)
    val isDeckCompleted: StateFlow<Boolean> = _isDeckCompleted.asStateFlow()

    private val _savedDecks = MutableStateFlow<List<FlashcardDeck>>(emptyList())
    val savedDecks: StateFlow<List<FlashcardDeck>> = _savedDecks.asStateFlow()

    // --- History State ---
    private val _quizHistory = MutableStateFlow<List<QuizHistoryItem>>(emptyList())
    val quizHistory: StateFlow<List<QuizHistoryItem>> = _quizHistory.asStateFlow()

    // --- Navigation & Cross-Tab Bridges ---
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _prefilledTopic = MutableStateFlow("")
    val prefilledTopic: StateFlow<String> = _prefilledTopic.asStateFlow()

    fun navigateToTab(tabIndex: Int, topic: String? = null) {
        if (!topic.isNullOrBlank()) {
            _prefilledTopic.value = topic
        }
        _selectedTab.value = tabIndex
    }

    fun clearPrefilledTopic() {
        _prefilledTopic.value = ""
    }

    init {
        loadHistory()
        loadSavedDecks()
        if (chatMessages.isEmpty()) {
            chatMessages.add(
                ChatMessage(
                    text = "Hello! 👋 I'm your AI Study Assistant.\n\nAsk me any study question, request conceptual breakdowns, or test yourself with an instant AI Quiz!",
                    isUser = false
                )
            )
        }
    }

    fun loadHistory() {
        _quizHistory.value = prefs.getQuizHistory()
    }

    fun clearQuizHistory() {
        prefs.clearQuizHistory()
        _quizHistory.value = emptyList()
    }

    fun loadSavedDecks() {
        _savedDecks.value = prefs.getFlashcardDecks()
    }

    private suspend fun executeGeminiCall(apiKey: String, request: GeminiRequest): GeminiResponse {
        val candidateModels = listOf("gemini-1.5-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-flash-lite")
        var lastException: Exception? = null

        for (model in candidateModels) {
            try {
                val fullUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                return ApiClient.api.generateContent(
                    url = fullUrl,
                    request = request
                )
            } catch (e: Exception) {
                lastException = e
                val msg = e.message ?: ""
                // If 404 or model not found, try next candidate model
                if (msg.contains("404") || msg.contains("not found", ignoreCase = true)) {
                    continue
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: IllegalStateException("Unable to connect to Gemini API.")
    }

    // --- Chat Logic (Multi-turn conversation context) ---

    fun sendChatMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isBlank()) return

        val key = getActiveApiKey()
        if (key.isBlank()) {
            _showSettingsDialog.value = true
            _chatError.value = "Please enter your Gemini API Key in Settings to chat."
            return
        }

        _chatError.value = null
        val userMsg = ChatMessage(text = trimmed, isUser = true)
        chatMessages.add(userMsg)
        _isChatLoading.value = true

        viewModelScope.launch {
            try {
                // Construct multi-turn contents
                val contents = mutableListOf<Content>()

                // System instruction / context setup as first turn
                contents.add(
                    Content(
                        role = "user",
                        parts = listOf(
                            Part(
                                text = "You are StudyGenius AI, an encouraging, clear, and top-tier study tutor. " +
                                        "Format explanations with clear structure, bold headings, bullet points, and concise formulas or code where relevant."
                            )
                        )
                    )
                )
                contents.add(
                    Content(
                        role = "model",
                        parts = listOf(Part(text = "Understood! I will serve as a clear, structured, and encouraging study tutor for any subject."))
                    )
                )

                // Add past turns (limit to last 10 messages for context)
                val recentHistory = chatMessages.takeLast(10)
                for (msg in recentHistory) {
                    contents.add(
                        Content(
                            role = if (msg.isUser) "user" else "model",
                            parts = listOf(Part(text = msg.text))
                        )
                    )
                }

                val request = GeminiRequest(
                    contents = contents,
                    generationConfig = GenerationConfig(temperature = 0.7f)
                )

                val response = executeGeminiCall(key, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "I couldn't generate a response. Please try again."

                chatMessages.add(ChatMessage(text = replyText, isUser = false))
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: ""
                _chatError.value = if (errorBody.contains("API_KEY_INVALID", ignoreCase = true)) {
                    "Invalid API Key. Please check the key in Settings."
                } else if (errorBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                    "Rate limit reached. Please wait a moment."
                } else {
                    "Error (${e.code()}): ${e.message()}"
                }
            } catch (e: Exception) {
                _chatError.value = "Error: ${e.localizedMessage ?: "Failed to connect to Gemini API. Check your API key or network."}"
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun clearChat() {
        chatMessages.clear()
        chatMessages.add(
            ChatMessage(
                text = "Chat cleared! How can I help you study next?",
                isUser = false
            )
        )
        _chatError.value = null
    }

    // --- Quiz Logic with Structured JSON ---

    fun generateQuiz(
        topic: String,
        difficulty: QuizDifficulty,
        numQuestions: Int,
        timedMinutes: Int? = null
    ) {
        val trimmedTopic = topic.trim()
        if (trimmedTopic.isBlank()) return

        val key = getActiveApiKey()
        if (key.isBlank()) {
            _showSettingsDialog.value = true
            _quizError.value = "Please enter your Gemini API Key in Settings to generate quizzes."
            return
        }

        resetQuiz()
        currentQuizTopic = trimmedTopic
        currentQuizDifficulty = difficulty
        _isQuizLoading.value = true
        _quizError.value = null

        viewModelScope.launch {
            try {
                val prompt = """
                    Generate a high-quality educational multiple-choice quiz on the topic: "$trimmedTopic".
                    Difficulty level: ${difficulty.label}.
                    Total number of questions: $numQuestions.
                    
                    You MUST respond with a valid JSON object matching this exact structure:
                    {
                      "questions": [
                        {
                          "question": "Question text here",
                          "options": ["Option A", "Option B", "Option C", "Option D"],
                          "correctIndex": 0,
                          "explanation": "Detailed explanation explaining why the correct option is right and the nuances of the concept."
                        }
                      ]
                    }
                    
                    Requirements:
                    - Exactly 4 options per question.
                    - correctIndex must be an integer from 0 to 3.
                    - Provide a clear, educational explanation for each question.
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(
                        Content(role = "user", parts = listOf(Part(text = prompt)))
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.3f
                    )
                )

                val response = executeGeminiCall(key, request)
                val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw IllegalStateException("Empty response from Gemini API")

                val cleanJson = rawJson
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val parsed = gson.fromJson(cleanJson, QuizResponse::class.java)
                if (parsed?.questions.isNullOrEmpty()) {
                    throw IllegalStateException("Failed to parse quiz questions from response.")
                }

                quizQuestions.addAll(parsed.questions)

                // Setup timer if requested
                if (timedMinutes != null && timedMinutes > 0) {
                    val totalSeconds = timedMinutes * 60
                    _quizTimerSeconds.value = totalSeconds
                    startQuizTimer()
                }

            } catch (e: Exception) {
                _quizError.value = "Quiz Generation Failed: ${e.localizedMessage ?: "Please check your network and API key."}"
            } finally {
                _isQuizLoading.value = false
            }
        }
    }

    private fun startQuizTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while ((_quizTimerSeconds.value ?: 0) > 0 && !_isQuizSubmitted.value) {
                delay(1000)
                _quizTimerSeconds.value = (_quizTimerSeconds.value ?: 1) - 1
            }
            if ((_quizTimerSeconds.value ?: 0) <= 0 && !_isQuizSubmitted.value) {
                submitQuiz()
            }
        }
    }

    fun selectAnswer(questionIndex: Int, optionIndex: Int) {
        if (!_isQuizSubmitted.value) {
            userAnswers[questionIndex] = optionIndex
        }
    }

    fun submitQuiz() {
        if (_isQuizSubmitted.value) return
        timerJob?.cancel()

        var calculatedScore = 0
        quizQuestions.forEachIndexed { index, q ->
            if (userAnswers[index] == q.correctIndex) {
                calculatedScore++
            }
        }

        _quizScore.value = calculatedScore
        _isQuizSubmitted.value = true

        // Save to History
        if (quizQuestions.isNotEmpty()) {
            val historyItem = QuizHistoryItem(
                topic = currentQuizTopic,
                difficulty = currentQuizDifficulty.label,
                score = calculatedScore,
                totalQuestions = quizQuestions.size,
                questions = quizQuestions.toList(),
                userAnswers = userAnswers.toMap()
            )
            prefs.saveQuizHistoryItem(historyItem)
            loadHistory()
        }
    }

    fun resetQuiz() {
        timerJob?.cancel()
        timerJob = null
        quizQuestions.clear()
        userAnswers.clear()
        _isQuizSubmitted.value = false
        _quizScore.value = 0
        _quizError.value = null
        _quizTimerSeconds.value = null
    }

    // --- Flashcards Logic ---

    fun generateFlashcards(topic: String, count: Int = 6) {
        val trimmedTopic = topic.trim()
        if (trimmedTopic.isBlank()) return

        val key = getActiveApiKey()
        if (key.isBlank()) {
            _showSettingsDialog.value = true
            _flashcardError.value = "Please enter your Gemini API Key in Settings to generate flashcards."
            return
        }

        _isFlashcardsLoading.value = true
        _flashcardError.value = null
        resetFlashcardSession()

        viewModelScope.launch {
            try {
                val prompt = """
                    Generate $count high-yield study flashcards on the topic: "$trimmedTopic".
                    Each flashcard must have a concise front question/term, a clear explanatory back, and an optional quick mnemonic or hint.
                    
                    You MUST respond with a JSON object matching this exact schema:
                    {
                      "cards": [
                        {
                          "front": "Term / Question / Core Concept",
                          "back": "Clear definition / explanation / breakdown",
                          "hint": "Helpful mnemonic or memory clue"
                        }
                      ]
                    }
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(
                        Content(role = "user", parts = listOf(Part(text = prompt)))
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.4f
                    )
                )

                val response = executeGeminiCall(key, request)
                val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw IllegalStateException("Empty response from Gemini API")

                val cleanJson = rawJson
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val parsed = gson.fromJson(cleanJson, FlashcardResponse::class.java)
                if (parsed?.cards.isNullOrEmpty()) {
                    throw IllegalStateException("No cards parsed from Gemini response.")
                }

                val deck = FlashcardDeck(
                    topic = trimmedTopic,
                    cards = parsed.cards
                )
                _currentDeck.value = deck
                prefs.saveFlashcardDeck(deck)
                loadSavedDecks()

            } catch (e: Exception) {
                _flashcardError.value = "Flashcard Generation Failed: ${e.localizedMessage ?: "Check connection and API key."}"
            } finally {
                _isFlashcardsLoading.value = false
            }
        }
    }

    fun flipCard() {
        _isCardFlipped.value = !_isCardFlipped.value
    }

    fun markCardMastered() {
        _masteredCount.value++
        advanceCard()
    }

    fun markCardNeedsReview() {
        _learningCount.value++
        advanceCard()
    }

    private fun advanceCard() {
        val deck = _currentDeck.value ?: return
        _isCardFlipped.value = false
        if (_currentCardIndex.value + 1 < deck.cards.size) {
            _currentCardIndex.value++
        } else {
            _isDeckCompleted.value = true
        }
    }

    fun restartCurrentDeck() {
        _currentCardIndex.value = 0
        _isCardFlipped.value = false
        _masteredCount.value = 0
        _learningCount.value = 0
        _isDeckCompleted.value = false
    }

    fun loadDeck(deck: FlashcardDeck) {
        _currentDeck.value = deck
        restartCurrentDeck()
    }

    fun deleteDeck(deckId: String) {
        prefs.deleteFlashcardDeck(deckId)
        loadSavedDecks()
        if (_currentDeck.value?.id == deckId) {
            _currentDeck.value = null
            resetFlashcardSession()
        }
    }

    fun closeCurrentDeck() {
        _currentDeck.value = null
        resetFlashcardSession()
    }

    fun resetFlashcardSession() {
        _currentCardIndex.value = 0
        _isCardFlipped.value = false
        _masteredCount.value = 0
        _learningCount.value = 0
        _isDeckCompleted.value = false
    }
}
