package com.wayfinder.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

/**
 * Activity that implements voice command functionality.
 * Allows the user to control the app using voice commands.
 */
class VoiceCommandActivity : BaseAccessibilityActivity() {

    private lateinit var listeningStatusTextView: TextView
    private lateinit var recognizedCommandTextView: TextView
    private lateinit var startListeningButton: Button
    private lateinit var stopListeningButton: Button
    private lateinit var backButton: Button

    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false

    private val RECORD_AUDIO_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_command)

        // Initialize views
        listeningStatusTextView = findViewById(R.id.listeningStatusTextView)
        recognizedCommandTextView = findViewById(R.id.recognizedCommandTextView)
        startListeningButton = findViewById(R.id.startListeningButton)
        stopListeningButton = findViewById(R.id.stopListeningButton)
        backButton = findViewById(R.id.backButton)

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Set up button click listeners
        startListeningButton.setOnClickListener {
            checkPermissionAndStartListening()
        }

        stopListeningButton.setOnClickListener {
            stopListening()
        }

        // Check if speech recognition is available on this device
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            // Initialize speech recognizer
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer.setRecognitionListener(createRecognitionListener())
        } else {
            // Inform user that speech recognition is not available
            Toast.makeText(this, "Speech recognition is not available on this device.", Toast.LENGTH_LONG).show()
            speak("Speech recognition is not available on this device.")
            // Disable the start button
            startListeningButton.isEnabled = false
        }

        // Initially hide the stop button
        stopListeningButton.visibility = View.GONE
    }

    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.voice_command_instructions))
    }

    private fun checkPermissionAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (!isListening) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }

            try {
                isListening = true
                speechRecognizer.startListening(intent)
                listeningStatusTextView.text = getString(R.string.listening_status)
                speak(getString(R.string.listening_status), TextToSpeech.QUEUE_ADD)

                // Update UI
                startListeningButton.visibility = View.GONE
                stopListeningButton.visibility = View.VISIBLE

                // Provide haptic feedback
                vibrate()
            } catch (e: Exception) {
                Toast.makeText(this, "Error starting speech recognition: ${e.message}", Toast.LENGTH_SHORT).show()
                isListening = false
            }
        }
    }

    private fun stopListening() {
        if (isListening) {
            speechRecognizer.stopListening()
            isListening = false

            // Update UI
            startListeningButton.visibility = View.VISIBLE
            stopListeningButton.visibility = View.GONE

            // Provide haptic feedback
            vibrate()
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                // Ready for speech
            }

            override fun onBeginningOfSpeech() {
                // Speech started
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Sound level changed
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // More sound data received
            }

            override fun onEndOfSpeech() {
                // Speech ended
                isListening = false
                startListeningButton.visibility = View.VISIBLE
                stopListeningButton.visibility = View.GONE
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }

                listeningStatusTextView.text = errorMessage
                recognizedCommandTextView.text = ""
                speak(errorMessage)

                isListening = false
                startListeningButton.visibility = View.VISIBLE
                stopListeningButton.visibility = View.GONE
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0]
                    listeningStatusTextView.text = "Command recognized:"
                    recognizedCommandTextView.text = command
                    speak(getString(R.string.command_recognized, command))

                    // Process the command
                    processVoiceCommand(command)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Partial recognition results
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Reserved for future events
            }
        }
    }

    private fun processVoiceCommand(command: String) {
        val lowerCommand = command.lowercase(Locale.getDefault())

        try {
            when {
                lowerCommand.contains("detect") && lowerCommand.contains("object") -> {
                    speak("Opening object detection")
                    val intent = Intent(this, ObjectDetectionActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("read") && lowerCommand.contains("text") -> {
                    speak("Opening text recognition")
                    val intent = Intent(this, TextRecognitionActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("navigate") || lowerCommand.contains("navigation") -> {
                    speak("Opening navigation assistant")
                    val intent = Intent(this, NavigationAssistActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("describe") && lowerCommand.contains("scene") -> {
                    speak("Opening scene description")
                    val intent = Intent(this, SceneDescriptionActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("color") -> {
                    speak("Opening color recognition")
                    val intent = Intent(this, ColorRecognitionActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("money") || lowerCommand.contains("currency") -> {
                    speak("Opening currency identifier")
                    val intent = Intent(this, CurrencyIdentifierActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("scan") && lowerCommand.contains("product") -> {
                    speak("Opening product scanner")
                    val intent = Intent(this, ProductScannerActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("indoor") -> {
                    speak("Opening indoor navigation")
                    val intent = Intent(this, IndoorNavigationActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("transport") || lowerCommand.contains("bus") || lowerCommand.contains("train") -> {
                    speak("Opening public transport assistant")
                    val intent = Intent(this, PublicTransportActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("emergency") || lowerCommand.contains("sos") || lowerCommand.contains("help") -> {
                    speak("Opening emergency SOS")
                    val intent = Intent(this, EmergencySOSActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("beacon") -> {
                    speak("Opening audio beacon")
                    val intent = Intent(this, AudioBeaconActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("sound") -> {
                    speak("Opening sound recognition")
                    val intent = Intent(this, SoundRecognitionActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("face") -> {
                    speak("Opening face recognition")
                    val intent = Intent(this, FaceRecognitionActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("gesture") -> {
                    speak("Opening gesture control")
                    val intent = Intent(this, GestureControlActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("obstacle") || lowerCommand.contains("memory") -> {
                    speak("Opening obstacle memory")
                    val intent = Intent(this, ObstacleMemoryActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("weather") -> {
                    speak("Opening weather information")
                    val intent = Intent(this, WeatherInformationActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                lowerCommand.contains("settings") || lowerCommand.contains("options") -> {
                    speak("Opening settings")
                    showCommandNotImplemented("Settings")
                }
                lowerCommand.contains("home") || lowerCommand.contains("main") || lowerCommand.contains("menu") -> {
                    speak("Going to home screen")
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                    finish()
                }
                else -> {
                    speak(getString(R.string.command_not_recognized))
                    // Restart listening after a short delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isListening) {
                            startListening()
                        }
                    }, 2000)
                }
            }
        } catch (e: Exception) {
            speak("Sorry, there was an error processing your command")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCommandNotImplemented(featureName: String) {
        Toast.makeText(this, "$featureName feature is not yet implemented", Toast.LENGTH_SHORT).show()
        // Restart listening after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isListening) {
                startListening()
            }
        }, 2000)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                Toast.makeText(this, "Permission denied. Voice commands require microphone access.", Toast.LENGTH_LONG).show()
                speak("Permission denied. Voice commands require microphone access.")
            }
        }
    }

    override fun onDestroy() {
        // Stop listening if active
        if (isListening) {
            try {
                speechRecognizer.stopListening()
            } catch (e: Exception) {
                // Ignore exceptions during cleanup
            }
            isListening = false
        }
        
        // Clean up the speech recognizer
        if (::speechRecognizer.isInitialized) {
            try {
                speechRecognizer.destroy()
            } catch (e: Exception) {
                // Ignore exceptions during cleanup
            }
        }
        
        super.onDestroy()
    }
}
