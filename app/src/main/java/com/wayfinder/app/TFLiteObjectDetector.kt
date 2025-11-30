package com.wayfinder.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TensorFlow Lite Object Detector using SSD MobileNet V1 model.
 * Detects 80+ common objects with bounding boxes.
 */
class TFLiteObjectDetector(private val context: Context) {

    companion object {
        private const val TAG = "TFLiteDetector"
        private const val MODEL_FILE = "ssd_mobilenet.tflite"
        private const val LABELS_FILE = "coco_labels.txt"
        private const val INPUT_SIZE = 300
        private const val NUM_DETECTIONS = 10
        private const val CONFIDENCE_THRESHOLD = 0.5f
    }

    data class Detection(
        val label: String,
        val confidence: Float,
        val boundingBox: RectF,
        val classId: Int
    )

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var isInitialized = false

    // Output arrays for the model
    private lateinit var outputLocations: Array<Array<FloatArray>>
    private lateinit var outputClasses: Array<FloatArray>
    private lateinit var outputScores: Array<FloatArray>
    private lateinit var numDetections: FloatArray

    fun initialize(): Boolean {
        return try {
            // Load labels
            labels = FileUtil.loadLabels(context, LABELS_FILE)
            Log.d(TAG, "Loaded ${labels.size} labels")

            // Load model
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)

            // Use CPU only - GPU delegate has compatibility issues on some devices
            val options = Interpreter.Options()
            options.setNumThreads(4)
            Log.d(TAG, "Using CPU with 4 threads")

            interpreter = Interpreter(modelBuffer, options)

            // Initialize output arrays
            outputLocations = Array(1) { Array(NUM_DETECTIONS) { FloatArray(4) } }
            outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
            outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
            numDetections = FloatArray(1)

            isInitialized = true
            Log.d(TAG, "TFLite detector initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TFLite detector", e)
            false
        }
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        if (!isInitialized) {
            Log.w(TAG, "Detector not initialized")
            return emptyList()
        }

        return try {
            // Resize bitmap to model input size
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            
            // Convert to ByteBuffer
            val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)
            
            // Prepare output map
            val outputMap = HashMap<Int, Any>()
            outputMap[0] = outputLocations
            outputMap[1] = outputClasses
            outputMap[2] = outputScores
            outputMap[3] = numDetections

            // Run inference
            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

            // Process results
            val detections = mutableListOf<Detection>()
            val numDetected = numDetections[0].toInt().coerceAtMost(NUM_DETECTIONS)
            
            for (i in 0 until numDetected) {
                val confidence = outputScores[0][i]
                if (confidence >= CONFIDENCE_THRESHOLD) {
                    val classId = outputClasses[0][i].toInt()
                    val label = if (classId < labels.size) labels[classId] else "Unknown"
                    
                    // Bounding box: [top, left, bottom, right] normalized 0-1
                    val top = outputLocations[0][i][0]
                    val left = outputLocations[0][i][1]
                    val bottom = outputLocations[0][i][2]
                    val right = outputLocations[0][i][3]
                    
                    // Scale to original image dimensions
                    val boundingBox = RectF(
                        left * bitmap.width,
                        top * bitmap.height,
                        right * bitmap.width,
                        bottom * bitmap.height
                    )

                    detections.add(Detection(label, confidence, boundingBox, classId))
                    Log.d(TAG, "Detected: $label (${(confidence * 100).toInt()}%) at $boundingBox")
                }
            }

            Log.d(TAG, "Total detections: ${detections.size}")
            detections
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
            emptyList()
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // SSD MobileNet V1 expects uint8 input (1 byte per channel, not float)
        val byteBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            // Extract RGB values as bytes (0-255)
            byteBuffer.put(((pixel shr 16) and 0xFF).toByte()) // R
            byteBuffer.put(((pixel shr 8) and 0xFF).toByte())  // G
            byteBuffer.put((pixel and 0xFF).toByte())          // B
        }

        byteBuffer.rewind()
        return byteBuffer
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
        Log.d(TAG, "TFLite detector closed")
    }

    fun isReady(): Boolean = isInitialized

    // Priority objects for accessibility (safety-critical)
    fun isPriorityObject(label: String): Boolean {
        val priorityLabels = setOf(
            "person", "car", "bicycle", "motorcycle", "bus", "truck",
            "traffic light", "fire hydrant", "stop sign", "dog", "cat",
            "chair", "couch", "bed", "dining table", "toilet", "tv",
            "laptop", "cell phone", "door", "stairs", "bench"
        )
        return priorityLabels.contains(label.lowercase())
    }
}

