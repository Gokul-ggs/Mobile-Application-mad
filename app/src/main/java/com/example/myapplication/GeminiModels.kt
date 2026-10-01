package com.example.myapplication

import java.util.UUID

// --- Gemini API Request / Response Models ---

data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

data class Content(
    val role: String? = null,
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

data class GeminiResponse(
    val candidates: List<Candidate>?
)

data class Candidate(
    val content: Content?
)

// --- Chat Models ---

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// --- Quiz Models ---

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class QuizResponse(
    val questions: List<QuizQuestion>
)

data class QuizHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val topic: String,
    val difficulty: String,
    val score: Int,
    val totalQuestions: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val questions: List<QuizQuestion>,
    val userAnswers: Map<Int, Int>
)

enum class QuizDifficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard")
}

// --- Flashcard Models ---

data class FlashcardItem(
    val front: String,
    val back: String,
    val hint: String? = null
)

data class FlashcardDeck(
    val id: String = UUID.randomUUID().toString(),
    val topic: String,
    val cards: List<FlashcardItem>,
    val timestamp: Long = System.currentTimeMillis()
)

data class FlashcardResponse(
    val cards: List<FlashcardItem>
)
