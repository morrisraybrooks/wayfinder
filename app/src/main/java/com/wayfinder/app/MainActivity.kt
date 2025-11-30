package com.wayfinder.app

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var welcomeTextView: TextView

    // Original features
    private lateinit var objectDetectionButton: Button
    private lateinit var textRecognitionButton: Button
    private lateinit var navigationAssistButton: Button
    private lateinit var voiceCommandButton: Button
    private lateinit var sceneDescriptionButton: Button
    private lateinit var settingsButton: Button

    // New features
    private lateinit var colorRecognitionButton: Button
    private lateinit var currencyIdentifierButton: Button
    private lateinit var productScannerButton: Button
    private lateinit var indoorNavigationButton: Button
    private lateinit var publicTransportButton: Button
    private lateinit var emergencySosButton: Button
    private lateinit var audioBeaconButton: Button
    private lateinit var soundRecognitionButton: Button
    private lateinit var faceRecognitionButton: Button
    private lateinit var gestureControlButton: Button
    private lateinit var obstacleMemoryButton: Button
    private lateinit var weatherInformationButton: Button

    private lateinit var textToSpeech: TextToSpeech
    private var ttsInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this, this)

        // Initialize views
        initializeViews()

        // Set up button click listeners
        setupButtonListeners()
    }

    private fun initializeViews() {
        // Main welcome text
        welcomeTextView = findViewById(R.id.welcomeTextView)

        // Original features
        objectDetectionButton = findViewById(R.id.objectDetectionButton)
        textRecognitionButton = findViewById(R.id.textRecognitionButton)
        navigationAssistButton = findViewById(R.id.navigationAssistButton)
        voiceCommandButton = findViewById(R.id.voiceCommandButton)
        sceneDescriptionButton = findViewById(R.id.sceneDescriptionButton)
        settingsButton = findViewById(R.id.settingsButton)

        // New features
        colorRecognitionButton = findViewById(R.id.colorRecognitionButton)
        currencyIdentifierButton = findViewById(R.id.currencyIdentifierButton)
        productScannerButton = findViewById(R.id.productScannerButton)
        indoorNavigationButton = findViewById(R.id.indoorNavigationButton)
        publicTransportButton = findViewById(R.id.publicTransportButton)
        emergencySosButton = findViewById(R.id.emergencySosButton)
        audioBeaconButton = findViewById(R.id.audioBeaconButton)
        soundRecognitionButton = findViewById(R.id.soundRecognitionButton)
        faceRecognitionButton = findViewById(R.id.faceRecognitionButton)
        gestureControlButton = findViewById(R.id.gestureControlButton)
        obstacleMemoryButton = findViewById(R.id.obstacleMemoryButton)
        weatherInformationButton = findViewById(R.id.weatherInformationButton)
    }

    private fun setupButtonListeners() {
        // Original features
        objectDetectionButton.setOnClickListener {
            speak(getString(R.string.detect_objects_description))
            val intent = Intent(this, ObjectDetectionActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        textRecognitionButton.setOnClickListener {
            speak(getString(R.string.read_text_description))
            val intent = Intent(this, TextRecognitionActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        navigationAssistButton.setOnClickListener {
            speak(getString(R.string.navigation_assist_description))
            val intent = Intent(this, NavigationAssistActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        voiceCommandButton.setOnClickListener {
            speak(getString(R.string.voice_command_description))
            val intent = Intent(this, VoiceCommandActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        sceneDescriptionButton.setOnClickListener {
            speak(getString(R.string.scene_description_description))
            val intent = Intent(this, SceneDescriptionActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        // New features
        colorRecognitionButton.setOnClickListener {
            speak(getString(R.string.color_recognition_description))
            val intent = Intent(this, ColorRecognitionActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        currencyIdentifierButton.setOnClickListener {
            speak(getString(R.string.currency_identifier_description))
            val intent = Intent(this, CurrencyIdentifierActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        productScannerButton.setOnClickListener {
            speak(getString(R.string.product_scanner_description))
            val intent = Intent(this, ProductScannerActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        indoorNavigationButton.setOnClickListener {
            speak(getString(R.string.indoor_navigation_description))
            val intent = Intent(this, IndoorNavigationActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        publicTransportButton.setOnClickListener {
            speak(getString(R.string.public_transport_description))
            val intent = Intent(this, PublicTransportActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        emergencySosButton.setOnClickListener {
            speak(getString(R.string.emergency_sos_description))
            val intent = Intent(this, EmergencySOSActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        audioBeaconButton.setOnClickListener {
            speak(getString(R.string.audio_beacon_description))
            val intent = Intent(this, AudioBeaconActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        soundRecognitionButton.setOnClickListener {
            speak(getString(R.string.sound_recognition_description))
            val intent = Intent(this, SoundRecognitionActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        faceRecognitionButton.setOnClickListener {
            speak(getString(R.string.face_recognition_description))
            val intent = Intent(this, FaceRecognitionActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        gestureControlButton.setOnClickListener {
            speak(getString(R.string.gesture_control_description))
            val intent = Intent(this, GestureControlActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        obstacleMemoryButton.setOnClickListener {
            speak(getString(R.string.obstacle_memory_description))
            val intent = Intent(this, ObstacleMemoryActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        weatherInformationButton.setOnClickListener {
            speak(getString(R.string.weather_information_description))
            val intent = Intent(this, WeatherInformationActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }

        settingsButton.setOnClickListener {
            speak(getString(R.string.settings_description))
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            vibrate(100)
        }
    }

    private fun vibrate(duration: Long = 100) {
        // Use modern getSystemService when available and fallback for older APIs
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            getSystemService(android.os.Vibrator::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }

        vibrator?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                it.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(duration)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language to US English
            val result = textToSpeech.setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Text-to-speech language not supported", Toast.LENGTH_SHORT).show()
            } else {
                ttsInitialized = true
                // Speak welcome message when TTS is initialized
                speak(getString(R.string.welcome_message))
            }
        } else {
            Toast.makeText(this, "Text-to-speech initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speak(text: String) {
        if (ttsInitialized) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
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
