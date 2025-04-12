package com.wayfinder.app

import android.Manifest
import android.content.Intent
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
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Activity that allows controlling the app with hand gestures.
 * Uses ML Kit's pose detection to recognize gestures.
 */
class GestureControlActivity : BaseAccessibilityActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var gestureInstructionsTextView: TextView
    private lateinit var gestureResultTextView: TextView
    private lateinit var toggleGestureDetectionButton: Button
    private lateinit var backButton: Button
    
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isDetecting = false
    
    private val CAMERA_PERMISSION_CODE = 100
    
    // Gesture detection thresholds
    private val GESTURE_COOLDOWN = 2000L // 2 seconds between gesture detections
    private var lastGestureTime = 0L
    private var lastDetectedGesture: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gesture_control)
        
        // Initialize views
        previewView = findViewById(R.id.previewView)
        gestureInstructionsTextView = findViewById(R.id.gestureInstructionsTextView)
        gestureResultTextView = findViewById(R.id.gestureResultTextView)
        toggleGestureDetectionButton = findViewById(R.id.toggleGestureDetectionButton)
        backButton = findViewById(R.id.backButton)
        
        // Set up button click listeners
        toggleGestureDetectionButton.setOnClickListener {
            if (isDetecting) {
                stopGestureDetection()
            } else {
                checkPermissionAndStartCamera()
            }
        }
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Set initial button text
        toggleGestureDetectionButton.text = getString(R.string.start_gesture_detection_button)
        toggleGestureDetectionButton.contentDescription = getString(R.string.start_gesture_detection_button)
    }
    
    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.gesture_control_instructions))
    }
    
    private fun checkPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startGestureDetection()
        }
    }
    
    private fun startGestureDetection() {
        if (!isDetecting) {
            isDetecting = true
            
            // Update UI
            toggleGestureDetectionButton.text = getString(R.string.stop_gesture_detection_button)
            toggleGestureDetectionButton.contentDescription = getString(R.string.stop_gesture_detection_button)
            gestureResultTextView.text = getString(R.string.looking_for_gestures)
            speak(getString(R.string.looking_for_gestures))
            
            // Reset the last detected gesture
            lastDetectedGesture = null
            
            // Start camera and analysis
            startCamera()
            
            // Provide haptic feedback
            vibrate()
        }
    }
    
    private fun stopGestureDetection() {
        if (isDetecting) {
            isDetecting = false
            
            // Update UI
            toggleGestureDetectionButton.text = getString(R.string.start_gesture_detection_button)
            toggleGestureDetectionButton.contentDescription = getString(R.string.start_gesture_detection_button)
            gestureResultTextView.text = getString(R.string.gesture_detection_stopped)
            
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
                    it.setAnalyzer(cameraExecutor, GestureAnalyzer())
                }
            
            // Select front camera
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            
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
    
    private inner class GestureAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTimestamp = 0L
        private val ANALYSIS_INTERVAL = 500L // 0.5 seconds between analyses
        
        // Set up the pose detector
        private val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        
        private val poseDetector = PoseDetection.getClient(options)
        
        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()
            
            if (currentTimestamp - lastAnalysisTimestamp >= ANALYSIS_INTERVAL) {
                val mediaImage = imageProxy.image
                if (mediaImage != null && isDetecting) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    
                    // Process the image with pose detector
                    poseDetector.process(image)
                        .addOnSuccessListener { pose ->
                            if (pose.allPoseLandmarks.isNotEmpty()) {
                                detectGestures(pose)
                            }
                            
                            lastAnalysisTimestamp = currentTimestamp
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this@GestureControlActivity, "Pose detection failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
        
        private fun detectGestures(pose: Pose) {
            // Check for cooldown period
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastGestureTime < GESTURE_COOLDOWN) {
                return
            }
            
            // Get relevant landmarks
            val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
            val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
            val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
            val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
            val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
            val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
            
            // Check for wave gesture (hand above shoulder)
            if (leftWrist != null && leftShoulder != null && leftWrist.position.y < leftShoulder.position.y) {
                processGesture("wave_left")
                return
            }
            
            if (rightWrist != null && rightShoulder != null && rightWrist.position.y < rightShoulder.position.y) {
                processGesture("wave_right")
                return
            }
            
            // Check for swipe left gesture (right hand moves left past shoulder)
            if (rightWrist != null && leftShoulder != null && rightWrist.position.x < leftShoulder.position.x) {
                processGesture("swipe_left")
                return
            }
            
            // Check for swipe right gesture (left hand moves right past shoulder)
            if (leftWrist != null && rightShoulder != null && leftWrist.position.x > rightShoulder.position.x) {
                processGesture("swipe_right")
                return
            }
            
            // Check for hands up gesture (both hands above shoulders)
            if (leftWrist != null && rightWrist != null && leftShoulder != null && rightShoulder != null &&
                leftWrist.position.y < leftShoulder.position.y && rightWrist.position.y < rightShoulder.position.y) {
                processGesture("hands_up")
                return
            }
        }
        
        private fun processGesture(gesture: String) {
            // Only process if it's a new gesture or enough time has passed
            if (gesture != lastDetectedGesture || System.currentTimeMillis() - lastGestureTime > GESTURE_COOLDOWN * 2) {
                lastDetectedGesture = gesture
                lastGestureTime = System.currentTimeMillis()
                
                // Get the user-friendly gesture name
                val gestureName = when (gesture) {
                    "wave_left" -> getString(R.string.gesture_wave_left)
                    "wave_right" -> getString(R.string.gesture_wave_right)
                    "swipe_left" -> getString(R.string.gesture_swipe_left)
                    "swipe_right" -> getString(R.string.gesture_swipe_right)
                    "hands_up" -> getString(R.string.gesture_hands_up)
                    else -> gesture
                }
                
                // Update UI and announce the gesture
                val gestureMessage = getString(R.string.gesture_detected, gestureName)
                gestureResultTextView.text = gestureMessage
                speak(gestureMessage)
                vibrate(200)
                
                // Perform action based on gesture
                performGestureAction(gesture)
            }
        }
        
        private fun performGestureAction(gesture: String) {
            when (gesture) {
                "wave_left", "wave_right" -> {
                    // Wave gesture could go back to previous screen
                    speak(getString(R.string.going_back))
                    finish()
                }
                "swipe_left" -> {
                    // Swipe left could launch the next feature
                    speak(getString(R.string.next_feature))
                    // This is just a demonstration - in a real app, you'd launch the next feature
                }
                "swipe_right" -> {
                    // Swipe right could launch the previous feature
                    speak(getString(R.string.previous_feature))
                    // This is just a demonstration - in a real app, you'd launch the previous feature
                }
                "hands_up" -> {
                    // Hands up could go to the home screen
                    speak(getString(R.string.going_home))
                    val intent = Intent(this@GestureControlActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startGestureDetection()
            } else {
                Toast.makeText(this, "Camera permission is required for gesture detection.", Toast.LENGTH_LONG).show()
                speak("Camera permission is required for gesture detection.")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
