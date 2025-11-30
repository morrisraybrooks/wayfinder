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
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Activity that provides indoor navigation assistance.
 * Specifically identifies doorways, stairs, and elevators to help with indoor navigation.
 */
class IndoorNavigationActivity : BaseAccessibilityActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var indoorNavigationInstructions: TextView
    private lateinit var indoorNavigationStatusTextView: TextView
    private lateinit var toggleIndoorNavigationButton: Button
    private lateinit var backButton: Button

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var isNavigating = false

    private val CAMERA_PERMISSION_CODE = 100
    
    // Indoor feature detection
    // Note: ML Kit doesn't have "door" directly, so we include related labels
    private val indoorFeatures = mapOf(
        "door" to listOf("door", "doorway", "entrance", "exit", "gate", "frame", "handle", "wood", "rectangle", "facade"),
        "stairs" to listOf("stairs", "staircase", "stairwell", "steps"),
        "elevator" to listOf("elevator", "lift"),
        "hallway" to listOf("hallway", "corridor", "passage", "hall", "lobby"),
        "room" to listOf("room", "office", "classroom", "bathroom", "restroom", "bedroom", "kitchen", "living room", "interior design", "property", "building", "house", "home", "architecture")
    )

    // Labels from ML Kit Image Labeling that suggest door area
    private val doorRelatedImageLabels = setOf(
        "room", "property", "house", "home", "building", "architecture",
        "hall", "hallway", "corridor", "lobby", "interior design",
        "rectangle", "facade", "gate", "frame", "wood", "handle"
    )
    
    // Track the last announced features to avoid repetitive announcements
    private var lastAnnouncedFeature: String? = null
    private var lastAnnouncementTime = 0L
    private val ANNOUNCEMENT_INTERVAL = 5000L // 5 seconds between announcements
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_indoor_navigation)
        
        // Initialize views
        previewView = findViewById(R.id.previewView)
        indoorNavigationInstructions = findViewById(R.id.indoorNavigationInstructions)
        indoorNavigationStatusTextView = findViewById(R.id.indoorNavigationStatusTextView)
        toggleIndoorNavigationButton = findViewById(R.id.toggleIndoorNavigationButton)
        backButton = findViewById(R.id.backButton)
        
        // Set up button click listeners
        toggleIndoorNavigationButton.setOnClickListener {
            if (isNavigating) {
                stopIndoorNavigation()
            } else {
                checkPermissionAndStartCamera()
            }
        }
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Set initial button text
        toggleIndoorNavigationButton.text = getString(R.string.start_indoor_navigation_button)
        toggleIndoorNavigationButton.contentDescription = getString(R.string.start_indoor_navigation_button)
    }
    
    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.indoor_navigation_instructions))
    }
    
    private fun checkPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startIndoorNavigation()
        }
    }
    
    private fun startIndoorNavigation() {
        if (!isNavigating) {
            isNavigating = true
            
            // Update UI
            toggleIndoorNavigationButton.text = getString(R.string.stop_indoor_navigation_button)
            toggleIndoorNavigationButton.contentDescription = getString(R.string.stop_indoor_navigation_button)
            indoorNavigationStatusTextView.text = getString(R.string.looking_for_indoor_features)
            speak(getString(R.string.looking_for_indoor_features))
            
            // Reset the last announced feature
            lastAnnouncedFeature = null
            
            // Start camera and analysis
            startCamera()
            
            // Provide haptic feedback
            vibrate()
        }
    }
    
    private fun stopIndoorNavigation() {
        if (isNavigating) {
            isNavigating = false

            // Unbind camera to stop processing
            cameraProvider?.unbindAll()

            // Update UI
            toggleIndoorNavigationButton.text = getString(R.string.start_indoor_navigation_button)
            toggleIndoorNavigationButton.contentDescription = getString(R.string.start_indoor_navigation_button)

            // Provide haptic feedback
            vibrate()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Set up the preview use case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Set up the image analysis use case
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, IndoorFeatureAnalyzer())
                }

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind any bound use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind use cases to camera
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private inner class IndoorFeatureAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTimestamp = 0L
        private val ANALYSIS_INTERVAL = 1000L // 1 second between analyses
        private val TAG = "IndoorNavigation"

        // Image labeler for detecting door-related labels
        // Note: ML Kit doesn't have "door" directly, so we use image labeling to detect related labels
        private val imageLabelerOptions = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.25f) // Lower threshold for better detection
            .build()

        private val imageLabeler = ImageLabeling.getClient(imageLabelerOptions)

        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()

            if (currentTimestamp - lastAnalysisTimestamp >= ANALYSIS_INTERVAL) {
                val mediaImage = imageProxy.image
                if (mediaImage != null && isNavigating) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                    // Use only image labeler (more reliable, avoids native crash from parallel processing)
                    // ML Kit object detection with image labeling running in parallel causes native crashes
                    imageLabeler.process(image)
                        .addOnSuccessListener { labels ->
                            val labelingResults = labels.map { it.text }
                            Log.d(TAG, "Image labeling found ${labels.size} labels: ${labelingResults.take(5)}")
                            processDetectedIndoorFeatures(labelingResults)
                            lastAnalysisTimestamp = currentTimestamp
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Image labeling failed", e)
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

        private fun processDetectedIndoorFeatures(labeledObjects: List<String>?) {
            // Look for indoor features in the detected objects
            val detectedFeatures = mutableListOf<String>()

            // Process image labeling results to detect door-related labels
            // Note: ML Kit Image Labeling doesn't have "door" directly
            if (labeledObjects != null) {
                var doorRelatedDetected = false

                for (label in labeledObjects) {
                    val lowerLabel = label.lowercase()

                    // Check if this is a door-related label
                    if (doorRelatedImageLabels.contains(lowerLabel)) {
                        doorRelatedDetected = true
                        Log.d(TAG, "Door-related label detected from image labeling: $label")
                    }

                    // Also check if any label matches indoor feature keywords
                    for ((featureType, keywords) in indoorFeatures) {
                        if (keywords.any { keyword -> lowerLabel.contains(keyword) }) {
                            if (!detectedFeatures.contains(featureType)) {
                                detectedFeatures.add(featureType)
                                Log.d(TAG, "Indoor feature detected from image labeling: $featureType (from label: $label)")
                            }
                            break
                        }
                    }
                }

                // If door-related labels detected but "door" not in features, add it
                if (doorRelatedDetected && !detectedFeatures.contains("door")) {
                    detectedFeatures.add("door")
                    Log.d(TAG, "Added 'door' feature based on related image labels")
                }
            }

            // Update the UI with detected features
            runOnUiThread {
                if (detectedFeatures.isNotEmpty()) {
                    val featuresText = detectedFeatures.joinToString(", ")
                    val resultText = getString(R.string.indoor_features_detected, featuresText)
                    indoorNavigationStatusTextView.text = resultText

                    // Check if we should announce a feature
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastAnnouncementTime >= ANNOUNCEMENT_INTERVAL) {
                        // Get the first detected feature
                        val featureToAnnounce = detectedFeatures.first()

                        // Only announce if it's different from the last announced feature
                        if (featureToAnnounce != lastAnnouncedFeature) {
                            // Announce the feature
                            val announcement = when (featureToAnnounce) {
                                "door" -> getString(R.string.door_detected)
                                "stairs" -> getString(R.string.stairs_detected)
                                "elevator" -> getString(R.string.elevator_detected)
                                "hallway" -> getString(R.string.hallway_detected)
                                "room" -> getString(R.string.room_detected)
                                else -> getString(R.string.indoor_feature_detected, featureToAnnounce)
                            }

                            speak(announcement)
                            vibrate(200)

                            lastAnnouncedFeature = featureToAnnounce
                            lastAnnouncementTime = currentTime
                        }
                    }
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startIndoorNavigation()
            } else {
                Toast.makeText(this, "Camera permission is required for indoor navigation.", Toast.LENGTH_LONG).show()
                speak("Camera permission is required for indoor navigation.")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
