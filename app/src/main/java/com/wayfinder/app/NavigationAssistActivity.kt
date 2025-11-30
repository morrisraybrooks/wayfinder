package com.wayfinder.app

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
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
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Activity that provides navigation assistance.
 * Uses object detection and image labeling to identify obstacles and provides audio feedback.
 * Combines ML Kit object detection with image labeling for better coverage of objects like "door".
 */
class NavigationAssistActivity : BaseAccessibilityActivity(), SensorEventListener {

    private lateinit var previewView: PreviewView
    private lateinit var navigationInstructions: TextView
    private lateinit var navigationStatusTextView: TextView
    private lateinit var toggleNavigationButton: Button
    private lateinit var backButton: Button
    
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isNavigating = false
    private var cameraProvider: ProcessCameraProvider? = null

    private val CAMERA_PERMISSION_CODE = 100
    private val LOCATION_PERMISSION_CODE = 101
    
    // Sensor-related variables
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var lastAccelerometer = FloatArray(3)
    private var lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var currentDegree = 0f
    
    // Navigation state
    private var lastObstacleAnnouncementTime = 0L
    private val OBSTACLE_ANNOUNCEMENT_INTERVAL = 3000L // 3 seconds between obstacle announcements
    private var detectedObstacles = mutableMapOf<String, Float>() // Object label to distance
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation_assist)
        
        // Initialize views
        previewView = findViewById(R.id.previewView)
        navigationInstructions = findViewById(R.id.navigationInstructions)
        navigationStatusTextView = findViewById(R.id.navigationStatusTextView)
        toggleNavigationButton = findViewById(R.id.toggleNavigationButton)
        backButton = findViewById(R.id.backButton)
        
        // Initialize sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        // Set up button click listeners
        toggleNavigationButton.setOnClickListener {
            if (isNavigating) {
                stopNavigation()
            } else {
                checkPermissionsAndStartNavigation()
            }
        }
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Set initial button text
        toggleNavigationButton.text = getString(R.string.start_navigation_button)
        toggleNavigationButton.contentDescription = getString(R.string.start_navigation_button)
    }
    
    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.navigation_instructions))
    }
    
    private fun checkPermissionsAndStartNavigation() {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val locationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        
        when {
            cameraPermission != PackageManager.PERMISSION_GRANTED && locationPermission != PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
                    CAMERA_PERMISSION_CODE
                )
            }
            cameraPermission != PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_CODE
                )
            }
            locationPermission != PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_CODE
                )
            }
            else -> {
                startNavigation()
            }
        }
    }
    
    private fun startNavigation() {
        if (!isNavigating) {
            isNavigating = true
            
            // Update UI
            toggleNavigationButton.text = getString(R.string.stop_navigation_button)
            toggleNavigationButton.contentDescription = getString(R.string.stop_navigation_button)
            navigationStatusTextView.text = getString(R.string.navigation_started)
            speak(getString(R.string.navigation_started))
            
            // Clear the detected obstacles
            detectedObstacles.clear()
            
            // Register sensor listeners
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            
            // Start camera and analysis
            startCamera()
            
            // Provide haptic feedback
            vibrate()
        }
    }
    
    private fun stopNavigation() {
        if (isNavigating) {
            isNavigating = false

            // Update UI
            toggleNavigationButton.text = getString(R.string.start_navigation_button)
            toggleNavigationButton.contentDescription = getString(R.string.start_navigation_button)
            navigationStatusTextView.text = getString(R.string.navigation_stopped)
            speak(getString(R.string.navigation_stopped))

            // Unregister sensor listeners
            sensorManager.unregisterListener(this)

            // Unbind camera
            cameraProvider?.unbindAll()

            // Close the navigation analyzer
            navigationAnalyzer?.close()

            // Provide haptic feedback
            vibrate()
        }
    }
    
    // Keep a reference to the analyzer so we can close it
    private var navigationAnalyzer: NavigationAnalyzer? = null

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            this.cameraProvider = cameraProvider

            // Set up the preview use case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Set up the image analysis use case
            navigationAnalyzer = NavigationAnalyzer()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, navigationAnalyzer!!)
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
    
    private inner class NavigationAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTimestamp = 0L
        private val ANALYSIS_INTERVAL = 500L // 0.5 seconds between analyses
        
        // Image labeler for general object recognition
        private val imageLabelerOptions = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.25f)
            .build()

        private val imageLabeler = ImageLabeling.getClient(imageLabelerOptions)

        // Obstacle types - only real physical obstacles that someone could walk into
        // Removed scene labels like "room", "building", "property" which aren't obstacles
        private val obstacleTypes = setOf(
            // Actual physical obstacles
            "wall", "stairs", "step", "staircase",
            "furniture", "table", "chair", "couch", "sofa", "bed",
            "person", "human", "man", "woman", "child",
            "car", "vehicle", "bicycle", "motorcycle",
            "cabinet", "cupboard", "wardrobe", "closet", "shelf",
            "pole", "post", "pillar", "column",
            "box", "luggage", "suitcase", "bag",
            "dog", "cat", "animal"
        )

        // Track last announced obstacle to avoid repeating the same one
        private var lastAnnouncedObstacle: String? = null
        
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
                            Log.d("Navigation", "Image labeling found ${labels.size} labels")
                            processObstacles(labelingResults)
                            lastAnalysisTimestamp = currentTimestamp
                        }
                        .addOnFailureListener { e ->
                            Log.e("Navigation", "Image labeling failed", e)
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
        
        private fun processObstacles(labeledObjects: List<String>?) {
            // Update the detected obstacles map
            val currentObstacles = mutableMapOf<String, Float>()

            // Process image labeling results - only detect real physical obstacles
            if (labeledObjects != null) {
                for (label in labeledObjects) {
                    val lowerLabel = label.lowercase()

                    // Only add if it's a real physical obstacle (not a scene label)
                    val alreadyDetected = currentObstacles.keys.any { it.equals(label, ignoreCase = true) }
                    if (obstacleTypes.contains(lowerLabel) && !alreadyDetected) {
                        // Image labeling doesn't provide distance, use medium estimate
                        currentObstacles[label] = 2.0f
                        Log.d("Navigation", "Detected real obstacle: $label")
                    }
                }
            }

            // Update the UI with detected obstacles (must be on UI thread)
            runOnUiThread {
                if (currentObstacles.isNotEmpty()) {
                    val obstacleText = currentObstacles.entries.joinToString("\n") {
                        "${it.key}: ${it.value} meters"
                    }
                    navigationStatusTextView.text = obstacleText

                    // Check if we should announce obstacles
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastObstacleAnnouncementTime >= OBSTACLE_ANNOUNCEMENT_INTERVAL) {
                        // Find the closest obstacle
                        val closestObstacle = currentObstacles.minByOrNull { it.value }

                        if (closestObstacle != null) {
                            val obstacleKey = closestObstacle.key
                            // Only announce if it's a different obstacle than last time
                            if (obstacleKey != lastAnnouncedObstacle) {
                                val obstacleMessage = getString(R.string.obstacle_warning, obstacleKey, closestObstacle.value)
                                speak(obstacleMessage)

                                // Vibrate with intensity based on distance
                                val vibrationDuration = (100 + (500 / closestObstacle.value).toInt()).coerceIn(100, 500)
                                vibrate(vibrationDuration.toLong())

                                lastAnnouncedObstacle = obstacleKey
                            }
                            // Always update timestamp to avoid repeated checks
                            lastObstacleAnnouncementTime = currentTime
                        }
                    }
                } else {
                    // Clear screen when no obstacles detected
                    navigationStatusTextView.text = getString(R.string.navigation_started)
                    // Reset last announced obstacle when path is clear
                    if (lastAnnouncedObstacle != null) {
                        lastAnnouncedObstacle = null
                    }
                }

                // Update the detected obstacles map
                detectedObstacles = currentObstacles
            }
        }

        fun close() {
            imageLabeler.close()
        }
    }
    
    // Sensor event handling for orientation
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor === accelerometer) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
            lastAccelerometerSet = true
        } else if (event.sensor === magnetometer) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
            lastMagnetometerSet = true
        }
        
        if (lastAccelerometerSet && lastMagnetometerSet) {
            try {
                SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)
                SensorManager.getOrientation(rotationMatrix, orientation)

                val azimuthInRadians = orientation[0]
                val azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()

                // Only announce significant direction changes
                val degreeDifference = abs((azimuthInDegrees - currentDegree + 180) % 360 - 180)
                if (degreeDifference > 45) { // Only announce when turning more than 45 degrees
                    currentDegree = azimuthInDegrees

                    // Convert degrees to cardinal direction
                    val direction = when {
                        azimuthInDegrees >= -22.5 && azimuthInDegrees < 22.5 -> "north"
                        azimuthInDegrees >= 22.5 && azimuthInDegrees < 67.5 -> "northeast"
                        azimuthInDegrees >= 67.5 && azimuthInDegrees < 112.5 -> "east"
                        azimuthInDegrees >= 112.5 && azimuthInDegrees < 157.5 -> "southeast"
                        azimuthInDegrees >= 157.5 || azimuthInDegrees < -157.5 -> "south"
                        azimuthInDegrees >= -157.5 && azimuthInDegrees < -112.5 -> "southwest"
                        azimuthInDegrees >= -112.5 && azimuthInDegrees < -67.5 -> "west"
                        else -> "northwest"
                    }

                    // Announce the direction
                    speak("Facing $direction", TextToSpeech.QUEUE_ADD)
                }
            } catch (e: Exception) {
                // Silent error handling for sensor issues
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE || requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startNavigation()
            } else {
                Toast.makeText(this, "Permissions are required for navigation assistance.", Toast.LENGTH_LONG).show()
                speak("Permissions are required for navigation assistance.")
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (isNavigating) {
            // Re-register sensor listeners
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Unregister sensor listeners to save battery
        sensorManager.unregisterListener(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
