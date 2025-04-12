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
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Activity that scans product barcodes and provides information.
 * Uses ML Kit's barcode scanning to identify products.
 */
class ProductScannerActivity : BaseAccessibilityActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var productInstructionsTextView: TextView
    private lateinit var productResultTextView: TextView
    private lateinit var startProductScanningButton: Button
    private lateinit var stopProductScanningButton: Button
    private lateinit var backButton: Button
    
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isAnalyzing = false
    
    private val CAMERA_PERMISSION_CODE = 100
    
    // Simple product database (in a real app, this would be a proper database or API)
    private val productDatabase = mapOf(
        "0123456789012" to ProductInfo("Cereal", "$4.99", "Breakfast cereal with whole grains"),
        "1234567890123" to ProductInfo("Milk", "$3.49", "1 gallon of whole milk"),
        "2345678901234" to ProductInfo("Bread", "$2.99", "Whole wheat bread loaf"),
        "3456789012345" to ProductInfo("Apples", "$1.99", "Bag of fresh apples"),
        "4567890123456" to ProductInfo("Chicken", "$8.99", "Boneless chicken breast"),
        "5678901234567" to ProductInfo("Rice", "$5.99", "5 pound bag of white rice"),
        "6789012345678" to ProductInfo("Pasta", "$1.49", "16 oz box of spaghetti"),
        "7890123456789" to ProductInfo("Tomato Sauce", "$2.29", "24 oz jar of tomato sauce"),
        "8901234567890" to ProductInfo("Coffee", "$7.99", "12 oz bag of ground coffee"),
        "9012345678901" to ProductInfo("Tea", "$3.99", "Box of 20 tea bags")
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_scanner)
        
        // Initialize views
        previewView = findViewById(R.id.previewView)
        productInstructionsTextView = findViewById(R.id.productInstructionsTextView)
        productResultTextView = findViewById(R.id.productResultTextView)
        startProductScanningButton = findViewById(R.id.startProductScanningButton)
        stopProductScanningButton = findViewById(R.id.stopProductScanningButton)
        backButton = findViewById(R.id.backButton)
        
        // Set up button click listeners
        startProductScanningButton.setOnClickListener {
            checkPermissionAndStartCamera()
        }
        
        stopProductScanningButton.setOnClickListener {
            stopProductScanning()
        }
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Initially hide the stop button
        stopProductScanningButton.visibility = View.GONE
    }
    
    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.product_scanner_instructions))
    }
    
    private fun checkPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startProductScanning()
        }
    }
    
    private fun startProductScanning() {
        if (!isAnalyzing) {
            isAnalyzing = true
            
            // Update UI
            startProductScanningButton.visibility = View.GONE
            stopProductScanningButton.visibility = View.VISIBLE
            productResultTextView.text = ""
            
            // Start camera and analysis
            startCamera()
            
            // Provide haptic feedback
            vibrate()
        }
    }
    
    private fun stopProductScanning() {
        if (isAnalyzing) {
            isAnalyzing = false
            
            // Update UI
            startProductScanningButton.visibility = View.VISIBLE
            stopProductScanningButton.visibility = View.GONE
            
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
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer())
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
    
    private inner class BarcodeAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTimestamp = 0L
        private val ANALYSIS_INTERVAL = 2000L // 2 seconds between analyses
        private val scanner = BarcodeScanning.getClient()
        private var lastScannedBarcode: String? = null
        
        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()
            
            if (currentTimestamp - lastAnalysisTimestamp >= ANALYSIS_INTERVAL) {
                val mediaImage = imageProxy.image
                if (mediaImage != null && isAnalyzing) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    
                    // Process the image with barcode scanner
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            if (barcodes.isEmpty()) {
                                // Don't update UI if no barcode is found to avoid flickering
                            } else {
                                // Get the first barcode
                                val barcode = barcodes[0]
                                
                                // Only process if this is a new barcode
                                if (barcode.rawValue != lastScannedBarcode) {
                                    lastScannedBarcode = barcode.rawValue
                                    
                                    when (barcode.valueType) {
                                        Barcode.TYPE_PRODUCT -> processProductBarcode(barcode.rawValue)
                                        Barcode.TYPE_URL -> processUrlBarcode(barcode.url?.url)
                                        Barcode.TYPE_TEXT -> processTextBarcode(barcode.rawValue)
                                        else -> processGenericBarcode(barcode.rawValue)
                                    }
                                }
                            }
                            
                            lastAnalysisTimestamp = currentTimestamp
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this@ProductScannerActivity, "Barcode scanning failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
        
        private fun processProductBarcode(rawValue: String?) {
            if (rawValue == null) {
                productResultTextView.text = getString(R.string.no_barcode_detected)
                return
            }
            
            // Display the barcode value
            val barcodeText = getString(R.string.barcode_detected, rawValue)
            productResultTextView.text = barcodeText
            speak(barcodeText, TextToSpeech.QUEUE_FLUSH)
            
            // Look up product information
            val productInfo = productDatabase[rawValue]
            if (productInfo != null) {
                // Display product information
                val productText = getString(
                    R.string.product_information,
                    productInfo.name,
                    productInfo.price,
                    productInfo.description
                )
                productResultTextView.text = "$barcodeText\n\n$productText"
                speak(productText, TextToSpeech.QUEUE_ADD)
            }
        }
        
        private fun processUrlBarcode(url: String?) {
            if (url == null) {
                productResultTextView.text = getString(R.string.no_barcode_detected)
                return
            }
            
            // Display the URL
            val urlText = "URL detected: $url"
            productResultTextView.text = urlText
            speak(urlText, TextToSpeech.QUEUE_FLUSH)
        }
        
        private fun processTextBarcode(text: String?) {
            if (text == null) {
                productResultTextView.text = getString(R.string.no_barcode_detected)
                return
            }
            
            // Display the text
            val textMessage = "Text detected: $text"
            productResultTextView.text = textMessage
            speak(textMessage, TextToSpeech.QUEUE_FLUSH)
        }
        
        private fun processGenericBarcode(rawValue: String?) {
            if (rawValue == null) {
                productResultTextView.text = getString(R.string.no_barcode_detected)
                return
            }
            
            // Try to look up in product database first
            val productInfo = productDatabase[rawValue]
            if (productInfo != null) {
                // Display product information
                val productText = getString(
                    R.string.product_information,
                    productInfo.name,
                    productInfo.price,
                    productInfo.description
                )
                productResultTextView.text = productText
                speak(productText, TextToSpeech.QUEUE_FLUSH)
            } else {
                // Just display the barcode value
                val barcodeText = getString(R.string.barcode_detected, rawValue)
                productResultTextView.text = barcodeText
                speak(barcodeText, TextToSpeech.QUEUE_FLUSH)
            }
        }
    }
    
    // Simple data class for product information
    data class ProductInfo(val name: String, val price: String, val description: String)
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startProductScanning()
            } else {
                Toast.makeText(this, "Camera permission is required for product scanning.", Toast.LENGTH_LONG).show()
                speak("Camera permission is required for product scanning.")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
