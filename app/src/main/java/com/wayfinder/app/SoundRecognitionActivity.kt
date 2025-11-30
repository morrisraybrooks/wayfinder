package com.wayfinder.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.log10

/**
 * Activity that recognizes important environmental sounds.
 * Identifies sounds like sirens, car horns, doorbells, etc.
 */
class SoundRecognitionActivity : BaseAccessibilityActivity() {

    private lateinit var soundInstructionsTextView: TextView
    private lateinit var soundResultTextView: TextView
    private lateinit var startSoundRecognitionButton: Button
    private lateinit var stopSoundRecognitionButton: Button
    private lateinit var backButton: Button
    
    private var isListening = false
    private var audioRecord: AudioRecord? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val AUDIO_PERMISSION_CODE = 102
    
    // Audio recording parameters
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 3
    
    // Sound detection thresholds
    private val AMBIENT_THRESHOLD = 65.0 // dB
    private val LOUD_THRESHOLD = 75.0 // dB
    private val VERY_LOUD_THRESHOLD = 85.0 // dB
    
    // Sound patterns (simplified for demonstration)
    private val soundPatterns = mapOf(
        "siren" to SoundPattern(85.0, 0.8, 1.2), // Very loud, oscillating between 0.8-1.2 seconds
        "car horn" to SoundPattern(80.0, 0.3, 0.7), // Loud, short duration 0.3-0.7 seconds
        "doorbell" to SoundPattern(70.0, 0.2, 0.5), // Moderate, very short 0.2-0.5 seconds
        "alarm" to SoundPattern(85.0, 0.5, 1.0), // Very loud, repeating every 0.5-1.0 seconds
        "glass breaking" to SoundPattern(75.0, 0.1, 0.3), // Loud, very short and sharp
        "dog barking" to SoundPattern(75.0, 0.2, 0.6), // Loud, short bursts
        "baby crying" to SoundPattern(75.0, 1.0, 3.0) // Loud, longer duration
    )
    
    // Track the last detected sound to avoid repetitive announcements
    private var lastDetectedSound: String? = null
    private var lastDetectionTime = 0L
    private val DETECTION_COOLDOWN = 5000L // 5 seconds between same sound announcements
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sound_recognition)
        
        // Initialize views
        soundInstructionsTextView = findViewById(R.id.soundInstructionsTextView)
        soundResultTextView = findViewById(R.id.soundResultTextView)
        startSoundRecognitionButton = findViewById(R.id.startSoundRecognitionButton)
        stopSoundRecognitionButton = findViewById(R.id.stopSoundRecognitionButton)
        backButton = findViewById(R.id.backButton)
        
        // Set up button click listeners
        startSoundRecognitionButton.setOnClickListener {
            checkPermissionAndStartListening()
        }
        
        stopSoundRecognitionButton.setOnClickListener {
            stopListening()
        }
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Initially hide the stop button
        stopSoundRecognitionButton.visibility = View.GONE
    }
    
    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.sound_recognition_instructions))
    }
    
    private fun checkPermissionAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                AUDIO_PERMISSION_CODE
            )
        } else {
            startListening()
        }
    }
    
    private fun startListening() {
        if (!isListening) {
            isListening = true
            
            // Update UI
            startSoundRecognitionButton.visibility = View.GONE
            stopSoundRecognitionButton.visibility = View.VISIBLE
            soundResultTextView.text = getString(R.string.listening_for_sounds)
            speak(getString(R.string.listening_for_sounds))
            
            // Start audio recording and analysis
            startAudioRecording()
            
            // Provide haptic feedback
            vibrate()
        }
    }
    
    private fun stopListening() {
        if (isListening) {
            isListening = false
            
            // Update UI
            startSoundRecognitionButton.visibility = View.VISIBLE
            stopSoundRecognitionButton.visibility = View.GONE
            soundResultTextView.text = getString(R.string.sound_recognition_stopped)
            
            // Stop audio recording
            stopAudioRecording()
            
            // Provide haptic feedback
            vibrate()
        }
    }
    
    private fun startAudioRecording() {
        executor.execute {
            // Verify audio permission again before initializing AudioRecord (lint: MissingPermission)
            if (ContextCompat.checkSelfPermission(this@SoundRecognitionActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                mainHandler.post {
                    Toast.makeText(this@SoundRecognitionActivity, "Audio permission is required for sound recognition.", Toast.LENGTH_SHORT).show()
                    stopListening()
                }
                return@execute
            }
            try {
                // Initialize AudioRecord
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
                )
                
                // Check if initialization was successful
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    mainHandler.post {
                        Toast.makeText(this, "Failed to initialize audio recording", Toast.LENGTH_SHORT).show()
                        stopListening()
                    }
                    return@execute
                }
                
                // Start recording
                audioRecord?.startRecording()
                
                // Buffer for audio data
                val audioBuffer = ShortArray(BUFFER_SIZE / 2)
                
                // Variables for sound pattern detection
                var soundStartTime = 0L
                var isSoundDetected = false
                var currentSoundLevel = 0.0
                var soundDuration = 0.0
                
                // Continue while listening
                while (isListening) {
                    // Read audio data
                    val readResult = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    
                    if (readResult > 0) {
                        // Calculate sound level (RMS)
                        val rms = calculateRMS(audioBuffer, readResult)
                        val db = 20 * log10(rms.toDouble())
                        
                        // Detect sound events
                        if (db > AMBIENT_THRESHOLD && !isSoundDetected) {
                            // Sound started
                            isSoundDetected = true
                            soundStartTime = System.currentTimeMillis()
                            currentSoundLevel = db
                        } else if (db > AMBIENT_THRESHOLD && isSoundDetected) {
                            // Sound continuing
                            currentSoundLevel = maxOf(currentSoundLevel, db)
                            soundDuration = (System.currentTimeMillis() - soundStartTime) / 1000.0
                            
                            // Try to identify the sound if it's been going for at least 0.2 seconds
                            if (soundDuration >= 0.2) {
                                identifySound(currentSoundLevel, soundDuration)
                            }
                        } else if (db <= AMBIENT_THRESHOLD && isSoundDetected) {
                            // Sound ended
                            isSoundDetected = false
                            soundDuration = (System.currentTimeMillis() - soundStartTime) / 1000.0
                            
                            // Final identification attempt
                            identifySound(currentSoundLevel, soundDuration)
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(this, "Error in audio recording: ${e.message}", Toast.LENGTH_SHORT).show()
                    stopListening()
                }
            }
        }
    }
    
    private fun stopAudioRecording() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping audio recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun calculateRMS(buffer: ShortArray, readSize: Int): Float {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        val mean = sum / readSize
        return Math.sqrt(mean).toFloat()
    }
    
    private fun identifySound(soundLevel: Double, duration: Double) {
        // Find the best matching sound pattern
        var bestMatch: String? = null
        var bestMatchScore = 0.0
        
        for ((soundName, pattern) in soundPatterns) {
            // Calculate match score based on sound level and duration
            val levelMatch = if (soundLevel >= pattern.minDecibels) 1.0 else 0.0
            val durationMatch = if (duration >= pattern.minDuration && duration <= pattern.maxDuration) 1.0 else 0.0
            
            val matchScore = levelMatch * 0.7 + durationMatch * 0.3 // Weight level more than duration
            
            if (matchScore > bestMatchScore && matchScore > 0.5) {
                bestMatch = soundName
                bestMatchScore = matchScore
            }
        }
        
        // If we found a match and it's different from the last detected sound or enough time has passed
        val currentTime = System.currentTimeMillis()
        if (bestMatch != null && (bestMatch != lastDetectedSound || currentTime - lastDetectionTime > DETECTION_COOLDOWN)) {
            lastDetectedSound = bestMatch
            lastDetectionTime = currentTime
            
            // Determine the direction (simplified - in a real app, this would use multiple microphones)
            val direction = getRandomDirection()
            
            // Update UI and announce the sound
            val soundMessage = getString(R.string.sound_direction, bestMatch, direction)
            mainHandler.post {
                soundResultTextView.text = soundMessage
                speak(soundMessage)
                
                // Vibrate with intensity based on sound level
                val vibrationDuration = when {
                    soundLevel >= VERY_LOUD_THRESHOLD -> 500L
                    soundLevel >= LOUD_THRESHOLD -> 300L
                    else -> 100L
                }
                vibrate(vibrationDuration)
            }
        }
    }
    
    private fun getRandomDirection(): String {
        // In a real app, this would use multiple microphones or other sensors to determine direction
        val directions = arrayOf(
            getString(R.string.direction_left),
            getString(R.string.direction_right),
            getString(R.string.direction_front),
            getString(R.string.direction_behind)
        )
        return directions.random()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                Toast.makeText(this, "Audio permission is required for sound recognition.", Toast.LENGTH_LONG).show()
                speak("Audio permission is required for sound recognition.")
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (isListening) {
            stopListening()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
    
    // Data class for sound patterns
    data class SoundPattern(
        val minDecibels: Double, // Minimum sound level in dB
        val minDuration: Double, // Minimum duration in seconds
        val maxDuration: Double  // Maximum duration in seconds
    )
}
