package com.wayfinder.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
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
import androidx.palette.graphics.Palette
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Activity that identifies colors of objects.
 * Uses the Palette API to extract dominant colors from camera images.
 */
class ColorRecognitionActivity : BaseAccessibilityActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var colorInstructionsTextView: TextView
    private lateinit var colorResultTextView: TextView
    private lateinit var startColorRecognitionButton: Button
    private lateinit var stopColorRecognitionButton: Button
    private lateinit var backButton: Button
    
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isAnalyzing = false
    
    private val CAMERA_PERMISSION_CODE = 100
    
    // Color name mapping
    private val colorNameMap = mapOf(
        Color.BLACK to "Black",
        Color.DKGRAY to "Dark Gray",
        Color.GRAY to "Gray",
        Color.LTGRAY to "Light Gray",
        Color.WHITE to "White",
        Color.RED to "Red",
        Color.GREEN to "Green",
        Color.BLUE to "Blue",
        Color.YELLOW to "Yellow",
        Color.CYAN to "Cyan",
        Color.MAGENTA to "Magenta"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_color_recognition)
        
        // Initialize views
        previewView = findViewById(R.id.previewView)
        colorInstructionsTextView = findViewById(R.id.colorInstructionsTextView)
        colorResultTextView = findViewById(R.id.colorResultTextView)
        startColorRecognitionButton = findViewById(R.id.startColorRecognitionButton)
        stopColorRecognitionButton = findViewById(R.id.stopColorRecognitionButton)
        backButton = findViewById(R.id.backButton)
        
        // Set up button click listeners
        startColorRecognitionButton.setOnClickListener {
            checkPermissionAndStartCamera()
        }
        
        stopColorRecognitionButton.setOnClickListener {
            stopColorRecognition()
        }
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Initially hide the stop button
        stopColorRecognitionButton.visibility = View.GONE
    }
    
    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.color_recognition_instructions))
    }
    
    private fun checkPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startColorRecognition()
        }
    }
    
    private fun startColorRecognition() {
        if (!isAnalyzing) {
            isAnalyzing = true
            
            // Update UI
            startColorRecognitionButton.visibility = View.GONE
            stopColorRecognitionButton.visibility = View.VISIBLE
            colorResultTextView.text = ""
            
            // Start camera and analysis
            startCamera()
            
            // Provide haptic feedback
            vibrate()
        }
    }
    
    private fun stopColorRecognition() {
        if (isAnalyzing) {
            isAnalyzing = false
            
            // Update UI
            startColorRecognitionButton.visibility = View.VISIBLE
            stopColorRecognitionButton.visibility = View.GONE
            
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
                    it.setAnalyzer(cameraExecutor, ColorAnalyzer())
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
    
    private inner class ColorAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTimestamp = 0L
        private val ANALYSIS_INTERVAL = 1000L // 1 second between analyses
        
        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()
            
            if (currentTimestamp - lastAnalysisTimestamp >= ANALYSIS_INTERVAL) {
                val bitmap = imageProxy.toBitmap()
                if (bitmap != null && isAnalyzing) {
                    // Use Palette API to extract colors
                    Palette.from(bitmap).generate { palette ->
                        if (palette == null) {
                            colorResultTextView.text = getString(R.string.no_colors_detected)
                            speak(getString(R.string.no_colors_detected))
                        } else {
                            // Get dominant colors
                            val dominantSwatch = palette.dominantSwatch
                            val vibrantSwatch = palette.vibrantSwatch
                            val mutedSwatch = palette.mutedSwatch
                            
                            if (dominantSwatch != null) {
                                val colorName = getColorName(dominantSwatch.rgb)
                                val result = getString(R.string.color_detected, colorName)
                                colorResultTextView.text = result
                                speak(result)
                                
                                // Set background color of result text to the detected color
                                colorResultTextView.setBackgroundColor(dominantSwatch.rgb)
                                // Set text color to ensure readability
                                colorResultTextView.setTextColor(if (isDarkColor(dominantSwatch.rgb)) Color.WHITE else Color.BLACK)
                                
                                // If we have multiple distinct colors, announce them
                                if (vibrantSwatch != null && mutedSwatch != null && 
                                    areColorsDifferent(dominantSwatch.rgb, vibrantSwatch.rgb) && 
                                    areColorsDifferent(dominantSwatch.rgb, mutedSwatch.rgb)) {
                                    
                                    val vibrantName = getColorName(vibrantSwatch.rgb)
                                    val mutedName = getColorName(mutedSwatch.rgb)
                                    
                                    if (vibrantName != colorName && mutedName != colorName && vibrantName != mutedName) {
                                        val multiColorResult = getString(
                                            R.string.multiple_colors_detected, 
                                            colorName, 
                                            vibrantName, 
                                            mutedName
                                        )
                                        colorResultTextView.text = multiColorResult
                                        speak(multiColorResult)
                                    }
                                }
                            } else {
                                colorResultTextView.text = getString(R.string.no_colors_detected)
                                speak(getString(R.string.no_colors_detected))
                            }
                        }
                        lastAnalysisTimestamp = currentTimestamp
                    }
                }
                
                imageProxy.close()
            } else {
                imageProxy.close()
            }
        }
        
        private fun ImageProxy.toBitmap(): Bitmap? {
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width
            
            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            
            // Copy image data to bitmap
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            
            // Crop to the original size
            return Bitmap.createBitmap(bitmap, 0, 0, width, height)
        }
        
        private fun getColorName(color: Int): String {
            // Find the closest named color
            var closestColor = Color.BLACK
            var minDistance = Double.MAX_VALUE
            
            for (namedColor in colorNameMap.keys) {
                val distance = colorDistance(color, namedColor)
                if (distance < minDistance) {
                    minDistance = distance
                    closestColor = namedColor
                }
            }
            
            return colorNameMap[closestColor] ?: "Unknown"
        }
        
        private fun colorDistance(color1: Int, color2: Int): Double {
            val r1 = Color.red(color1)
            val g1 = Color.green(color1)
            val b1 = Color.blue(color1)
            
            val r2 = Color.red(color2)
            val g2 = Color.green(color2)
            val b2 = Color.blue(color2)
            
            // Using Euclidean distance in RGB space
            return Math.sqrt(
                Math.pow((r1 - r2).toDouble(), 2.0) +
                Math.pow((g1 - g2).toDouble(), 2.0) +
                Math.pow((b1 - b2).toDouble(), 2.0)
            )
        }
        
        private fun isDarkColor(color: Int): Boolean {
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return darkness >= 0.5
        }
        
        private fun areColorsDifferent(color1: Int, color2: Int): Boolean {
            // Consider colors different if their distance is above a threshold
            return colorDistance(color1, color2) > 100
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startColorRecognition()
            } else {
                Toast.makeText(this, "Camera permission is required for color recognition.", Toast.LENGTH_LONG).show()
                speak("Camera permission is required for color recognition.")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
