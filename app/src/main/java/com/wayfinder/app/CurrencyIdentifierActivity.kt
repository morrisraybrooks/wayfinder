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
 * Activity that identifies currency denominations.
 * Uses text recognition to identify currency values on bills.
 */
class CurrencyIdentifierActivity : BaseAccessibilityActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var currencyInstructionsTextView: TextView
    private lateinit var currencyResultTextView: TextView
    private lateinit var startCurrencyIdentificationButton: Button
    private lateinit var stopCurrencyIdentificationButton: Button
    private lateinit var backButton: Button

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var isAnalyzing = false

    private val CAMERA_PERMISSION_CODE = 100
    
    // Currency patterns to look for
    private val currencyPatterns = mapOf(
        "USD" to listOf(
            Pattern.compile("\\$?\\s*1\\s*(?:dollar|DOLLAR)"),
            Pattern.compile("\\$?\\s*5\\s*(?:dollar|DOLLAR)"),
            Pattern.compile("\\$?\\s*10\\s*(?:dollar|DOLLAR)"),
            Pattern.compile("\\$?\\s*20\\s*(?:dollar|DOLLAR)"),
            Pattern.compile("\\$?\\s*50\\s*(?:dollar|DOLLAR)"),
            Pattern.compile("\\$?\\s*100\\s*(?:dollar|DOLLAR)")
        ),
        "EUR" to listOf(
            Pattern.compile("5\\s*(?:euro|EURO)"),
            Pattern.compile("10\\s*(?:euro|EURO)"),
            Pattern.compile("20\\s*(?:euro|EURO)"),
            Pattern.compile("50\\s*(?:euro|EURO)"),
            Pattern.compile("100\\s*(?:euro|EURO)"),
            Pattern.compile("200\\s*(?:euro|EURO)"),
            Pattern.compile("500\\s*(?:euro|EURO)")
        )
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_currency_identifier)
        
        // Initialize views
        previewView = findViewById(R.id.previewView)
        currencyInstructionsTextView = findViewById(R.id.currencyInstructionsTextView)
        currencyResultTextView = findViewById(R.id.currencyResultTextView)
        startCurrencyIdentificationButton = findViewById(R.id.startCurrencyIdentificationButton)
        stopCurrencyIdentificationButton = findViewById(R.id.stopCurrencyIdentificationButton)
        backButton = findViewById(R.id.backButton)
        
        // Set up button click listeners
        startCurrencyIdentificationButton.setOnClickListener {
            checkPermissionAndStartCamera()
        }
        
        stopCurrencyIdentificationButton.setOnClickListener {
            stopCurrencyIdentification()
        }
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Initially hide the stop button
        stopCurrencyIdentificationButton.visibility = View.GONE
    }
    
    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.currency_identifier_instructions))
    }
    
    private fun checkPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startCurrencyIdentification()
        }
    }
    
    private fun startCurrencyIdentification() {
        if (!isAnalyzing) {
            isAnalyzing = true
            
            // Update UI
            startCurrencyIdentificationButton.visibility = View.GONE
            stopCurrencyIdentificationButton.visibility = View.VISIBLE
            currencyResultTextView.text = ""
            
            // Start camera and analysis
            startCamera()
            
            // Provide haptic feedback
            vibrate()
        }
    }
    
    private fun stopCurrencyIdentification() {
        if (isAnalyzing) {
            isAnalyzing = false

            // Unbind camera to stop processing
            cameraProvider?.unbindAll()

            // Update UI
            startCurrencyIdentificationButton.visibility = View.VISIBLE
            stopCurrencyIdentificationButton.visibility = View.GONE

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
                    it.setAnalyzer(cameraExecutor, CurrencyAnalyzer())
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
    
    private inner class CurrencyAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTimestamp = 0L
        private val ANALYSIS_INTERVAL = 2000L // 2 seconds between analyses
        private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()
            
            if (currentTimestamp - lastAnalysisTimestamp >= ANALYSIS_INTERVAL) {
                val mediaImage = imageProxy.image
                if (mediaImage != null && isAnalyzing) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    
                    // Process the image with text recognition
                    textRecognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val text = visionText.text

                            if (text.isEmpty()) {
                                runOnUiThread {
                                    currencyResultTextView.text = getString(R.string.no_currency_detected)
                                }
                                speak(getString(R.string.no_currency_detected), TextToSpeech.QUEUE_FLUSH)
                            } else {
                                // Check for currency patterns
                                var currencyFound = false

                                for ((currency, patterns) in currencyPatterns) {
                                    for (pattern in patterns) {
                                        val matcher = pattern.matcher(text)
                                        if (matcher.find()) {
                                            val match = matcher.group(0)
                                            val denomination = extractDenomination(match)

                                            val result = getString(R.string.currency_detected, denomination, currency)
                                            runOnUiThread {
                                                currencyResultTextView.text = result
                                            }
                                            speak(result, TextToSpeech.QUEUE_FLUSH)

                                            currencyFound = true
                                            break
                                        }
                                    }
                                    if (currencyFound) break
                                }

                                if (!currencyFound) {
                                    runOnUiThread {
                                        currencyResultTextView.text = getString(R.string.no_currency_detected)
                                    }
                                    // Don't speak this every time to avoid too much feedback
                                }
                            }

                            lastAnalysisTimestamp = currentTimestamp
                        }
                        .addOnFailureListener { e ->
                            runOnUiThread {
                                Toast.makeText(this@CurrencyIdentifierActivity, "Text recognition failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
        
        private fun extractDenomination(text: String?): String {
            if (text == null) return "unknown"
            // Extract the numeric value from the matched text
            val numericPattern = Pattern.compile("\\d+")
            val matcher = numericPattern.matcher(text)
            
            return if (matcher.find()) {
                matcher.group(0) ?: "unknown"
            } else {
                "unknown"
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCurrencyIdentification()
            } else {
                Toast.makeText(this, "Camera permission is required for currency identification.", Toast.LENGTH_LONG).show()
                speak("Camera permission is required for currency identification.")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
