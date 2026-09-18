package com.jarvis.ai

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jarvis.ai.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), VoiceManager.VoiceListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var voiceManager: VoiceManager
    private lateinit var aiBrain: AIBrain
    private lateinit var chatAdapter: ChatAdapter

    private var corePulseAnimator: ObjectAnimator? = null
    private var glowAnimator: ObjectAnimator? = null
    private var isVoiceModeActive = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
            startVoiceMode()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aiBrain = AIBrain()
        voiceManager = VoiceManager(this, this)
        setupChat()
        setupClickListeners()
        setupCoreAnimation()
        updateStatus("Ready")

        chatAdapter.addAiMessage(getString(R.string.jarvis_greeting))
        scrollToBottom()
    }

    private fun setupChat() {
        chatAdapter = ChatAdapter()
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnMic.setOnClickListener {
            if (isVoiceModeActive) {
                stopVoiceMode()
            } else {
                checkPermissionAndStartVoice()
            }
        }

        binding.btnStop.setOnClickListener {
            stopVoiceMode()
        }

        binding.btnSend.setOnClickListener {
            sendTextMessage()
        }

        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTextMessage()
                true
            } else false
        }
    }

    private fun checkPermissionAndStartVoice() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startVoiceMode()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                AlertDialog.Builder(this)
                    .setTitle("Microphone Permission")
                    .setMessage(getString(R.string.permission_rationale))
                    .setPositiveButton("Grant") { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startVoiceMode() {
        if (!voiceManager.isSpeechRecognitionAvailable()) {
            Toast.makeText(this, getString(R.string.speech_not_available), Toast.LENGTH_LONG).show()
            return
        }
        isVoiceModeActive = true
        binding.btnStop.visibility = View.VISIBLE
        binding.btnMic.alpha = 0.5f
        voiceManager.startContinuousMode()
        startListeningAnimation()
    }

    private fun stopVoiceMode() {
        isVoiceModeActive = false
        voiceManager.stopContinuousMode()
        binding.btnStop.visibility = View.GONE
        binding.btnMic.alpha = 1.0f
        stopAllAnimations()
        updateStatus("Ready")
        startIdleCoreAnimation()
    }

    private fun sendTextMessage() {
        val text = binding.etMessage.text?.toString()?.trim()
        if (text.isNullOrEmpty()) return

        binding.etMessage.setText("")
        processUserInput(text)
    }

    private fun processUserInput(text: String) {
        chatAdapter.addUserMessage(text)
        scrollToBottom()

        updateStatus("Thinking...")
        startThinkingAnimation()

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                aiBrain.process(text)
            }

            chatAdapter.addAiMessage(response)
            scrollToBottom()
            voiceManager.speak(response)
        }
    }

    private fun scrollToBottom() {
        binding.rvChat.post {
            val pos = chatAdapter.getLastPosition()
            if (pos >= 0) binding.rvChat.smoothScrollToPosition(pos)
        }
    }

    // ──────────────────── VoiceListener callbacks ────────────────────

    override fun onListeningStarted() {
        runOnUiThread {
            updateStatus("Listening...")
            startListeningAnimation()
            binding.ivMicIcon.visibility = View.VISIBLE
        }
    }

    override fun onPartialResult(text: String) {
        runOnUiThread {
            binding.etMessage.setText(text)
            binding.etMessage.setSelection(text.length)
        }
    }

    override fun onFinalResult(text: String) {
        runOnUiThread {
            binding.etMessage.setText("")
            processUserInput(text)
        }
    }

    override fun onSpeechError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            if (!isVoiceModeActive) {
                updateStatus("Ready")
                stopAllAnimations()
                startIdleCoreAnimation()
            }
        }
    }

    override fun onSpeakingStarted() {
        runOnUiThread {
            updateStatus("Speaking...")
            startSpeakingAnimation()
            binding.ivMicIcon.visibility = View.GONE
        }
    }

    override fun onSpeakingFinished() {
        runOnUiThread {
            if (isVoiceModeActive) {
                updateStatus("Listening...")
                startListeningAnimation()
            } else {
                updateStatus("Ready")
                stopAllAnimations()
                startIdleCoreAnimation()
            }
        }
    }

    override fun onTtsError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPermissionRequired() {
        runOnUiThread {
            checkPermissionAndStartVoice()
        }
    }

    override fun onPermissionDenied() {
        runOnUiThread {
            showPermissionDeniedDialog()
        }
    }

    override fun onStatusChanged(status: String) {
        runOnUiThread {
            updateStatus(status)
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(getString(R.string.permission_denied))
            .setPositiveButton("Retry") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton("Cancel") { _, _ ->
                stopVoiceMode()
            }
            .setCancelable(false)
            .show()
    }

    // ──────────────────── UI Status & Animations ────────────────────

    private fun updateStatus(status: String) {
        binding.tvStatus.text = status
        val color = when {
            status.contains("Listening", ignoreCase = true) -> R.color.status_listening
            status.contains("Thinking", ignoreCase = true) -> R.color.status_thinking
            status.contains("Speaking", ignoreCase = true) -> R.color.status_speaking
            else -> R.color.status_ready
        }
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, color))
    }

    private fun setupCoreAnimation() {
        startIdleCoreAnimation()
    }

    private fun startIdleCoreAnimation() {
        stopAllAnimations()
        corePulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.coreCircle,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f, 1f)
        ).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        glowAnimator = ObjectAnimator.ofFloat(binding.glowRing, View.ALPHA, 0.25f, 0.55f, 0.25f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startListeningAnimation() {
        stopAllAnimations()
        corePulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.coreCircle,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f, 1f)
        ).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        glowAnimator = ObjectAnimator.ofFloat(binding.glowRing, View.ALPHA, 0.4f, 0.9f, 0.4f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        ObjectAnimator.ofPropertyValuesHolder(
            binding.innerPulse,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.8f, 1.2f, 0.8f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.8f, 1.2f, 0.8f)
        ).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun startThinkingAnimation() {
        stopAllAnimations()
        corePulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.coreCircle,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.92f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.92f, 1f)
        ).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        glowAnimator = ObjectAnimator.ofFloat(binding.glowRing, View.ALPHA, 0.3f, 0.7f, 0.3f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun startSpeakingAnimation() {
        stopAllAnimations()
        corePulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.coreCircle,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f, 1f)
        ).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        glowAnimator = ObjectAnimator.ofFloat(binding.glowRing, View.ALPHA, 0.5f, 1.0f, 0.5f).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        ObjectAnimator.ofPropertyValuesHolder(
            binding.innerPulse,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.7f, 1.3f, 0.7f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.7f, 1.3f, 0.7f)
        ).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopAllAnimations() {
        corePulseAnimator?.cancel()
        glowAnimator?.cancel()
        binding.coreCircle.scaleX = 1f
        binding.coreCircle.scaleY = 1f
        binding.innerPulse.scaleX = 1f
        binding.innerPulse.scaleY = 1f
        binding.glowRing.alpha = 0.4f
        binding.ivMicIcon.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        if (isVoiceModeActive) {
            voiceManager.stopListening()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isVoiceModeActive && voiceManager.hasRecordPermission()) {
            voiceManager.startListening()
        }
    }

    override fun onDestroy() {
        voiceManager.release()
        stopAllAnimations()
        super.onDestroy()
    }
}
