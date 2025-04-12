package com.wayfinder.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Activity that helps with public transportation.
 * Recognizes bus numbers, train information, and schedules.
 */
class PublicTransportActivity : BaseAccessibilityActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var transportInstructions: TextView
    private lateinit var transportResultTextView: TextView
    private lateinit var toggleTransportButton: Button
    private lateinit var backButton: Button
    
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isAnalyzing = false
    
    private val CAMERA_PERMISSION_CODE = 100
    
    // Patterns for recognizing transport information
    private val busNumberPattern = Pattern.compile("\\b(?:bus|route)\\s*(?:#|number|no\\.?)?\\s*(\\d+[A-Z]?|[A-Z]\\d+)\\b", Pattern.CASE_INSENSITIVE)
    private val trainNumberPattern = Pattern.compile("\\b(?:train|express|local)\\s*(?:#|number|no\\.?)?\\s*(\\d+[A-Z]?|[A-Z]\\d+)\\b", Pattern.CASE_INSENSITIVE)
    private val timePattern = Pattern.compile("\\b(\\d{1,2}:\\d{2})\\s*(?:am|pm|a\\.m\\.|p\\.m\\.)?\\b", Pattern.CASE_INSENSITIVE)
    private val destinationPattern = Pattern.compile("\\bto\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)\\b", Pattern.CASE_INSENSITIVE)
    
    // Track the last announced information to avoid repetitive announcements
    private var lastAnnouncedInfo: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_public_transport)
        
        // Initialize views
        previewView = findViewById(R.id.previewView)
        transportInstructions = findViewById(R.id.transportInstructions)
        transportResultTextView = findViewById(R.id.transportResultTextView)
        toggleTransportButton = findViewById(R.id.toggleTransportButton)
        backButton = findViewById(R.id.backButton)
        
        // Set up button click listeners
        toggleTransportButton.setOnClickListener {
            if (isAnalyzing) {
                stopTransportRecognition()
            } else {
                checkPermissionAndStartCamera()
            }
        }
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Set initial button text
        toggleTransportButton.text = getString(R.string.start_transport_recognition_button)
        toggleTransportButton.contentDescription = getString(R.string.start_transport_recognition_button)
    }
    
    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.public_transport_instructions))
    }
    
    private fun checkPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startTransportRecognition()
        }
    }
    
    private fun startTransportRecognition() {
        if (!isAnalyzing) {
            isAnalyzing = true
            
            // Update UI
            toggleTransportButton.text = getString(R.string.stop_transport_recognition_button)
            toggleTransportButton.contentDescription = getString(R.string.stop_transport_recognition_button)
            transportResultTextView.text = getString(R.string.looking_for_transport_info)
            speak(getString(R.string.looking_for_transport_info))
            
            // Reset the last announced info
            lastAnnouncedInfo = null
            
            // Start camera and analysis
            startCamera()
            
            // Provide haptic feedback
            vibrate()
        }
    }
    
    private fun stopTransportRecognition() {
        if (isAnalyzing) {
            isAnalyzing = false
            
            // Update UI
            toggleTransportButton.text = getString(R.string.start_transport_recognition_button)
            toggleTransportButton.contentDescription = getString(R.string.start_transport_recognition_button)
            
            // Provide haptic feedback
            vibrate()
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Set up the preview use case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            // Set up the image analysis use case
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, TransportInfoAnalyzer())
                }
            
            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll()
                
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private inner class TransportInfoAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTimestamp = 0L
        private val ANALYSIS_INTERVAL = 2000L // 2 seconds between analyses
        
        private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()
            
            if (currentTimestamp - lastAnalysisTimestamp >= ANALYSIS_INTERVAL) {
                val mediaImage = imageProxy.image
                if (mediaImage != null && isAnalyzing) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    
                    // Process the image with text recognizer
                    textRecognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val detectedText = visionText.text
                            
                            if (detectedText.isEmpty()) {
                                transportResultTextView.text = getString(R.string.no_transport_info_detected)
                                // Don't speak this every time to avoid too much feedback
                            } else {
                                // Look for transport information in the text
                                val transportInfo = extractTransportInfo(detectedText)
                                
                                if (transportInfo.isNotEmpty()) {
                                    // Update the UI with the detected information
                                    val resultText = transportInfo.joinToString("\n")
                                    transportResultTextView.text = resultText
                                    
                                    // Only announce if it's different from the last announced info
                                    if (resultText != lastAnnouncedInfo) {
                                        // Announce the information
                                        speak(getString(R.string.transport_info_detected))
                                        transportInfo.forEach { info ->
                                            speak(info, TextToSpeech.QUEUE_ADD)
                                        }
                                        
                                        lastAnnouncedInfo = resultText
                                    }
                                } else {
                                    transportResultTextView.text = getString(R.string.no_transport_info_detected)
                                }
                            }
                            
                            lastAnalysisTimestamp = currentTimestamp
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this@PublicTransportActivity, "Text recognition failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            } else {
                imageProxy.close()
            }
        }
        
        private fun extractTransportInfo(text: String): List<String> {
            val transportInfo = mutableListOf<String>()
            
            // Look for bus numbers
            val busMatcher = busNumberPattern.matcher(text)
            while (busMatcher.find()) {
                val busNumber = busMatcher.group(1)
                transportInfo.add(getString(R.string.bus_number_detected, busNumber))
            }
            
            // Look for train numbers
            val trainMatcher = trainNumberPattern.matcher(text)
            while (trainMatcher.find()) {
                val trainNumber = trainMatcher.group(1)
                transportInfo.add(getString(R.string.train_info_detected, "Train $trainNumber"))
            }
            
            // Look for times
            val timeMatcher = timePattern.matcher(text)
            while (timeMatcher.find()) {
                val time = timeMatcher.group(1)
                transportInfo.add(getString(R.string.schedule_detected, "Departure at $time"))
            }
            
            // Look for destinations
            val destinationMatcher = destinationPattern.matcher(text)
            while (destinationMatcher.find()) {
                val destination = destinationMatcher.group(1)
                transportInfo.add(getString(R.string.destination_detected, destination))
            }
            
            return transportInfo
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startTransportRecognition()
            } else {
                Toast.makeText(this, "Camera permission is required for transport information recognition.", Toast.LENGTH_LONG).show()
                speak("Camera permission is required for transport information recognition.")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
