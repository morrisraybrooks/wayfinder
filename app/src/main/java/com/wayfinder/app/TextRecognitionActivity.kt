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

/**
 * Activity that recognizes and reads text from the camera.
 * Uses ML Kit's text recognition to identify and read text aloud.
 */
class TextRecognitionActivity : BaseAccessibilityActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var textRecognitionInstructions: TextView
    private lateinit var recognizedTextView: TextView
    private lateinit var toggleRecognitionButton: Button
    private lateinit var backButton: Button

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isAnalyzing = false
    private var cameraProvider: ProcessCameraProvider? = null

    private val CAMERA_PERMISSION_CODE = 100

    // Track the last read text to avoid repetitive reading
    private var lastReadText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_recognition)

        // Initialize views
        previewView = findViewById(R.id.previewView)
        textRecognitionInstructions = findViewById(R.id.textRecognitionInstructions)
        recognizedTextView = findViewById(R.id.recognizedTextView)
        toggleRecognitionButton = findViewById(R.id.toggleRecognitionButton)
        backButton = findViewById(R.id.backButton)

        // Set up button click listeners
        toggleRecognitionButton.setOnClickListener {
            if (isAnalyzing) {
                stopTextRecognition()
            } else {
                checkPermissionAndStartCamera()
            }
        }

        backButton.setOnClickListener {
            finish()
        }

        // Set initial button text
        toggleRecognitionButton.text = getString(R.string.start_text_recognition_button)
        toggleRecognitionButton.contentDescription = getString(R.string.start_text_recognition_button)
    }

    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.text_recognition_instructions))
    }

    private fun checkPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startTextRecognition()
        }
    }

    private fun startTextRecognition() {
        if (!isAnalyzing) {
            isAnalyzing = true

            // Update UI
            toggleRecognitionButton.text = getString(R.string.stop_text_recognition_button)
            toggleRecognitionButton.contentDescription = getString(R.string.stop_text_recognition_button)
            recognizedTextView.text = getString(R.string.looking_for_text)
            speak(getString(R.string.looking_for_text))

            // Clear the last read text
            lastReadText = ""

            // Start camera and analysis
            startCamera()

            // Provide haptic feedback
            vibrate()
        }
    }

    private fun stopTextRecognition() {
        if (isAnalyzing) {
            isAnalyzing = false

            // Update UI
            toggleRecognitionButton.text = getString(R.string.start_text_recognition_button)
            toggleRecognitionButton.contentDescription = getString(R.string.start_text_recognition_button)

            // Unbind camera
            cameraProvider?.unbindAll()

            // Provide haptic feedback
            vibrate()
        }
    }

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
            textAnalyzer = TextAnalyzer()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, textAnalyzer!!)
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

    private inner class TextAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTimestamp = 0L
        private val ANALYSIS_INTERVAL = 3000L // 3 seconds between analyses

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

                            runOnUiThread {
                                if (detectedText.isEmpty()) {
                                    recognizedTextView.text = getString(R.string.no_text_detected)
                                    // Don't speak this every time to avoid too much feedback
                                } else {
                                    // Update the UI with the detected text
                                    recognizedTextView.text = detectedText

                                    // Only read the text if it's different from the last read text
                                    if (detectedText != lastReadText) {
                                        lastReadText = detectedText

                                        // Speak the detected text
                                        speak(getString(R.string.text_detected_reading))

                                        // Split the text into sentences for better TTS experience
                                        val sentences = detectedText.split(Regex("[.!?]"))
                                            .filter { it.trim().isNotEmpty() }
                                            .map { it.trim() + "." }

                                        // Read each sentence with a slight pause between them
                                        sentences.forEachIndexed { index, sentence ->
                                            speak(sentence, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD)
                                        }
                                    }
                                }
                            }

                            lastAnalysisTimestamp = currentTimestamp
                        }
                        .addOnFailureListener { e ->
                            runOnUiThread {
                                Toast.makeText(this@TextRecognitionActivity, "Text recognition failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
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

        fun close() {
            textRecognizer.close()
        }
    }

    // Keep a reference to the analyzer so we can close it
    private var textAnalyzer: TextAnalyzer? = null

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startTextRecognition()
            } else {
                Toast.makeText(this, "Camera permission is required for text recognition.", Toast.LENGTH_LONG).show()
                speak("Camera permission is required for text recognition.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textAnalyzer?.close()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
