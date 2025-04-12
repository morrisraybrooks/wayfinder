package com.wayfinder.app

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat

/**
 * Activity that provides app settings and preferences.
 * Allows the user to adjust TTS rate/pitch, vibration, and other accessibility options.
 */
class SettingsActivity : BaseAccessibilityActivity() {

    private lateinit var speechRateSeekBar: SeekBar
    private lateinit var speechPitchSeekBar: SeekBar
    private lateinit var vibrationIntensitySeekBar: SeekBar
    private lateinit var highContrastModeSwitch: SwitchCompat
    private lateinit var speechRateValueTextView: TextView
    private lateinit var speechPitchValueTextView: TextView
    private lateinit var vibrationValueTextView: TextView
    private lateinit var saveSettingsButton: Button
    private lateinit var resetSettingsButton: Button
    private lateinit var backButton: Button

    // Default settings values
    private val DEFAULT_SPEECH_RATE = 1.0f
    private val DEFAULT_SPEECH_PITCH = 1.0f
    private val DEFAULT_VIBRATION_INTENSITY = 0.5f
    private val DEFAULT_HIGH_CONTRAST = false

    // Current settings values
    private var currentSpeechRate = DEFAULT_SPEECH_RATE
    private var currentSpeechPitch = DEFAULT_SPEECH_PITCH
    private var currentVibrationIntensity = DEFAULT_VIBRATION_INTENSITY
    private var currentHighContrast = DEFAULT_HIGH_CONTRAST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize views
        speechRateSeekBar = findViewById(R.id.speechRateSeekBar)
        speechPitchSeekBar = findViewById(R.id.speechPitchSeekBar)
        vibrationIntensitySeekBar = findViewById(R.id.vibrationIntensitySeekBar)
        highContrastModeSwitch = findViewById(R.id.highContrastModeSwitch)
        speechRateValueTextView = findViewById(R.id.speechRateValueTextView)
        speechPitchValueTextView = findViewById(R.id.speechPitchValueTextView)
        vibrationValueTextView = findViewById(R.id.vibrationValueTextView)
        saveSettingsButton = findViewById(R.id.saveSettingsButton)
        resetSettingsButton = findViewById(R.id.resetSettingsButton)
        backButton = findViewById(R.id.backButton)

        // Load current settings
        loadSettings()

        // Set up seek bar listeners
        setupSeekBars()

        // Set up button click listeners
        saveSettingsButton.setOnClickListener {
            saveSettings()
        }

        resetSettingsButton.setOnClickListener {
            resetSettings()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.settings_title))
    }

    private fun loadSettings() {
        // Load settings from SharedPreferences
        val prefs = getSharedPreferences("WayfinderSettings", MODE_PRIVATE)
        
        // Speech rate (0.5 to 2.0)
        currentSpeechRate = prefs.getFloat("speech_rate", DEFAULT_SPEECH_RATE)
        speechRateSeekBar.progress = ((currentSpeechRate - 0.5f) * 100).toInt()
        speechRateValueTextView.text = String.format("%.1f", currentSpeechRate)
        
        // Speech pitch (0.5 to 2.0)
        currentSpeechPitch = prefs.getFloat("speech_pitch", DEFAULT_SPEECH_PITCH)
        speechPitchSeekBar.progress = ((currentSpeechPitch - 0.5f) * 100).toInt()
        speechPitchValueTextView.text = String.format("%.1f", currentSpeechPitch)
        
        // Vibration intensity (0.0 to 1.0)
        currentVibrationIntensity = prefs.getFloat("vibration_intensity", DEFAULT_VIBRATION_INTENSITY)
        vibrationIntensitySeekBar.progress = (currentVibrationIntensity * 100).toInt()
        vibrationValueTextView.text = String.format("%.1f", currentVibrationIntensity)
        
        // High contrast mode
        currentHighContrast = prefs.getBoolean("high_contrast", DEFAULT_HIGH_CONTRAST)
        highContrastModeSwitch.isChecked = currentHighContrast
        
        // Apply speech settings to TextToSpeech
        if (ttsInitialized) {
            textToSpeech.setSpeechRate(currentSpeechRate)
            textToSpeech.setPitch(currentSpeechPitch)
        }
    }

    private fun setupSeekBars() {
        // Speech rate seek bar
        speechRateSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Map progress (0-100) to speech rate (0.5-2.0)
                currentSpeechRate = 0.5f + (progress / 100f).coerceAtMost(1.5f)
                speechRateValueTextView.text = String.format("%.1f", currentSpeechRate)
                
                // Apply immediately for preview
                if (ttsInitialized) {
                    textToSpeech.setSpeechRate(currentSpeechRate)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Speak a sample when adjustment ends
                speak("This is a sample of the speech rate", TextToSpeech.QUEUE_FLUSH)
            }
        })

        // Speech pitch seek bar
        speechPitchSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Map progress (0-100) to speech pitch (0.5-2.0)
                currentSpeechPitch = 0.5f + (progress / 100f).coerceAtMost(1.5f)
                speechPitchValueTextView.text = String.format("%.1f", currentSpeechPitch)
                
                // Apply immediately for preview
                if (ttsInitialized) {
                    textToSpeech.setPitch(currentSpeechPitch)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Speak a sample when adjustment ends
                speak("This is a sample of the speech pitch", TextToSpeech.QUEUE_FLUSH)
            }
        })

        // Vibration intensity seek bar
        vibrationIntensitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Map progress (0-100) to vibration intensity (0.0-1.0)
                currentVibrationIntensity = progress / 100f
                vibrationValueTextView.text = String.format("%.1f", currentVibrationIntensity)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Provide vibration feedback when adjustment ends
                vibrate((currentVibrationIntensity * 300).toLong())
            }
        })

        // High contrast mode switch
        highContrastModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            currentHighContrast = isChecked
            // In a real app, this would apply high contrast theme
        }
    }

    private fun saveSettings() {
        // Save settings to SharedPreferences
        val editor = getSharedPreferences("WayfinderSettings", MODE_PRIVATE).edit()
        editor.putFloat("speech_rate", currentSpeechRate)
        editor.putFloat("speech_pitch", currentSpeechPitch)
        editor.putFloat("vibration_intensity", currentVibrationIntensity)
        editor.putBoolean("high_contrast", currentHighContrast)
        editor.apply()

        // Provide feedback
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        speak(getString(R.string.settings_saved))
        vibrate(100)
    }

    private fun resetSettings() {
        // Reset to defaults
        currentSpeechRate = DEFAULT_SPEECH_RATE
        currentSpeechPitch = DEFAULT_SPEECH_PITCH
        currentVibrationIntensity = DEFAULT_VIBRATION_INTENSITY
        currentHighContrast = DEFAULT_HIGH_CONTRAST

        // Update UI
        speechRateSeekBar.progress = ((currentSpeechRate - 0.5f) * 100).toInt()
        speechPitchSeekBar.progress = ((currentSpeechPitch - 0.5f) * 100).toInt()
        vibrationIntensitySeekBar.progress = (currentVibrationIntensity * 100).toInt()
        highContrastModeSwitch.isChecked = currentHighContrast

        // Apply speech settings
        if (ttsInitialized) {
            textToSpeech.setSpeechRate(currentSpeechRate)
            textToSpeech.setPitch(currentSpeechPitch)
        }

        // Provide feedback
        Toast.makeText(this, getString(R.string.settings_reset), Toast.LENGTH_SHORT).show()
        speak(getString(R.string.settings_reset))
        vibrate(100)
    }
}