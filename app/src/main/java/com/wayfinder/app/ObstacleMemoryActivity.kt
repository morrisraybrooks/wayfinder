package com.wayfinder.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Activity that remembers obstacles in frequently visited places.
 * Allows the user to mark obstacles and get warnings when approaching them later.
 */
class ObstacleMemoryActivity : BaseAccessibilityActivity(), LocationListener {

    private lateinit var obstacleInstructionsTextView: TextView
    private lateinit var obstacleStatusTextView: TextView
    private lateinit var obstacleNameEditText: EditText
    private lateinit var obstacleDescriptionEditText: EditText
    private lateinit var addObstacleButton: Button
    private lateinit var listObstaclesButton: Button
    private lateinit var clearObstaclesButton: Button
    private lateinit var backButton: Button

    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null

    private val LOCATION_PERMISSION_CODE = 101
    private val STORAGE_PERMISSION_CODE = 102
    private val MIN_DISTANCE_CHANGE_FOR_UPDATES = 1.0f // 1 meter
    private val MIN_TIME_BETWEEN_UPDATES = 1000L // 1 second

    // Obstacle warning settings
    private val OBSTACLE_WARNING_DISTANCE = 10.0f // 10 meters
    private var lastObstacleWarningTime = 0L
    private val OBSTACLE_WARNING_COOLDOWN = 10000L // 10 seconds between warnings

    // List of remembered obstacles
    private val obstacles = mutableListOf<Obstacle>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_obstacle_memory)

        // Initialize location services
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Initialize views
        obstacleInstructionsTextView = findViewById(R.id.obstacleInstructionsTextView)
        obstacleStatusTextView = findViewById(R.id.obstacleStatusTextView)
        obstacleNameEditText = findViewById(R.id.obstacleNameEditText)
        obstacleDescriptionEditText = findViewById(R.id.obstacleDescriptionEditText)
        addObstacleButton = findViewById(R.id.addObstacleButton)
        listObstaclesButton = findViewById(R.id.listObstaclesButton)
        clearObstaclesButton = findViewById(R.id.clearObstaclesButton)
        backButton = findViewById(R.id.backButton)

        // Set up button click listeners
        addObstacleButton.setOnClickListener {
            addObstacle()
        }

        listObstaclesButton.setOnClickListener {
            listObstacles()
        }

        clearObstaclesButton.setOnClickListener {
            clearObstacles()
        }

        backButton.setOnClickListener {
            finish()
        }

        // Load saved obstacles
        loadObstacles()

        // Request location permission immediately
        checkLocationPermission()
    }

    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.obstacle_memory_instructions))
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_CHANGE_FOR_UPDATES,
                this
            )

            // Also request updates from network provider as a fallback
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_CHANGE_FOR_UPDATES,
                this
            )

            // Get last known location to initialize
            val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (lastKnownLocation != null) {
                currentLocation = lastKnownLocation
                checkForNearbyObstacles()
            }

            obstacleStatusTextView.text = getString(R.string.location_services_active)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location permission is required for obstacle memory.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting location updates: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addObstacle() {
        val obstacleName = obstacleNameEditText.text.toString().trim()
        val obstacleDescription = obstacleDescriptionEditText.text.toString().trim()

        if (obstacleName.isEmpty()) {
            Toast.makeText(this, getString(R.string.enter_obstacle_name), Toast.LENGTH_SHORT).show()
            speak(getString(R.string.enter_obstacle_name))
            return
        }

        val location = currentLocation
        if (location != null) {
            // Create a new obstacle
            val obstacle = Obstacle(
                obstacleName,
                obstacleDescription,
                location.latitude,
                location.longitude
            )

            // Add to the list
            obstacles.add(obstacle)

            // Save the updated list
            saveObstacles()

            // Update UI and announce success
            val successMessage = getString(R.string.obstacle_added, obstacleName)
            obstacleStatusTextView.text = successMessage
            speak(successMessage)
            vibrate(300)

            // Clear the input fields
            obstacleNameEditText.text.clear()
            obstacleDescriptionEditText.text.clear()
        } else {
            Toast.makeText(this, getString(R.string.waiting_for_obstacle_location), Toast.LENGTH_SHORT).show()
            speak(getString(R.string.waiting_for_obstacle_location))
        }
    }

    private fun listObstacles() {
        if (obstacles.isEmpty()) {
            val message = getString(R.string.no_obstacles_saved)
            obstacleStatusTextView.text = message
            speak(message)
            return
        }

        // Build a list of obstacles with their distances
        val obstacleList = StringBuilder()
        val location = currentLocation

        obstacles.forEachIndexed { index, obstacle ->
            obstacleList.append("${index + 1}. ${obstacle.name}")

            if (obstacle.description.isNotEmpty()) {
                obstacleList.append(": ${obstacle.description}")
            }

            if (location != null) {
                val obstacleLocation = Location("").apply {
                    latitude = obstacle.latitude
                    longitude = obstacle.longitude
                }
                val distance = location.distanceTo(obstacleLocation).toInt()
                obstacleList.append(" (${distance} meters away)")
            }

            obstacleList.append("\n")
        }

        // Update UI
        obstacleStatusTextView.text = obstacleList.toString()

        // Announce the number of obstacles
        val message = resources.getQuantityString(
            R.plurals.obstacles_count,
            obstacles.size,
            obstacles.size
        )
        speak(message)

        // Then read the list
        speak(getString(R.string.obstacles_list_follows), TextToSpeech.QUEUE_ADD)
        obstacles.forEachIndexed { index, obstacle ->
            val obstacleInfo = "${index + 1}. ${obstacle.name}"
            speak(obstacleInfo, TextToSpeech.QUEUE_ADD)
        }
    }

    private fun clearObstacles() {
        if (obstacles.isEmpty()) {
            val message = getString(R.string.no_obstacles_to_clear)
            obstacleStatusTextView.text = message
            speak(message)
            return
        }

        // Clear the list
        obstacles.clear()

        // Save the empty list
        saveObstacles()

        // Update UI and announce success
        val message = getString(R.string.obstacles_cleared)
        obstacleStatusTextView.text = message
        speak(message)
        vibrate(300)
    }

    private fun checkForNearbyObstacles() {
        val location = currentLocation ?: return
        val currentTime = System.currentTimeMillis()

        // Check if enough time has passed since the last warning
        if (currentTime - lastObstacleWarningTime < OBSTACLE_WARNING_COOLDOWN) {
            return
        }

        // Find the closest obstacle within warning distance
        var closestObstacle: Obstacle? = null
        var closestDistance = Float.MAX_VALUE

        for (obstacle in obstacles) {
            val obstacleLocation = Location("").apply {
                latitude = obstacle.latitude
                longitude = obstacle.longitude
            }

            val distance = location.distanceTo(obstacleLocation)

            if (distance < OBSTACLE_WARNING_DISTANCE && distance < closestDistance) {
                closestObstacle = obstacle
                closestDistance = distance
            }
        }

        // If a nearby obstacle was found, warn the user
        if (closestObstacle != null) {
            val warningMessage = getString(
                R.string.obstacle_proximity_warning,
                closestObstacle.name,
                closestDistance.toInt()
            )

            obstacleStatusTextView.text = warningMessage
            speak(warningMessage)
            vibrate(500)

            lastObstacleWarningTime = currentTime
        }
    }

    private fun saveObstacles() {
        // Check storage permissions for devices running Android M (6.0) and higher
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
                return
            }
        }
        
        try {
            // Convert obstacles to JSON
            val gson = Gson()
            val json = gson.toJson(obstacles)

            // Use internal storage instead of external for better reliability
            val file = File(filesDir, "obstacles.json")
            FileOutputStream(file).use { output ->
                output.write(json.toByteArray())
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Error saving obstacles: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Unexpected error while saving: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadObstacles() {
        try {
            // First check internal storage for newer files
            var file = File(filesDir, "obstacles.json")
            
            // If not found, try fallback to external storage for backward compatibility
            if (!file.exists()) {
                file = File(getExternalFilesDir(null), "obstacles.json")
            }
            
            if (file.exists()) {
                val json = file.readText()
                val gson = Gson()
                try {
                    val type = object : TypeToken<List<Obstacle>>() {}.type
                    val loadedObstacles: List<Obstacle> = gson.fromJson(json, type)
    
                    obstacles.clear()
                    obstacles.addAll(loadedObstacles)
    
                    // Update UI to show the number of loaded obstacles
                    val obstacleCount = obstacles.size
                    if (obstacleCount > 0) {
                        val message = resources.getQuantityString(
                            R.plurals.obstacles_loaded,
                            obstacleCount,
                            obstacleCount
                        )
                        obstacleStatusTextView.text = message
                    }
                } catch (e: com.google.gson.JsonSyntaxException) {
                    Toast.makeText(this, "Error parsing obstacles data. Creating new file.", Toast.LENGTH_SHORT).show()
                    // If the file is corrupted, create a new empty one
                    saveObstacles()
                }
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Error reading obstacles file: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Unexpected error loading obstacles: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = location
        checkForNearbyObstacles()
    }
    
    // Required implementations for LocationListener interface
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER || provider == LocationManager.NETWORK_PROVIDER) {
            Toast.makeText(this, "Location services disabled. Please enable for obstacle detection.", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER || provider == LocationManager.NETWORK_PROVIDER) {
            Toast.makeText(this, "Location services enabled.", Toast.LENGTH_SHORT).show()
        }
    }
    
    @Deprecated("This method is deprecated in API level 29", ReplaceWith(""))
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        // Not used but required for older Android versions
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates()
                } else {
                    Toast.makeText(this, "Location permission is required for obstacle memory.", Toast.LENGTH_LONG).show()
                    speak("Location permission is required for obstacle memory.")
                }
            }
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Try saving obstacles again
                    saveObstacles()
                } else {
                    Toast.makeText(this, "Storage permission is required to save obstacles.", Toast.LENGTH_LONG).show()
                    speak("Storage permission is required to save obstacles.")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Restart location updates if needed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop location updates to save battery
        locationManager.removeUpdates(this)
    }

    // Data class for storing obstacle information
    data class Obstacle(
        val name: String,
        val description: String,
        val latitude: Double,
        val longitude: Double
    )
}
