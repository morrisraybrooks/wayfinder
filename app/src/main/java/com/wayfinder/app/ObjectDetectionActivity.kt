package com.wayfinder.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.app.AlertDialog
import android.content.SharedPreferences
import android.widget.SeekBar
import android.widget.LinearLayout
import android.view.ViewGroup
import android.view.Gravity
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Activity that detects objects in the camera view.
 * Uses TensorFlow Lite with MobileNet SSD for real object detection with bounding boxes.
 * Provides accurate spatial information (left/center/right, close/medium/far).
 */
class ObjectDetectionActivity : BaseAccessibilityActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var objectDetectionInstructions: TextView
    private lateinit var detectedObjectsTextView: TextView
    private lateinit var toggleDetectionButton: Button
    private lateinit var backButton: Button

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isAnalyzing = false
    private var cameraProvider: ProcessCameraProvider? = null

    private val CAMERA_PERMISSION_CODE = 100
    private val TAG = "ObjectDetection"

    // TensorFlow Lite object detector
    private var tfliteDetector: TFLiteObjectDetector? = null

    // Settings / preferences
    private lateinit var prefs: SharedPreferences
    private var announcementCooldownMs: Long = 3000L
    private var detectionConfidenceThreshold: Float = 0.5f
    private var settingsButton: Button? = null

    // Cached preview dimensions for thread-safe access
    @Volatile private var cachedPreviewWidth: Int = 1080
    @Volatile private var cachedPreviewHeight: Int = 1920

    // Flag to prevent processing after activity is destroyed
    @Volatile private var isActivityActive = true

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

        // Initialize TensorFlow Lite detector
        initializeTFLiteDetector()

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
        // Initialize preferences and settings
        prefs = getSharedPreferences("WayfinderSettings", MODE_PRIVATE)
        loadSettingsFromPrefs()

        // Initialize settings button if present in layout
        settingsButton = findViewById(R.id.settingsButton) as? Button
        settingsButton?.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun initializeTFLiteDetector() {
        tfliteDetector = TFLiteObjectDetector(this)
        val success = tfliteDetector?.initialize() ?: false
        if (success) {
            Log.d(TAG, "TensorFlow Lite detector initialized successfully")
        } else {
            Log.e(TAG, "Failed to initialize TensorFlow Lite detector")
            Toast.makeText(this, "Object detection initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.object_detection_instructions))
    }

    private fun loadSettingsFromPrefs() {
        announcementCooldownMs = (prefs.getInt("detection_announcement_cooldown", 3) * 1000).toLong()
        detectionConfidenceThreshold = prefs.getFloat("detection_confidence_threshold", 0.5f)
    }

    private fun saveSettingsToPrefs(cooldownSeconds: Int, confidenceThreshold: Float) {
        prefs.edit()
            .putInt("detection_announcement_cooldown", cooldownSeconds)
            .putFloat("detection_confidence_threshold", confidenceThreshold)
            .apply()
        // Update runtime values
        announcementCooldownMs = (cooldownSeconds * 1000).toLong()
        detectionConfidenceThreshold = confidenceThreshold
    }

    private fun showSettingsDialog() {
        val context = this

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 8)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val cooldownLabel = TextView(context).apply { text = "Announcement cooldown (seconds)" }
        val cooldownSeek = SeekBar(context).apply {
            max = 9 // represents 1..10
            progress = ((announcementCooldownMs / 1000).toInt() - 1).coerceIn(0, 9)
        }
        val cooldownValue = TextView(context).apply {
            text = "${(announcementCooldownMs/1000)}s"
            gravity = Gravity.CENTER
        }

        cooldownSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val valSec = progress + 1
                cooldownValue.text = "${valSec}s"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val thresholdLabel = TextView(context).apply { text = "Detection confidence threshold" }
        val thresholdSeek = SeekBar(context).apply {
            max = 50 // maps to 0.25..0.75
            val prog = ((detectionConfidenceThreshold - 0.25f) / 0.5f * 50f).toInt().coerceIn(0,50)
            progress = prog
        }
        val thresholdValue = TextView(context).apply { text = "${String.format("%.2f", detectionConfidenceThreshold)}"; gravity = Gravity.CENTER }

        thresholdSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val thr = 0.25f + (progress.toFloat()/50f)*0.5f
                thresholdValue.text = String.format("%.2f", thr)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        layout.addView(cooldownLabel)
        layout.addView(cooldownSeek)
        layout.addView(cooldownValue)
        layout.addView(thresholdLabel)
        layout.addView(thresholdSeek)
        layout.addView(thresholdValue)

        AlertDialog.Builder(context)
            .setTitle("Detection Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val cooldownSec = (cooldownSeek.progress + 1)
                val thr = 0.25f + (thresholdSeek.progress.toFloat()/50f)*0.5f
                saveSettingsToPrefs(cooldownSec, thr)
            }
            .setNegativeButton("Cancel", null)
            .show()
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

            // Unbind camera
            cameraProvider?.unbindAll()

            // Provide haptic feedback
            vibrate()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            if (!isActivityActive) return@addListener

            val cameraProvider = cameraProviderFuture.get()
            this.cameraProvider = cameraProvider

            // Cache preview dimensions for thread-safe access from analyzer
            previewView.post {
                if (isActivityActive && previewView.width > 0 && previewView.height > 0) {
                    cachedPreviewWidth = previewView.width
                    cachedPreviewHeight = previewView.height
                    Log.d(TAG, "Cached preview dimensions: $cachedPreviewWidth x $cachedPreviewHeight")
                }
            }

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
                if (!isActivityActive) return@addListener
                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                if (isActivityActive) {
                    Toast.makeText(this, "Camera initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
        private val ANALYSIS_INTERVAL = 500L // Analysis every 500ms for good responsiveness

        // Track objects across frames for more stable detection
        private val trackedObjects = mutableMapOf<String, TrackedObject>()

        // Cooldown for announcements to prevent rapid-fire speech
        private var lastAnnouncementTime = 0L

        override fun analyze(imageProxy: ImageProxy) {
            // Check if activity is still active before processing
            if (!isActivityActive) {
                imageProxy.close()
                return
            }

            val currentTimestamp = System.currentTimeMillis()

            if (currentTimestamp - lastAnalysisTimestamp >= ANALYSIS_INTERVAL) {
                if (isAnalyzing && isActivityActive && tfliteDetector?.isReady() == true) {
                    try {
                        // Convert ImageProxy to Bitmap
                        val bitmap = imageProxyToBitmap(imageProxy)
                        if (bitmap != null) {
                            // Run TFLite detection
                            val detections = tfliteDetector?.detect(bitmap) ?: emptyList()
                            Log.d(TAG, "TFLite detected ${detections.size} objects")

                            // Process results
                            processDetections(detections, bitmap.width, bitmap.height)
                            lastAnalysisTimestamp = currentTimestamp
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during detection", e)
                    }
                }
                imageProxy.close()
            } else {
                imageProxy.close()
            }
        }

        private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
            return try {
                val yBuffer = imageProxy.planes[0].buffer
                val uBuffer = imageProxy.planes[1].buffer
                val vBuffer = imageProxy.planes[2].buffer

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)
                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                val yuvImage = android.graphics.YuvImage(
                    nv21,
                    android.graphics.ImageFormat.NV21,
                    imageProxy.width,
                    imageProxy.height,
                    null
                )

                val out = java.io.ByteArrayOutputStream()
                yuvImage.compressToJpeg(
                    android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
                    80,
                    out
                )
                val imageBytes = out.toByteArray()
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                // Rotate bitmap if needed based on rotation degrees
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                if (rotationDegrees != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(rotationDegrees.toFloat())
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error converting ImageProxy to Bitmap", e)
                null
            }
        }

        private fun processDetections(detections: List<TFLiteObjectDetector.Detection>, imageWidth: Int, imageHeight: Int) {
            val currentTime = System.currentTimeMillis()
            val objectLabels = mutableListOf<String>()
            val objectsToAnnounce = mutableListOf<TrackedObject>()

            // Remove objects that haven't been seen for 5 seconds
            val objectsToRemove = trackedObjects.filter {
                currentTime - it.value.lastSeenTimestamp > 5000
            }.keys.toList()
            objectsToRemove.forEach { trackedObjects.remove(it) }

            // Process TFLite detections
            for (detection in detections) {
                val label = detection.label
                val confidence = detection.confidence

                // Convert RectF to Rect, scaling to preview dimensions
                val scaleX = cachedPreviewWidth.toFloat() / imageWidth
                val scaleY = cachedPreviewHeight.toFloat() / imageHeight
                val boundingBox = android.graphics.Rect(
                    (detection.boundingBox.left * scaleX).toInt(),
                    (detection.boundingBox.top * scaleY).toInt(),
                    (detection.boundingBox.right * scaleX).toInt(),
                    (detection.boundingBox.bottom * scaleY).toInt()
                )

                updateOrCreateTrackedObject(label, confidence, currentTime, boundingBox, objectLabels, objectsToAnnounce)
            }

            // Update the UI with all detected objects
            if (isActivityActive) {
                runOnUiThread {
                    if (!isActivityActive) return@runOnUiThread
                    try {
                        if (objectLabels.isNotEmpty()) {
                            val objectsText = objectLabels.distinct().joinToString(", ")
                            val resultText = getString(R.string.objects_detected, objectsText)
                            detectedObjectsTextView.text = resultText

                            // Announce objects with spatial information (with cooldown)
                            val timeSinceLastAnnouncement = currentTime - lastAnnouncementTime
                            Log.d(TAG, "Announcement check: objectsToAnnounce=${objectsToAnnounce.size}, timeSinceLastAnnouncement=${timeSinceLastAnnouncement}ms, cooldown=${this@ObjectDetectionActivity.announcementCooldownMs}ms")
                            if (objectsToAnnounce.isNotEmpty() &&
                                timeSinceLastAnnouncement >= this@ObjectDetectionActivity.announcementCooldownMs) {
                                announceObjectsWithLocation(objectsToAnnounce)
                                lastAnnouncementTime = currentTime
                            }
                        } else {
                            detectedObjectsTextView.text = getString(R.string.no_objects_detected)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating UI", e)
                    }
                }
            }
        }

        private fun updateOrCreateTrackedObject(
            label: String,
            confidence: Float,
            currentTime: Long,
            boundingBox: android.graphics.Rect,
            objectLabels: MutableList<String>,
            objectsToAnnounce: MutableList<TrackedObject>
        ) {
            objectLabels.add(label)

            // Update tracked object or create a new one
            if (trackedObjects.containsKey(label)) {
                trackedObjects[label]?.let { trackedObject ->
                    trackedObject.confidence = confidence
                    trackedObject.lastSeenTimestamp = currentTime
                    trackedObject.consecutiveDetections++
                    trackedObject.boundingBox = boundingBox

                    // Check if enough time has passed since last announcement
                    val timeSinceLastAnnouncement = currentTime - lastAnnouncementTime

                    // For all objects (priority and non-priority):
                    // 1. Need at least 2 consecutive detections for stability
                    // 2. Either not announced yet, OR cooldown has passed (for re-announcement)
                    // Priority objects are announced more aggressively (always re-announced after cooldown)
                    // Non-priority objects are announced once, then re-announced after a longer cooldown (2x)
                    val requiredCooldown = if (trackedObject.isPriority) {
                        this@ObjectDetectionActivity.announcementCooldownMs
                    } else {
                        this@ObjectDetectionActivity.announcementCooldownMs * 2  // Longer cooldown for non-priority
                    }

                    val shouldAnnounce = trackedObject.consecutiveDetections >= 2 &&
                        (!trackedObject.announced || timeSinceLastAnnouncement >= requiredCooldown)

                    if (shouldAnnounce) {
                        // Don't mark as announced yet - only mark after actually speaking
                        // This prevents objects from being "announced" but never spoken
                        objectsToAnnounce.add(trackedObject)
                        Log.d(TAG, "Adding to announce list: ${trackedObject.label} (priority=${trackedObject.isPriority}, cooldownPassed=${timeSinceLastAnnouncement >= requiredCooldown})")
                    }
                }
            } else {
                // Create new tracked object - use TFLite detector's priority check
                val isPriority = tfliteDetector?.isPriorityObject(label) ?: false
                val newObject = TrackedObject(
                    label = label,
                    confidence = confidence,
                    lastSeenTimestamp = currentTime,
                    consecutiveDetections = 1,
                    boundingBox = boundingBox,
                    isPriority = isPriority
                )
                trackedObjects[label] = newObject

                // Announce priority objects immediately (but don't mark as announced until actually spoken)
                if (isPriority) {
                    objectsToAnnounce.add(newObject)
                    Log.d(TAG, "Priority object detected and added to announce list: $label (confidence: $confidence)")
                } else {
                    Log.d(TAG, "New non-priority object tracked: $label (needs 2 consecutive detections)")
                }
            }
        }

        private fun announceObjectsWithLocation(objectsToAnnounce: List<TrackedObject>) {
            // Priority order: Door area first, then safety-critical objects
            val sortedObjects = objectsToAnnounce.sortedWith(
                compareByDescending<TrackedObject> {
                    // Give "Door area" highest priority since that's what users are looking for
                    it.label.equals("Door area", ignoreCase = true)
                }
                .thenByDescending { it.isPriority }
                .thenByDescending { it.confidence }
            )

            // Only announce 1 object at a time to avoid confusion
            val topObject = sortedObjects.firstOrNull() ?: return

            // Mark ONLY the announced object as announced (not all objects in the list)
            topObject.announced = true

            Log.d(TAG, "Announcing object: ${topObject.label}")

            // Determine object position (left, center, right, etc.)
            val position = getObjectPosition(topObject.boundingBox)
            val distance = estimateDistance(topObject.boundingBox)

            // Create announcement with position and distance
            val announcement = when {
                distance == "close" -> getString(R.string.object_close, topObject.label, position)
                distance == "medium" -> getString(R.string.object_medium, topObject.label, position)
                else -> getString(R.string.object_far, topObject.label, position)
            }

            Log.d(TAG, "Speaking: $announcement")

            // Use QUEUE_FLUSH to replace any pending speech
            speak(announcement, TextToSpeech.QUEUE_FLUSH)

            // Add haptic feedback for close objects
            if (distance == "close" && topObject.isPriority) {
                vibrate(300)
            }
        }

        private fun getObjectPosition(boundingBox: android.graphics.Rect): String {
            val centerX = boundingBox.centerX()
            // Use cached dimensions for thread-safe access
            val imageWidth = cachedPreviewWidth.coerceAtLeast(1)

            return when {
                centerX < imageWidth / 3 -> getString(R.string.position_left)
                centerX < 2 * imageWidth / 3 -> getString(R.string.position_center)
                else -> getString(R.string.position_right)
            }
        }

        private fun estimateDistance(boundingBox: android.graphics.Rect): String {
            val boxArea = boundingBox.width() * boundingBox.height()
            // Use cached dimensions for thread-safe access
            val imageArea = (cachedPreviewWidth * cachedPreviewHeight).coerceAtLeast(1)
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
        // Mark activity as inactive FIRST to stop processing
        isActivityActive = false
        isAnalyzing = false

        // Unbind camera
        cameraProvider?.unbindAll()

        // Shutdown executor immediately to cancel pending tasks
        try {
            cameraExecutor.shutdown()
            if (!cameraExecutor.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                cameraExecutor.shutdownNow()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down executor", e)
        }

        // Close TensorFlow Lite detector
        try {
            tfliteDetector?.close()
            tfliteDetector = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing TFLite detector", e)
        }

        super.onDestroy()
    }
}
