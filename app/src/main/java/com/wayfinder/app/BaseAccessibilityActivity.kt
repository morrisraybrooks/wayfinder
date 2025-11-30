package com.wayfinder.app

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

/**
 * Base activity class that provides common accessibility features for all activities in the app.
 * This includes text-to-speech functionality, vibration feedback, and other accessibility helpers.
 */
abstract class BaseAccessibilityActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    
    protected lateinit var textToSpeech: TextToSpeech
    protected var ttsInitialized = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language to US English
            val result = textToSpeech.setLanguage(Locale.US)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Text-to-speech language not supported", Toast.LENGTH_SHORT).show()
            } else {
                ttsInitialized = true
                onTtsInitialized()
            }
        } else {
            Toast.makeText(this, "Text-to-speech initialization failed", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Called when TTS is successfully initialized.
     * Override this method to perform actions that require TTS to be ready.
     */
    protected open fun onTtsInitialized() {
        // Default implementation does nothing
    }
    
    /**
     * Speaks the given text using text-to-speech.
     * @param text The text to speak
     * @param queueMode Whether to add to queue or flush existing speech
     */
    protected fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        try {
            if (ttsInitialized && ::textToSpeech.isInitialized) {
                textToSpeech.speak(text, queueMode, null, null)
            } else {
                // If TTS is not initialized, show a toast with the message instead
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // If there's an error with TTS, fallback to showing a toast
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Provides haptic feedback through vibration.
     * @param duration Duration of vibration in milliseconds
     */
    protected fun vibrate(duration: Long = 100) {
        try {
            // Use the modern getSystemService(Class) when available to avoid deprecated constants
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                getSystemService(android.os.Vibrator::class.java)
            } else {
                @Suppress("DEPRECATION")
                getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }

            if (vibrator != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
        } catch (e: Exception) {
            // Silently fail if vibration is not available or fails
        }
    }
    
    override fun onDestroy() {
        // Shutdown TTS when activity is destroyed
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}
