package com.jarvis.ai

import kotlinx.coroutines.delay
import java.util.Locale

class AIBrain {

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    suspend fun process(userMessage: String): String {
        delay(900 + (userMessage.length * 15L).coerceAtMost(1200))

        val normalized = userMessage.lowercase(Locale.getDefault()).trim()
        val hasHindi = userMessage.any { it in '\u0900'..'\u097F' }
        val hasEnglish = userMessage.any { it in 'a'..'z' || it in 'A'..'Z' }

        val response = when {
            normalized.contains("hello") || normalized.contains("hi") ||
                    normalized.contains("namaste") || normalized.contains("नमस्ते") ||
                    normalized.contains("hey jarvis") -> {
                if (hasHindi) "नमस्ते! मैं जार्विस हूँ। आपकी कैसे मदद कर सकता हूँ?"
                else "Hello! I am JARVIS. How can I assist you today?"
            }

            normalized.contains("time") || normalized.contains("समय") -> {
                val time = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(java.util.Date())
                if (hasHindi) "अभी समय है $time"
                else "The current time is $time"
            }

            normalized.contains("weather") || normalized.contains("मौसम") -> {
                if (hasHindi) "आज मौसम साफ है। तापमान लगभग 28°C है।"
                else "Today the weather is clear. Temperature is around 28°C."
            }

            normalized.contains("who are you") || normalized.contains("तुम कौन हो") ||
                    normalized.contains("what is your name") -> {
                if (hasHindi) "मैं जार्विस हूँ, आपका व्यक्तिगत AI सहायक।"
                else "I am JARVIS, your personal AI assistant."
            }

            normalized.contains("thank") || normalized.contains("धन्यवाद") ||
                    normalized.contains("shukriya") -> {
                if (hasHindi) "आपका स्वागत है! और कुछ चाहिए तो बताइए।"
                else "You're welcome! Let me know if you need anything else."
            }

            else -> {
                if (hasHindi && !hasEnglish) {
                    "आपने कहा: \"$userMessage\"। मैं समझ गया। अभी मैं डेमो मोड में हूँ, लेकिन असली AI बैकएंड से जुड़ने के बाद मैं और बेहतर जवाब दूंगा।"
                } else if (hasEnglish && !hasHindi) {
                    "You said: \"$userMessage\". I understand. Currently running in demo mode. Once connected to the real AI backend I will give smarter answers."
                } else {
                    "Got it! आपने कहा: \"$userMessage\". I understood the mixed Hindi-English. Demo mode active – real AI brain will handle this better soon."
                }
            }
        }

        conversationHistory.add(userMessage to response)
        return response
    }

    fun getHistory(): List<Pair<String, String>> = conversationHistory.toList()

    fun clearHistory() {
        conversationHistory.clear()
    }
}
