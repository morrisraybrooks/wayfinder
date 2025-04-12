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
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
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
    private var isNavigating = false
    
    private val CAMERA_PERMISSION_CODE = 100
    
    // Indoor feature detection
    private val indoorFeatures = mapOf(
        "door" to listOf("door", "doorway", "entrance", "exit"),
        "stairs" to listOf("stairs", "staircase", "stairwell", "steps"),
        "elevator" to listOf("elevator", "lift"),
        "hallway" to listOf("hallway", "corridor", "passage"),
        "room" to listOf("room", "office", "classroom", "bathroom", "restroom")
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
                    it.setAnalyzer(cameraExecutor, IndoorFeatureAnalyzer())
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
    
    private inner class IndoorFeatureAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTimestamp = 0L
        private val ANALYSIS_INTERVAL = 1000L // 1 second between analyses
        
        // Set up the object detector options
        private val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()  // Enable classification
            .enableMultipleObjects() // Enable multiple object detection
            .build()
        
        private val objectDetector = ObjectDetection.getClient(options)
        
        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()
            
            if (currentTimestamp - lastAnalysisTimestamp >= ANALYSIS_INTERVAL) {
                val mediaImage = imageProxy.image
                if (mediaImage != null && isNavigating) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    
                    // Process the image with object detector
                    objectDetector.process(image)
                        .addOnSuccessListener { detectedObjects ->
                            if (detectedObjects.isEmpty()) {
                                // Don't update UI if no objects are found to avoid flickering
                            } else {
                                processDetectedIndoorFeatures(detectedObjects)
                            }
                            
                            lastAnalysisTimestamp = currentTimestamp
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this@IndoorNavigationActivity, "Object detection failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
        
        private fun processDetectedIndoorFeatures(detectedObjects: List<DetectedObject>) {
            // Look for indoor features in the detected objects
            val detectedFeatures = mutableListOf<String>()
            
            for (detectedObject in detectedObjects) {
                // Get all labels for this object
                for (label in detectedObject.labels) {
                    val objectName = label.text.lowercase()
                    
                    // Check if this object matches any indoor feature
                    for ((featureType, keywords) in indoorFeatures) {
                        if (keywords.any { keyword -> objectName.contains(keyword) }) {
                            detectedFeatures.add(featureType)
                            break
                        }
                    }
                }
            }
            
            // Update the UI with detected features
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
        cameraExecutor.shutdown()
    }
}
