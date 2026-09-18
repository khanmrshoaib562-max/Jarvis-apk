package com.jarvis.ai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val listener: VoiceListener
) : TextToSpeech.OnInitListener {

    interface VoiceListener {
        fun onListeningStarted()
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onSpeechError(message: String)
        fun onSpeakingStarted()
        fun onSpeakingFinished()
        fun onTtsError(message: String)
        fun onPermissionRequired()
        fun onPermissionDenied()
        fun onStatusChanged(status: String)
    }

    companion object {
        private const val TAG = "VoiceManager"
        const val REQUEST_RECORD_AUDIO = 1001
        private const val SPEECH_TIMEOUT_MS = 8000L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isListening = false
    private var isSpeaking = false
    private var continuousMode = false
    private val handler = Handler(Looper.getMainLooper())
    private var speechTimeoutRunnable: Runnable? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.ENGLISH)
            }
            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    handler.post {
                        listener.onSpeakingStarted()
                        listener.onStatusChanged("Speaking...")
                    }
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    handler.post {
                        listener.onSpeakingFinished()
                        if (continuousMode) {
                            startListening()
                        } else {
                            listener.onStatusChanged("Ready")
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    handler.post {
                        listener.onTtsError("TTS error occurred")
                        listener.onStatusChanged("Ready")
                        if (continuousMode) {
                            startListening()
                        }
                    }
                }
            })
            isTtsReady = true
            Log.d(TAG, "TTS initialized successfully")
        } else {
            isTtsReady = false
            listener.onTtsError(context.getString(R.string.tts_not_available))
            Log.e(TAG, "TTS initialization failed")
        }
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestRecordPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
    }

    fun onPermissionResult(grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "RECORD_AUDIO granted")
        } else {
            listener.onPermissionDenied()
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startContinuousMode() {
        if (!hasRecordPermission()) {
            listener.onPermissionRequired()
            return
        }
        if (!isSpeechRecognitionAvailable()) {
            listener.onSpeechError(context.getString(R.string.speech_not_available))
            return
        }
        continuousMode = true
        startListening()
    }

    fun stopContinuousMode() {
        continuousMode = false
        stopListening()
        stopSpeaking()
        listener.onStatusChanged("Ready")
    }

    fun isContinuousModeActive(): Boolean = continuousMode

    fun startListening() {
        if (isListening || isSpeaking) return
        if (!hasRecordPermission()) {
            listener.onPermissionRequired()
            return
        }
        if (!isSpeechRecognitionAvailable()) {
            listener.onSpeechError(context.getString(R.string.speech_not_available))
            return
        }

        destroyRecognizer()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            listener.onListeningStarted()
            listener.onStatusChanged("Listening...")
            scheduleSpeechTimeout()
            Log.d(TAG, "Started listening")
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            isListening = false
            listener.onSpeechError("Failed to start speech recognition: ${e.message}")
        }
    }

    private fun scheduleSpeechTimeout() {
        cancelSpeechTimeout()
        speechTimeoutRunnable = Runnable {
            if (isListening) {
                Log.d(TAG, "Speech timeout – stopping")
                stopListening()
                if (continuousMode) {
                    handler.postDelayed({ startListening() }, 800)
                }
            }
        }
        handler.postDelayed(speechTimeoutRunnable!!, SPEECH_TIMEOUT_MS)
    }

    private fun cancelSpeechTimeout() {
        speechTimeoutRunnable?.let { handler.removeCallbacks(it) }
        speechTimeoutRunnable = null
    }

    fun stopListening() {
        cancelSpeechTimeout()
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "stopListening error", e)
            }
            isListening = false
        }
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "destroyRecognizer error", e)
        }
        speechRecognizer = null
        isListening = false
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech")
            cancelSpeechTimeout()
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            cancelSpeechTimeout()
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> context.getString(R.string.error_network)
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> context.getString(R.string.error_speech)
            }
            Log.e(TAG, "Speech error code=$error : $message")

            if (continuousMode && (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            ) {
                handler.postDelayed({ startListening() }, 600)
            } else {
                listener.onSpeechError(message)
                if (continuousMode) {
                    handler.postDelayed({ startListening() }, 1200)
                } else {
                    listener.onStatusChanged("Ready")
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            cancelSpeechTimeout()
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim()
            if (!text.isNullOrEmpty()) {
                Log.d(TAG, "Final result: $text")
                listener.onFinalResult(text)
            } else {
                if (continuousMode) {
                    handler.postDelayed({ startListening() }, 600)
                } else {
                    listener.onStatusChanged("Ready")
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim()
            if (!text.isNullOrEmpty()) {
                listener.onPartialResult(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun speak(text: String) {
        if (!isTtsReady || tts == null) {
            listener.onTtsError(context.getString(R.string.tts_not_available))
            handler.post {
                listener.onSpeakingFinished()
                if (continuousMode) startListening()
            }
            return
        }

        stopListening()

        val hasHindi = text.any { it in '\u0900'..'\u097F' }
        val locale = if (hasHindi) Locale("hi", "IN") else Locale.ENGLISH
        tts?.language = locale

        val utteranceId = "jarvis_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
        }
    }

    fun isListening(): Boolean = isListening
    fun isSpeaking(): Boolean = isSpeaking

    fun release() {
        continuousMode = false
        stopListening()
        stopSpeaking()
        cancelSpeechTimeout()
        tts?.shutdown()
        tts = null
        isTtsReady = false
        destroyRecognizer()
    }
}
