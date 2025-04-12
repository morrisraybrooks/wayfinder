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
 * Activity that detects objects in the camera view.
 * Uses ML Kit's object detection to identify objects and provide audio feedback.
 */
class ObjectDetectionActivity : BaseAccessibilityActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var objectDetectionInstructions: TextView
    private lateinit var detectedObjectsTextView: TextView
    private lateinit var toggleDetectionButton: Button
    private lateinit var backButton: Button

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isAnalyzing = false

    private val CAMERA_PERMISSION_CODE = 100

    // Track the last announced objects to avoid repetitive announcements
    private var lastAnnouncedObjects = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_object_detection)

        // Initialize views
        previewView = findViewById(R.id.previewView)
        objectDetectionInstructions = findViewById(R.id.objectDetectionInstructions)
        detectedObjectsTextView = findViewById(R.id.detectedObjectsTextView)
        toggleDetectionButton = findViewById(R.id.toggleDetectionButton)
        backButton = findViewById(R.id.backButton)

        // Set up button click listeners
        toggleDetectionButton.setOnClickListener {
            if (isAnalyzing) {
                stopObjectDetection()
            } else {
                checkPermissionAndStartCamera()
            }
        }

        backButton.setOnClickListener {
            finish()
        }

        // Set initial button text
        toggleDetectionButton.text = getString(R.string.start_detection_button)
        toggleDetectionButton.contentDescription = getString(R.string.start_detection_button)
    }

    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.object_detection_instructions))
    }

    private fun checkPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startObjectDetection()
        }
    }

    private fun startObjectDetection() {
        if (!isAnalyzing) {
            isAnalyzing = true

            // Update UI
            toggleDetectionButton.text = getString(R.string.stop_detection_button)
            toggleDetectionButton.contentDescription = getString(R.string.stop_detection_button)
            detectedObjectsTextView.text = getString(R.string.looking_for_objects)
            speak(getString(R.string.looking_for_objects))

            // Clear the last announced objects
            lastAnnouncedObjects.clear()

            // Start camera and analysis
            startCamera()

            // Provide haptic feedback
            vibrate()
        }
    }

    private fun stopObjectDetection() {
        if (isAnalyzing) {
            isAnalyzing = false

            // Update UI
            toggleDetectionButton.text = getString(R.string.start_detection_button)
            toggleDetectionButton.contentDescription = getString(R.string.start_detection_button)

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
                    it.setAnalyzer(cameraExecutor, ObjectAnalyzer())
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

    // Data class for tracking objects across frames
    private data class TrackedObject(
        val label: String,
        var confidence: Float,
        var lastSeenTimestamp: Long,
        var consecutiveDetections: Int,
        var boundingBox: android.graphics.Rect,
        var announced: Boolean = false,
        var isPriority: Boolean = false
    )

    private inner class ObjectAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTimestamp = 0L
        private val ANALYSIS_INTERVAL = 1000L // 1 second between analyses for more responsive detection

        // Set up the object detector options with improved settings
        private val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()  // Enable classification
            .enableMultipleObjects() // Enable multiple object detection
            .build()

        private val objectDetector = ObjectDetection.getClient(options)

        // Track objects across frames for more stable detection
        private val trackedObjects = mutableMapOf<String, TrackedObject>()

        // Priority objects that should be announced immediately
        private val priorityObjects = setOf(
            "person", "car", "bicycle", "motorcycle", "bus", "truck", "traffic light",
            "fire hydrant", "stop sign", "dog", "cat", "stairs", "door", "elevator"
        )

        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()

            if (currentTimestamp - lastAnalysisTimestamp >= ANALYSIS_INTERVAL) {
                val mediaImage = imageProxy.image
                if (mediaImage != null && isAnalyzing) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                    // Process the image with object detector
                    objectDetector.process(image)
                        .addOnSuccessListener { detectedObjects ->
                            if (detectedObjects.isEmpty()) {
                                detectedObjectsTextView.text = getString(R.string.no_objects_detected)
                                // Don't speak this every time to avoid too much feedback
                            } else {
                                processDetectedObjects(detectedObjects)
                            }

                            lastAnalysisTimestamp = currentTimestamp
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this@ObjectDetectionActivity, "Object detection failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
            val currentTime = System.currentTimeMillis()
            val objectLabels = mutableListOf<String>()
            val newObjects = mutableListOf<TrackedObject>()
            val objectsToAnnounce = mutableListOf<TrackedObject>()

            // Mark all tracked objects as not seen in this frame
            val objectsToRemove = mutableListOf<String>()
            for ((label, trackedObject) in trackedObjects) {
                // Remove objects that haven't been seen for 5 seconds
                if (currentTime - trackedObject.lastSeenTimestamp > 5000) {
                    objectsToRemove.add(label)
                }
            }

            // Remove old objects
            objectsToRemove.forEach { trackedObjects.remove(it) }

            // Process newly detected objects
            for (detectedObject in detectedObjects) {
                // Get the highest confidence label for this object
                val highestConfidenceLabel = detectedObject.labels.maxByOrNull { it.confidence }

                if (highestConfidenceLabel != null) {
                    val label = highestConfidenceLabel.text
                    val confidence = highestConfidenceLabel.confidence

                    // Use different confidence thresholds based on object type
                    val confidenceThreshold = when {
                        priorityObjects.contains(label.toLowerCase()) -> 0.4f // Lower threshold for important objects
                        else -> 0.6f // Higher threshold for other objects to reduce false positives
                    }

                    if (confidence > confidenceThreshold) {
                        objectLabels.add(label)

                        // Update tracked object or create a new one
                        if (trackedObjects.containsKey(label)) {
                            val trackedObject = trackedObjects[label]!!
                            trackedObject.confidence = confidence
                            trackedObject.lastSeenTimestamp = currentTime
                            trackedObject.consecutiveDetections++
                            trackedObject.boundingBox = detectedObject.boundingBox

                            // If object has been consistently detected and not announced yet
                            if (trackedObject.consecutiveDetections >= 3 && !trackedObject.announced) {
                                trackedObject.announced = true
                                objectsToAnnounce.add(trackedObject)
                            }
                        } else {
                            // Create new tracked object
                            val isPriority = priorityObjects.contains(label.toLowerCase())
                            val newObject = TrackedObject(
                                label = label,
                                confidence = confidence,
                                lastSeenTimestamp = currentTime,
                                consecutiveDetections = 1,
                                boundingBox = detectedObject.boundingBox,
                                isPriority = isPriority
                            )
                            trackedObjects[label] = newObject
                            newObjects.add(newObject)

                            // Announce priority objects immediately
                            if (isPriority) {
                                newObject.announced = true
                                objectsToAnnounce.add(newObject)
                            }
                        }
                    }
                }
            }

            // Update the UI with all detected objects
            if (objectLabels.isNotEmpty()) {
                val objectsText = objectLabels.joinToString(", ")
                val resultText = getString(R.string.objects_detected, objectsText)
                detectedObjectsTextView.text = resultText

                // Announce objects with spatial information
                if (objectsToAnnounce.isNotEmpty()) {
                    announceObjectsWithLocation(objectsToAnnounce)
                }
            }
        }

        private fun announceObjectsWithLocation(objectsToAnnounce: List<TrackedObject>) {
            // Sort objects by priority first, then by confidence
            val sortedObjects = objectsToAnnounce.sortedWith(compareByDescending<TrackedObject> { it.isPriority }
                .thenByDescending { it.confidence })

            // Limit to top 3 objects to avoid information overload
            val topObjects = sortedObjects.take(3)

            for (obj in topObjects) {
                // Determine object position (left, center, right, etc.)
                val position = getObjectPosition(obj.boundingBox)
                val distance = estimateDistance(obj.boundingBox)

                // Create announcement with position and distance
                val announcement = when {
                    distance == "close" -> getString(R.string.object_close, obj.label, position)
                    distance == "medium" -> getString(R.string.object_medium, obj.label, position)
                    else -> getString(R.string.object_far, obj.label, position)
                }

                // Use QUEUE_ADD to announce multiple objects
                speak(announcement, TextToSpeech.QUEUE_ADD)

                // Add haptic feedback for close objects
                if (distance == "close" && obj.isPriority) {
                    vibrate(300)
                }
            }
        }

        private fun getObjectPosition(boundingBox: android.graphics.Rect): String {
            val centerX = boundingBox.centerX()
            val imageWidth = previewView.width

            return when {
                centerX < imageWidth / 3 -> getString(R.string.position_left)
                centerX < 2 * imageWidth / 3 -> getString(R.string.position_center)
                else -> getString(R.string.position_right)
            }
        }

        private fun estimateDistance(boundingBox: android.graphics.Rect): String {
            val boxArea = boundingBox.width() * boundingBox.height()
            val imageArea = previewView.width * previewView.height
            val areaRatio = boxArea.toFloat() / imageArea

            return when {
                areaRatio > 0.25 -> "close" // Object takes up more than 25% of the screen
                areaRatio > 0.1 -> "medium" // Object takes up more than 10% of the screen
                else -> "far"
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startObjectDetection()
            } else {
                Toast.makeText(this, "Camera permission is required for object detection.", Toast.LENGTH_LONG).show()
                speak("Camera permission is required for object detection.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
