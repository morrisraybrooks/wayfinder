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
    
    // Currency denomination mappings - text found on bills to their values
    private val usdTextToDenomination = mapOf(
        // Word forms found on US bills
        "ONE" to "1",
        "TWO" to "2",
        "FIVE" to "5",
        "TEN" to "10",
        "TWENTY" to "20",
        "FIFTY" to "50",
        "ONE HUNDRED" to "100",
        "HUNDRED" to "100"
    )

    // Patterns to identify USD bills - looking for text actually printed on bills
    private val usdPatterns = listOf(
        // Look for denomination words
        Pattern.compile("\\b(ONE HUNDRED|HUNDRED)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(FIFTY)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(TWENTY)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(TEN)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(FIVE)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(TWO)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(ONE)\\b", Pattern.CASE_INSENSITIVE),
        // Look for numeric values with dollar signs or "DOLLARS"
        Pattern.compile("\\$\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+)\\s*DOLLARS?", Pattern.CASE_INSENSITIVE),
        // Common text found on US bills
        Pattern.compile("FEDERAL RESERVE NOTE", Pattern.CASE_INSENSITIVE),
        Pattern.compile("UNITED STATES OF AMERICA", Pattern.CASE_INSENSITIVE),
        Pattern.compile("THE UNITED STATES", Pattern.CASE_INSENSITIVE),
        Pattern.compile("THIS NOTE IS LEGAL TENDER", Pattern.CASE_INSENSITIVE),
        Pattern.compile("IN GOD WE TRUST", Pattern.CASE_INSENSITIVE)
    )

    // Patterns for Euro bills
    private val euroPatterns = listOf(
        Pattern.compile("(5|10|20|50|100|200|500)\\s*€", Pattern.CASE_INSENSITIVE),
        Pattern.compile("€\\s*(5|10|20|50|100|200|500)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(5|10|20|50|100|200|500)\\s*EURO", Pattern.CASE_INSENSITIVE),
        Pattern.compile("EUROPEAN CENTRAL BANK", Pattern.CASE_INSENSITIVE),
        Pattern.compile("BCE ECB EZB EKT EKP", Pattern.CASE_INSENSITIVE)
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
        private val ANALYSIS_INTERVAL = 1500L // 1.5 seconds between analyses
        private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        private var lastSpokenResult = ""
        private var lastSpokenTime = 0L
        private val SPEAK_COOLDOWN = 3000L // Don't repeat same result within 3 seconds

        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()

            if (currentTimestamp - lastAnalysisTimestamp >= ANALYSIS_INTERVAL) {
                val mediaImage = imageProxy.image
                if (mediaImage != null && isAnalyzing) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                    // Process the image with text recognition
                    textRecognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val text = visionText.text.uppercase()

                            if (text.isEmpty()) {
                                updateResult(getString(R.string.no_currency_detected), false)
                            } else {
                                // Try to identify currency
                                val result = identifyCurrency(text)
                                if (result != null) {
                                    updateResult(result, true)
                                } else {
                                    // Check if we see any currency-related text
                                    if (looksLikeCurrency(text)) {
                                        updateResult("Possible currency detected. Please adjust angle or lighting.", false)
                                    } else {
                                        updateResult(getString(R.string.no_currency_detected), false)
                                    }
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

        private fun updateResult(result: String, shouldSpeak: Boolean) {
            runOnUiThread {
                currencyResultTextView.text = result
            }

            // Only speak if it's a new result or enough time has passed
            val currentTime = System.currentTimeMillis()
            if (shouldSpeak && (result != lastSpokenResult || currentTime - lastSpokenTime > SPEAK_COOLDOWN)) {
                speak(result, TextToSpeech.QUEUE_FLUSH)
                vibrate() // Haptic feedback when currency detected
                lastSpokenResult = result
                lastSpokenTime = currentTime
            }
        }

        private fun identifyCurrency(text: String): String? {
            // First, try to identify USD
            val usdResult = identifyUSD(text)
            if (usdResult != null) return usdResult

            // Try to identify Euro
            val euroResult = identifyEuro(text)
            if (euroResult != null) return euroResult

            return null
        }

        private fun identifyUSD(text: String): String? {
            // Look for denomination words on US bills
            for ((word, value) in usdTextToDenomination) {
                if (text.contains(word)) {
                    // Make sure it's not just a random occurrence - check for other bill indicators
                    if (text.contains("DOLLAR") || text.contains("FEDERAL") ||
                        text.contains("UNITED STATES") || text.contains("RESERVE") ||
                        text.contains("LEGAL TENDER") || text.contains("NOTE") ||
                        text.contains("AMERICA") || text.contains("GOD WE TRUST") ||
                        text.contains("WASHINGTON") || text.contains("LINCOLN") ||
                        text.contains("HAMILTON") || text.contains("JACKSON") ||
                        text.contains("GRANT") || text.contains("FRANKLIN")) {
                        return "$value dollar bill detected"
                    }
                }
            }

            // Look for numeric patterns with dollar indicators
            val dollarPattern = Pattern.compile("\\$\\s*(\\d+)")
            val dollarMatcher = dollarPattern.matcher(text)
            if (dollarMatcher.find()) {
                val amount = dollarMatcher.group(1)
                if (amount in listOf("1", "2", "5", "10", "20", "50", "100")) {
                    return "$amount dollar bill detected"
                }
            }

            // Check for "X DOLLARS" pattern
            val dollarsPattern = Pattern.compile("(\\d+)\\s*DOLLARS?")
            val dollarsMatcher = dollarsPattern.matcher(text)
            if (dollarsMatcher.find()) {
                val amount = dollarsMatcher.group(1)
                if (amount in listOf("1", "2", "5", "10", "20", "50", "100")) {
                    return "$amount dollar bill detected"
                }
            }

            return null
        }

        private fun identifyEuro(text: String): String? {
            // Look for Euro denominations
            val euroAmounts = listOf("5", "10", "20", "50", "100", "200", "500")

            for (amount in euroAmounts) {
                // Check various Euro patterns
                if (text.contains("$amount €") || text.contains("€ $amount") ||
                    text.contains("$amount EURO") || text.contains("EURO $amount")) {
                    return "$amount Euro note detected"
                }
            }

            // Check for Euro Central Bank text with any amount
            if (text.contains("EUROPEAN CENTRAL BANK") || text.contains("BCE") || text.contains("ECB")) {
                for (amount in euroAmounts) {
                    if (text.contains(amount)) {
                        return "$amount Euro note detected"
                    }
                }
                return "Euro note detected - please hold steady to identify denomination"
            }

            return null
        }

        private fun looksLikeCurrency(text: String): Boolean {
            // Check if text contains any currency indicators
            val currencyIndicators = listOf(
                "DOLLAR", "FEDERAL", "RESERVE", "UNITED STATES", "AMERICA",
                "EURO", "€", "$", "BANK", "NOTE", "TENDER", "CURRENCY"
            )
            return currencyIndicators.any { text.contains(it) }
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
