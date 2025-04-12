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
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Activity that provides comprehensive scene descriptions.
 * Uses ML Kit's image labeling to identify multiple objects and describe the environment.
 */
class SceneDescriptionActivity : BaseAccessibilityActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var sceneDescriptionInstructions: TextView
    private lateinit var sceneDescriptionTextView: TextView
    private lateinit var startSceneDescriptionButton: Button
    private lateinit var stopSceneDescriptionButton: Button
    private lateinit var backButton: Button

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isAnalyzing = false

    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scene_description)

        // Initialize views
        previewView = findViewById(R.id.previewView)
        sceneDescriptionInstructions = findViewById(R.id.sceneDescriptionInstructions)
        sceneDescriptionTextView = findViewById(R.id.sceneDescriptionTextView)
        startSceneDescriptionButton = findViewById(R.id.startSceneDescriptionButton)
        stopSceneDescriptionButton = findViewById(R.id.stopSceneDescriptionButton)
        backButton = findViewById(R.id.backButton)

        // Set up button click listeners
        startSceneDescriptionButton.setOnClickListener {
            checkPermissionAndStartCamera()
        }

        stopSceneDescriptionButton.setOnClickListener {
            stopSceneDescription()
        }

        backButton.setOnClickListener {
            finish()
        }

        // Initially hide the stop button
        stopSceneDescriptionButton.visibility = View.GONE
    }

    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.scene_description_instructions))
    }

    private fun checkPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startSceneDescription()
        }
    }

    private fun startSceneDescription() {
        if (!isAnalyzing) {
            isAnalyzing = true

            // Update UI
            startSceneDescriptionButton.visibility = View.GONE
            stopSceneDescriptionButton.visibility = View.VISIBLE
            sceneDescriptionTextView.text = getString(R.string.scene_analyzing)
            speak(getString(R.string.scene_analyzing))

            // Start camera and analysis
            startCamera()

            // Provide haptic feedback
            vibrate()
        }
    }

    private fun stopSceneDescription() {
        if (isAnalyzing) {
            isAnalyzing = false

            // Update UI
            startSceneDescriptionButton.visibility = View.VISIBLE
            stopSceneDescriptionButton.visibility = View.GONE

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
                    it.setAnalyzer(cameraExecutor, SceneAnalyzer())
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

    private inner class SceneAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTimestamp = 0L
        private val ANALYSIS_INTERVAL = 3000L // 3 seconds between analyses

        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()

            if (currentTimestamp - lastAnalysisTimestamp >= ANALYSIS_INTERVAL) {
                val mediaImage = imageProxy.image
                if (mediaImage != null && isAnalyzing) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                    // Set up image labeler
                    val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

                    // Process the image
                    labeler.process(image)
                        .addOnSuccessListener { labels ->
                            if (labels.isEmpty()) {
                                sceneDescriptionTextView.text = getString(R.string.no_scene_detected)
                                speak(getString(R.string.no_scene_detected))
                            } else {
                                // Generate scene description from labels
                                val description = generateSceneDescription(labels)
                                sceneDescriptionTextView.text = description
                                speak(description)
                            }
                            lastAnalysisTimestamp = currentTimestamp
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this@SceneDescriptionActivity, "Analysis failed: ${e.message}", Toast.LENGTH_SHORT).show()
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

        private fun generateSceneDescription(labels: List<com.google.mlkit.vision.label.ImageLabel>): String {
            // Sort labels by confidence
            val sortedLabels = labels.sortedByDescending { it.confidence }

            // Get the top 5 labels or fewer if there aren't 5
            val topLabels = sortedLabels.take(5)

            // Generate description based on the number of labels
            return when {
                topLabels.isEmpty() -> getString(R.string.no_scene_detected)
                topLabels.size == 1 -> {
                    val label = topLabels[0]
                    "I can see a ${label.text} in front of you."
                }
                topLabels.size == 2 -> {
                    val (label1, label2) = topLabels
                    "I can see a ${label1.text} and a ${label2.text} in front of you."
                }
                else -> {
                    val mainLabel = topLabels[0]
                    val otherLabels = topLabels.subList(1, topLabels.size)
                    val otherLabelsText = otherLabels.joinToString(", ") { it.text }

                    "You appear to be in a scene with a ${mainLabel.text}. I can also see $otherLabelsText."
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSceneDescription()
            } else {
                Toast.makeText(this, "Camera permission is required for scene description.", Toast.LENGTH_LONG).show()
                speak("Camera permission is required for scene description.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
