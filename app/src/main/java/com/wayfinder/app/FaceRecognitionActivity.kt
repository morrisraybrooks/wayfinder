package com.wayfinder.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Activity that recognizes familiar faces.
 * Allows the user to register faces and then identify them later.
 */
class FaceRecognitionActivity : BaseAccessibilityActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var faceInstructionsTextView: TextView
    private lateinit var faceResultTextView: TextView
    private lateinit var personNameEditText: EditText
    private lateinit var capturedImageView: ImageView
    private lateinit var registerFaceButton: Button
    private lateinit var startRecognitionButton: Button
    private lateinit var stopRecognitionButton: Button
    private lateinit var backButton: Button

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var isRecognizing = false

    private val CAMERA_PERMISSION_CODE = 100
    private val STORAGE_PERMISSION_CODE = 101

    // Face detection options
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.15f)
        .enableTracking()
        .build()

    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    // Simple in-memory database of registered faces
    private val registeredFaces = mutableMapOf<String, FaceData>()

    // Track the last recognized face to avoid repetitive announcements
    private var lastRecognizedPerson: String? = null
    private var lastRecognitionTime = 0L
    private val RECOGNITION_COOLDOWN = 5000L // 5 seconds between same face announcements

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_recognition)

        // Initialize views
        previewView = findViewById(R.id.previewView)
        faceInstructionsTextView = findViewById(R.id.faceInstructionsTextView)
        faceResultTextView = findViewById(R.id.faceResultTextView)
        personNameEditText = findViewById(R.id.personNameEditText)
        capturedImageView = findViewById(R.id.capturedImageView)
        registerFaceButton = findViewById(R.id.registerFaceButton)
        startRecognitionButton = findViewById(R.id.startRecognitionButton)
        stopRecognitionButton = findViewById(R.id.stopRecognitionButton)
        backButton = findViewById(R.id.backButton)

        // Set up button click listeners
        registerFaceButton.setOnClickListener {
            checkPermissionsAndRegisterFace()
        }

        startRecognitionButton.setOnClickListener {
            checkPermissionAndStartRecognition()
        }

        stopRecognitionButton.setOnClickListener {
            stopRecognition()
        }

        backButton.setOnClickListener {
            finish()
        }

        // Initially hide the stop button
        stopRecognitionButton.visibility = View.GONE

        // Load previously registered faces
        loadRegisteredFaces()
    }

    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.face_recognition_instructions))
    }

    private fun checkPermissionsAndRegisterFace() {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val storagePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        when {
            cameraPermission != PackageManager.PERMISSION_GRANTED && storagePermission != PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
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
            storagePermission != PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            }
            else -> {
                registerFace()
            }
        }
    }

    private fun checkPermissionAndStartRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startRecognition()
        }
    }

    private fun registerFace() {
        // Check if a name is entered
        val personName = personNameEditText.text.toString().trim()
        if (personName.isEmpty()) {
            Toast.makeText(this, getString(R.string.enter_person_name), Toast.LENGTH_SHORT).show()
            speak(getString(R.string.enter_person_name))
            return
        }

        // Start camera if not already started
        if (imageCapture == null) {
            startCameraForCapture()
        }

        // Take a picture
        speak(getString(R.string.taking_picture))

        val imageCapture = imageCapture ?: return

        // Create a file to store the image
        val photoFile = File(getExternalFilesDir(null), "$personName.jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Process the saved image to extract face features
                    processSavedImage(photoFile, personName)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@FaceRecognitionActivity, "Error taking picture: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun processSavedImage(photoFile: File, personName: String) {
        try {
            // Load the image
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

            // Display the captured image
            capturedImageView.setImageBitmap(bitmap)
            capturedImageView.visibility = View.VISIBLE

            // Create an InputImage for face detection
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            // Process the image with face detector
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        Toast.makeText(this, getString(R.string.no_face_detected), Toast.LENGTH_SHORT).show()
                        speak(getString(R.string.no_face_detected))
                    } else {
                        // Get the first detected face
                        val face = faces[0]

                        // Create a FaceData object with the face features
                        val faceData = FaceData(
                            personName,
                            photoFile.absolutePath,
                            face.boundingBox,
                            face.trackingId ?: -1
                        )

                        // Add to registered faces
                        registeredFaces[personName] = faceData

                        // Save the registered face data
                        saveRegisteredFaces()

                        // Announce success
                        val successMessage = getString(R.string.face_registered, personName)
                        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
                        speak(successMessage)

                        // Clear the name field
                        personNameEditText.text.clear()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Face detection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecognition() {
        if (!isRecognizing) {
            // Check if we have any registered faces
            if (registeredFaces.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_faces_registered), Toast.LENGTH_SHORT).show()
                speak(getString(R.string.no_faces_registered))
                return
            }

            isRecognizing = true

            // Update UI
            startRecognitionButton.visibility = View.GONE
            stopRecognitionButton.visibility = View.VISIBLE
            registerFaceButton.isEnabled = false
            capturedImageView.visibility = View.GONE
            faceResultTextView.text = getString(R.string.looking_for_faces)
            speak(getString(R.string.looking_for_faces))

            // Reset the last recognized face
            lastRecognizedPerson = null

            // Start camera for continuous face recognition
            startCameraForRecognition()

            // Provide haptic feedback
            vibrate()
        }
    }

    private fun stopRecognition() {
        if (isRecognizing) {
            isRecognizing = false

            // Unbind camera to stop processing
            cameraProvider?.unbindAll()

            // Update UI
            startRecognitionButton.visibility = View.VISIBLE
            stopRecognitionButton.visibility = View.GONE
            registerFaceButton.isEnabled = true
            faceResultTextView.text = getString(R.string.face_recognition_stopped)

            // Provide haptic feedback
            vibrate()
        }
    }

    private fun startCameraForCapture() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Set up the preview use case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Set up the image capture use case
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // Select front camera
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind any bound use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind use cases to camera
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCameraForRecognition() {
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
                    it.setAnalyzer(cameraExecutor, FaceAnalyzer())
                }

            // Select front camera
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

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

    private inner class FaceAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTimestamp = 0L
        private val ANALYSIS_INTERVAL = 1000L // 1 second between analyses

        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()

            if (currentTimestamp - lastAnalysisTimestamp >= ANALYSIS_INTERVAL) {
                val mediaImage = imageProxy.image
                if (mediaImage != null && isRecognizing) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                    // Process the image with face detector
                    faceDetector.process(image)
                        .addOnSuccessListener { faces ->
                            if (faces.isEmpty()) {
                                // Don't update UI if no faces are found to avoid flickering
                            } else {
                                // For each detected face, try to match with registered faces
                                for (face in faces) {
                                    val matchedPerson = findMatchingFace(face)

                                    if (matchedPerson != null) {
                                        // Check if this is a new recognition or enough time has passed
                                        val currentTime = System.currentTimeMillis()
                                        if (matchedPerson != lastRecognizedPerson || currentTime - lastRecognitionTime > RECOGNITION_COOLDOWN) {
                                            lastRecognizedPerson = matchedPerson
                                            lastRecognitionTime = currentTime

                                            // Update UI and announce the recognized person
                                            val recognitionMessage = getString(R.string.face_recognized, matchedPerson)
                                            faceResultTextView.text = recognitionMessage
                                            speak(recognitionMessage)
                                            vibrate(200)
                                        }
                                        break // Stop after finding the first match
                                    }
                                }
                            }

                            lastAnalysisTimestamp = currentTimestamp
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this@FaceRecognitionActivity, "Face detection failed: ${e.message}", Toast.LENGTH_SHORT).show()
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

        private fun findMatchingFace(detectedFace: com.google.mlkit.vision.face.Face): String? {
            // This is a simplified face matching algorithm
            // In a real app, you would use more sophisticated face recognition techniques

            for ((personName, faceData) in registeredFaces) {
                // Compare face bounding boxes (very basic matching)
                val registeredBox = faceData.boundingBox
                val detectedBox = detectedFace.boundingBox

                // Calculate overlap ratio
                val overlapRatio = calculateOverlapRatio(registeredBox, detectedBox)

                // If overlap is significant, consider it a match
                if (overlapRatio > 0.6) {
                    return personName
                }
            }

            return null
        }

        private fun calculateOverlapRatio(box1: android.graphics.Rect, box2: android.graphics.Rect): Float {
            // Create copies to avoid modifying the original rects
            // Note: Rect.intersect() modifies the rect it's called on!
            val box1Copy = android.graphics.Rect(box1)
            val box2Copy = android.graphics.Rect(box2)

            // Calculate intersection
            val intersects = box1Copy.intersect(box2Copy)
            val intersectionArea = if (intersects) {
                box1Copy.width() * box1Copy.height()
            } else {
                0
            }

            // Calculate the union area using original boxes
            val box1Area = box1.width() * box1.height()
            val box2Area = box2.width() * box2.height()
            val unionArea = box1Area + box2Area - intersectionArea

            // Return the ratio
            return if (unionArea > 0) intersectionArea.toFloat() / unionArea else 0f
        }
    }

    private fun saveRegisteredFaces() {
        try {
            // Convert registered faces to a simple format for storage
            val facesData = registeredFaces.map { (name, faceData) ->
                val box = faceData.boundingBox
                "$name|${faceData.imagePath}|${box.left},${box.top},${box.right},${box.bottom}|${faceData.trackingId}"
            }.joinToString("\n")

            // Save to a file
            val file = File(getExternalFilesDir(null), "registered_faces.txt")
            FileOutputStream(file).use { output ->
                output.write(facesData.toByteArray())
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving face data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadRegisteredFaces() {
        try {
            val file = File(getExternalFilesDir(null), "registered_faces.txt")
            if (file.exists()) {
                file.readText().split("\n").forEach { line ->
                    if (line.isNotEmpty()) {
                        val parts = line.split("|")
                        if (parts.size >= 4) {
                            val name = parts[0]
                            val imagePath = parts[1]
                            // Parse the bounding box coordinates
                            val boxCoords = parts[2].split(",").map { it.toInt() }
                            val boundingBox = android.graphics.Rect(boxCoords[0], boxCoords[1], boxCoords[2], boxCoords[3])
                            val trackingId = parts[3].toIntOrNull() ?: -1

                            registeredFaces[name] = FaceData(name, imagePath, boundingBox, trackingId)
                        }
                    }
                }

                // Update UI to show the number of registered faces
                val faceCount = registeredFaces.size
                if (faceCount > 0) {
                    val message = resources.getQuantityString(
                        R.plurals.registered_faces_count,
                        faceCount,
                        faceCount
                    )
                    faceResultTextView.text = message
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading face data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                        grantResults.size > 1 &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                        registerFace()
                    } else {
                        startRecognition()
                    }
                } else {
                    Toast.makeText(this, "Camera permission is required for face recognition.", Toast.LENGTH_LONG).show()
                    speak("Camera permission is required for face recognition.")
                }
            }
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    registerFace()
                } else {
                    Toast.makeText(this, "Storage permission is required to save face data.", Toast.LENGTH_LONG).show()
                    speak("Storage permission is required to save face data.")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    // Data class for storing face information
    data class FaceData(
        val name: String,
        val imagePath: String,
        val boundingBox: android.graphics.Rect,
        val trackingId: Int
    )
}
