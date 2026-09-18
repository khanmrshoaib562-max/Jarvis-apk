package com.jarvis.ai

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Firebase Long-term Memory for JARVIS
 * Stores important user facts (name, preferences etc.)
 */
class FirebaseMemory {

    private val db: FirebaseFirestore = Firebase.firestore
    private val TAG = "FirebaseMemory"

    // Abhi simple device-based userId use kar rahe hain
    // Baad me Firebase Auth se real userId le sakte ho
    private val userId = "default_user"

    private val memoryRef = db.collection("users")
        .document(userId)
        .collection("memory")
        .document("profile")

    /**
     * Save key-value memory
     */
    suspend fun save(key: String, value: String) {
        try {
            memoryRef.set(
                mapOf(key to value),
                SetOptions.merge()
            ).await()
            Log.d(TAG, "Saved: $key = $value")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving memory", e)
        }
    }

    /**
     * Save multiple fields at once
     */
    suspend fun saveAll(data: Map<String, String>) {
        try {
            memoryRef.set(data, SetOptions.merge()).await()
            Log.d(TAG, "Saved all: $data")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving all memory", e)
        }
    }

    /**
     * Load all long-term memory
     */
    suspend fun loadAll(): Map<String, String> {
        return try {
            val snapshot = memoryRef.get().await()
            if (snapshot.exists()) {
                snapshot.data?.mapValues { it.value.toString() } ?: emptyMap()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading memory", e)
            emptyMap()
        }
    }

    /**
     * Clear all memory (for settings)
     */
    suspend fun clear() {
        try {
            memoryRef.delete().await()
            Log.d(TAG, "Memory cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing memory", e)
        }
    }
}
