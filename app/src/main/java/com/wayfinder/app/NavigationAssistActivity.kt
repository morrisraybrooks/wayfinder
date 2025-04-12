package com.wayfinder.app

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.abs

/**
 * Activity that provides navigation assistance.
 * Uses object detection to identify obstacles and provides audio feedback.
 */
class NavigationAssistActivity : BaseAccessibilityActivity(), SensorEventListener {

    private lateinit var previewView: PreviewView
    private lateinit var navigationInstructions: TextView
    private lateinit var navigationStatusTextView: TextView
    private lateinit var toggleNavigationButton: Button
    private lateinit var backButton: Button
    
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isNavigating = false
    
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
                    it.setAnalyzer(cameraExecutor, NavigationAnalyzer())
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
                            if (detectedObjects.isNotEmpty()) {
                                processDetectedObjects(detectedObjects)
                            }
                            
                            lastAnalysisTimestamp = currentTimestamp
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this@NavigationAssistActivity, "Object detection failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
        
        private fun processDetectedObjects(detectedObjects: List<DetectedObject>) {
            // Update the detected obstacles map
            val currentObstacles = mutableMapOf<String, Float>()
            
            for (detectedObject in detectedObjects) {
                // Get the highest confidence label for this object
                val highestConfidenceLabel = detectedObject.labels.maxByOrNull { it.confidence }
                
                if (highestConfidenceLabel != null && highestConfidenceLabel.confidence > 0.5) {
                    val label = highestConfidenceLabel.text
                    
                    // Calculate approximate distance based on bounding box size
                    // This is a very rough estimate - in a real app, you'd use depth sensing
                    val boundingBox = detectedObject.boundingBox
                    val objectWidth = boundingBox.width().toFloat()
                    val objectHeight = boundingBox.height().toFloat()
                    val imageWidth = previewView.width.toFloat()
                    val imageHeight = previewView.height.toFloat()
                    
                    // Rough distance estimate based on object size relative to screen
                    val sizeRatio = (objectWidth / imageWidth + objectHeight / imageHeight) / 2
                    val estimatedDistance = 1 / sizeRatio * 2 // Very rough approximation
                    
                    // Limit to reasonable values and round to one decimal place
                    val clampedDistance = (estimatedDistance.coerceIn(0.5f, 10.0f) * 10).toInt() / 10.0f
                    
                    // Store in current obstacles map
                    currentObstacles[label] = clampedDistance
                }
            }
            
            // Update the UI with detected obstacles
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
                        // Announce the closest obstacle
                        val obstacleMessage = getString(R.string.obstacle_warning, closestObstacle.key, closestObstacle.value)
                        speak(obstacleMessage)
                        
                        // Vibrate with intensity based on distance
                        val vibrationDuration = (100 + (500 / closestObstacle.value).toInt()).coerceIn(100, 500)
                        vibrate(vibrationDuration.toLong())
                        
                        lastObstacleAnnouncementTime = currentTime
                    }
                }
            }
            
            // Update the detected obstacles map
            detectedObstacles = currentObstacles
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
        
        if (lastAccelerometerSet && lastMagnetometerSet && magnetometer != null) {
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
