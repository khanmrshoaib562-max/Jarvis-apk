package com.jarvis.ai

import android.util.Log
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * JARVIS AI Brain - Feature 2 (with Firebase Long-term Memory)
 */
class AIBrain {

    // Short-term conversation context
    private val shortTermHistory = mutableListOf<Message>()
    private val MAX_SHORT_TERM = 12

    // Long-term memory
    private val longTermMemory = ConcurrentHashMap<String, String>()
    private val firebaseMemory = FirebaseMemory()
    private var isMemoryLoaded = false

    data class Message(
        val role: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun process(userMessage: String): String {
        val cleaned = userMessage.trim()
        if (cleaned.isEmpty()) return getEmptyInputResponse(detectLanguage(cleaned))

        // Load long-term memory once (from Firebase)
        if (!isMemoryLoaded) {
            loadMemoryFromFirebase()
        }

        delay(700 + (cleaned.length * 12L).coerceAtMost(1100))

        val lang = detectLanguage(cleaned)
        val normalized = cleaned.lowercase(Locale.getDefault())

        // Update long-term memory if user shares personal info
        extractAndStoreMemory(cleaned, normalized)

        // Handle special intents first
        val special = handleSpecialIntents(cleaned, normalized, lang)
        if (special != null) {
            addToHistory("user", cleaned)
            addToHistory("assistant", special)
            return special
        }

        // Context-aware response
        val contextual = generateContextualResponse(cleaned, normalized, lang)

        addToHistory("user", cleaned)
        addToHistory("assistant", contextual)
        return contextual
    }

    private suspend fun loadMemoryFromFirebase() {
        try {
            val data = firebaseMemory.loadAll()
            longTermMemory.clear()
            longTermMemory.putAll(data)
            isMemoryLoaded = true
            Log.d("AIBrain", "Long-term memory loaded: $data")
        } catch (e: Exception) {
            Log.e("AIBrain", "Failed to load memory from Firebase", e)
            isMemoryLoaded = true
        }
    }

    private suspend fun saveMemoryToFirebase(key: String, value: String) {
        longTermMemory[key] = value
        try {
            firebaseMemory.save(key, value)
        } catch (e: Exception) {
            Log.e("AIBrain", "Failed to save memory", e)
        }
    }

    private fun detectLanguage(text: String): Lang {
        val hasHindi = text.any { it in '\u0900'..'\u097F' }
        val hasEnglish = text.any { it in 'a'..'z' || it in 'A'..'Z' }
        return when {
            hasHindi && hasEnglish -> Lang.MIXED
            hasHindi -> Lang.HINDI
            else -> Lang.ENGLISH
        }
    }

    enum class Lang { HINDI, ENGLISH, MIXED }

    private suspend fun extractAndStoreMemory(original: String, normalized: String) {
        val namePatterns = listOf(
            Regex("""(?:mera naam|my name is|i am|i'm|main)\s+([a-zA-Z\u0900-\u097F]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:naam hai|name is)\s+([a-zA-Z\u0900-\u097F]+)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in namePatterns) {
            val match = pattern.find(normalized)
            if (match != null) {
                val name = match.groupValues[1].replaceFirstChar { it.uppercase() }
                if (name.length in 2..20) {
                    saveMemoryToFirebase("user_name", name)
                }
            }
        }

        if (normalized.contains("mujhe pasand") || normalized.contains("i like") || normalized.contains("i love")) {
            saveMemoryToFirebase("last_preference", original)
        }
    }

    private fun getUserName(): String? = longTermMemory["user_name"]

    private fun handleSpecialIntents(original: String, normalized: String, lang: Lang): String? {

        if (isGreeting(normalized)) {
            val name = getUserName()
            return when (lang) {
                Lang.HINDI -> if (name != null) "नमस्ते $name! कैसे मदद कर सकता हूँ?" else "नमस्ते! मैं जार्विस हूँ। बताइए, कैसे मदद करूँ?"
                Lang.MIXED -> if (name != null) "Namaste $name! Bataiye, kaise help karun?" else "Namaste! Main Jarvis hoon. Bataiye kya chahiye?"
                else -> if (name != null) "Hello $name! How can I help you?" else "Hello! I'm JARVIS. How can I assist you?"
            }
        }

        if (normalized.contains("who are you") || normalized.contains("tum kaun ho") ||
            normalized.contains("tu kaun hai") || normalized.contains("what is your name") ||
            normalized.contains("aap kaun ho") || normalized.contains("jarvis kaun hai")) {
            return when (lang) {
                Lang.HINDI -> "मैं जार्विस हूँ — आपका व्यक्तिगत AI सहायक। मैं बातचीत, सवालों के जवाब, अनुवाद और सुझाव दे सकता हूँ।"
                Lang.MIXED -> "Main Jarvis hoon — aapka personal AI assistant. Main baat-cheet, sawalon ke jawab, translation aur suggestions de sakta hoon."
                else -> "I'm JARVIS, your personal AI assistant. I can chat, answer questions, translate, summarize and give suggestions."
            }
        }

        if (normalized.contains("mera naam kya hai") || normalized.contains("my name") ||
            normalized.contains("what is my name") || normalized.contains("naam kya hai mera")) {
            val name = getUserName()
            return when {
                name != null && lang == Lang.HINDI -> "आपका नाम $name है।"
                name != null && lang == Lang.MIXED -> "Aapka naam $name hai."
                name != null -> "Your name is $name."
                lang == Lang.HINDI -> "आपने अभी तक अपना नाम नहीं बताया।"
                lang == Lang.MIXED -> "Aapne abhi tak apna naam nahi bataya."
                else -> "You haven't told me your name yet."
            }
        }

        if (normalized.contains("time") || normalized.contains("samay") || normalized.contains("kitna baja") ||
            normalized.contains("what's the time") || normalized.contains("current time")) {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            return when (lang) {
                Lang.HINDI -> "अभी समय है $time।"
                Lang.MIXED -> "Abhi time hai $time."
                else -> "The current time is $time."
            }
        }

        if (normalized.contains("date") || normalized.contains("aaj ki tarikh") || normalized.contains("today's date")) {
            val date = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
            return when (lang) {
                Lang.HINDI -> "आज की तारीख $date है।"
                Lang.MIXED -> "Aaj ki date $date hai."
                else -> "Today's date is $date."
            }
        }

        if (normalized.contains("thank") || normalized.contains("dhanyavad") ||
            normalized.contains("shukriya") || normalized.contains("thanks")) {
            return when (lang) {
                Lang.HINDI -> "आपका स्वागत है।"
                Lang.MIXED -> "Aapka swagat hai."
                else -> "You're welcome."
            }
        }

        if (normalized.contains("how are you") || normalized.contains("kaise ho") ||
            normalized.contains("kya haal")) {
            return when (lang) {
                Lang.HINDI -> "मैं बिल्कुल ठीक हूँ। आप कैसे हैं?"
                Lang.MIXED -> "Main bilkul theek hoon. Aap kaise hain?"
                else -> "I'm doing well. How about you?"
            }
        }

        return null
    }

    private fun isGreeting(normalized: String): Boolean {
        val greetings = listOf(
            "hello", "hi", "hey", "namaste", "namaskar", "good morning",
            "good evening", "good afternoon", "hola", "salam", "hey jarvis",
            "hi jarvis", "hello jarvis", "नमस्ते", "हेलो", "हाय"
        )
        return greetings.any { normalized.contains(it) }
    }

    private fun generateContextualResponse(original: String, normalized: String, lang: Lang): String {
        val name = getUserName()

        return when (lang) {
            Lang.HINDI -> {
                val prefix = if (name != null) "$name, " else ""
                "${prefix}मैं समझ गया। आपने कहा — \"$original\"। अभी मैं बेहतर जवाब देने के लिए तैयार हूँ। क्या आप और डिटेल चाहते हैं?"
            }
            Lang.MIXED -> {
                val prefix = if (name != null) "$name, " else ""
                "${prefix}Samajh gaya. Aapne kaha — \"$original\". Main help kar sakta hoon. Aur detail chahiye kya?"
            }
            else -> {
                val prefix = if (name != null) "$name, " else ""
                "${prefix}Got it. You said — \"$original\". I'm ready to help. Want me to go deeper on this?"
            }
        }
    }

    private fun getEmptyInputResponse(lang: Lang): String {
        return when (lang) {
            Lang.HINDI -> "कृपया कुछ लिखें या बोलें।"
            Lang.MIXED -> "Kuch likhiye ya boliye."
            else -> "Please type or say something."
        }
    }

    private fun addToHistory(role: String, content: String) {
        shortTermHistory.add(Message(role, content))
        if (shortTermHistory.size > MAX_SHORT_TERM * 2) {
            while (shortTermHistory.size > MAX_SHORT_TERM) {
                shortTermHistory.removeAt(0)
            }
        }
    }

    fun getHistory(): List<Pair<String, String>> {
        return shortTermHistory
            .windowed(2, 2, partialWindows = false)
            .mapNotNull { window ->
                if (window[0].role == "user" && window[1].role == "assistant") {
                    window[0].content to window[1].content
                } else null
            }
    }

    fun clearHistory() {
        shortTermHistory.clear()
    }

    suspend fun clearLongTermMemory() {
        longTermMemory.clear()
        firebaseMemory.clear()
    }

    fun getLongTermMemorySnapshot(): Map<String, String> = longTermMemory.toMap()
}
