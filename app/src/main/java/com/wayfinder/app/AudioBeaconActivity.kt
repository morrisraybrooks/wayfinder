package com.wayfinder.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Activity that provides audio beacon functionality.
 * Allows the user to set audio markers at specific locations and find them later.
 */
class AudioBeaconActivity : BaseAccessibilityActivity(), LocationListener {

    private lateinit var beaconInstructionsTextView: TextView
    private lateinit var beaconStatusTextView: TextView
    private lateinit var setBeaconButton: Button
    private lateinit var activateBeaconButton: Button
    private lateinit var deactivateBeaconButton: Button
    private lateinit var backButton: Button

    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private var beaconLocation: Location? = null
    private var isBeaconActive = false

    private val LOCATION_PERMISSION_CODE = 101
    private val MIN_DISTANCE_CHANGE_FOR_UPDATES = 1.0f // 1 meter
    private val MIN_TIME_BETWEEN_UPDATES = 1000L // 1 second

    // Sound-related variables
    private lateinit var soundPool: SoundPool
    private var beaconSoundId = 0
    private var beaconSoundStreamId = 0
    private var beaconExecutor: ScheduledExecutorService? = null

    // Beacon settings
    private val BEACON_SOUND_INTERVAL_MIN = 500L // 0.5 seconds at minimum distance
    private val BEACON_SOUND_INTERVAL_MAX = 3000L // 3 seconds at maximum distance
    private val BEACON_DISTANCE_MAX = 50.0f // 50 meters maximum tracking distance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_beacon)

        // Initialize location services
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Initialize views
        beaconInstructionsTextView = findViewById(R.id.beaconInstructionsTextView)
        beaconStatusTextView = findViewById(R.id.beaconStatusTextView)
        setBeaconButton = findViewById(R.id.setBeaconButton)
        activateBeaconButton = findViewById(R.id.activateBeaconButton)
        deactivateBeaconButton = findViewById(R.id.deactivateBeaconButton)
        backButton = findViewById(R.id.backButton)

        // Initialize sound pool
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load the beacon sound
        try {
            beaconSoundId = soundPool.load(this, R.raw.beacon_sound, 1)
        } catch (e: Exception) {
            // If resource loading fails, we'll use TTS as a fallback
            Toast.makeText(this, "Using TTS for beacon sound", Toast.LENGTH_SHORT).show()
        }

        // Set up button click listeners
        setBeaconButton.setOnClickListener {
            setBeacon()
        }

        activateBeaconButton.setOnClickListener {
            activateBeacon()
        }

        deactivateBeaconButton.setOnClickListener {
            stopBeacon()
        }

        backButton.setOnClickListener {
            finish()
        }

        // Update UI based on initial state
        updateButtonStates()

        // Request location permission immediately
        checkLocationPermission()
    }

    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.audio_beacon_instructions))
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
                updateBeaconStatus()
            }

            beaconStatusTextView.text = getString(R.string.location_services_active)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location permission is required for audio beacon.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting location updates: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setBeacon() {
        val current = currentLocation
        if (current != null) {
            beaconLocation = Location(current)
            beaconStatusTextView.text = getString(R.string.beacon_set)
            speak(getString(R.string.beacon_set))
            vibrate(300)

            // Update UI
            updateButtonStates()
        } else {
            Toast.makeText(this, getString(R.string.waiting_for_location), Toast.LENGTH_SHORT).show()
            speak(getString(R.string.waiting_for_location))
        }
    }

    private fun activateBeacon() {
        if (beaconLocation != null) {
            isBeaconActive = true

            // Update UI
            updateButtonStates()
            beaconStatusTextView.text = getString(R.string.beacon_activated)
            speak(getString(R.string.beacon_activated))

            // Start the beacon sound
            startBeaconSound()
        } else {
            Toast.makeText(this, getString(R.string.no_beacon_set), Toast.LENGTH_SHORT).show()
            speak(getString(R.string.no_beacon_set))
        }
    }

    private fun stopBeacon() {
        isBeaconActive = false

        // Stop the beacon sound
        stopBeaconSound()

        // Update UI
        updateButtonStates()
        beaconStatusTextView.text = getString(R.string.beacon_deactivated)
        speak(getString(R.string.beacon_deactivated))
    }

    private fun startBeaconSound() {
        // Stop any existing beacon sound
        stopBeaconSound()

        // Create a new executor for the beacon sound
        beaconExecutor = Executors.newSingleThreadScheduledExecutor()

        // Schedule the beacon sound to play at intervals based on distance
        beaconExecutor?.scheduleAtFixedRate({
            runOnUiThread {
                playBeaconSound()
            }
        }, 0, calculateBeaconInterval(), TimeUnit.MILLISECONDS)
    }

    private fun stopBeaconSound() {
        // Stop the scheduled executor
        beaconExecutor?.shutdownNow()
        beaconExecutor = null

        // Stop the sound
        soundPool.stop(beaconSoundStreamId)
    }

    private fun playBeaconSound() {
        try {
            if (beaconSoundId > 0) {
                // Try to play the sound with the SoundPool
                val volume = calculateBeaconVolume()
                beaconSoundStreamId = soundPool.play(beaconSoundId, volume, volume, 1, 0, 1.0f)
                
                if (beaconSoundStreamId == 0) {
                    // If sound play failed, fallback to TTS
                    speak("beep", TextToSpeech.QUEUE_FLUSH)
                }
            } else {
                // If sound resource wasn't loaded, use TTS
                speak("beep", TextToSpeech.QUEUE_FLUSH)
            }
        } catch (e: Exception) {
            // In case of any exception, use TTS as fallback
            speak("beep", TextToSpeech.QUEUE_FLUSH)
        }

        // Also vibrate with intensity based on distance
        val volume = calculateBeaconVolume()
        val vibrationDuration = if (volume > 0.01f) {
            (50 + (200 / volume).toInt()).coerceIn(50, 300)
        } else {
            300 // Maximum duration if volume is very low or zero
        }
        vibrate(vibrationDuration.toLong())
    }

    private fun calculateBeaconInterval(): Long {
        if (currentLocation == null || beaconLocation == null) {
            return BEACON_SOUND_INTERVAL_MAX
        }
        // Use local non-null references to avoid repeated '!!' and improve clarity
        val current = currentLocation!!
        val beacon = beaconLocation!!
        val distance = current.distanceTo(beacon)
        val normalizedDistance = (distance / BEACON_DISTANCE_MAX).coerceIn(0.0f, 1.0f)

        // Map the normalized distance to the interval range
        // Closer = faster beeping
        return (BEACON_SOUND_INTERVAL_MIN + normalizedDistance * (BEACON_SOUND_INTERVAL_MAX - BEACON_SOUND_INTERVAL_MIN)).toLong()
    }

    private fun calculateBeaconVolume(): Float {
        if (currentLocation == null || beaconLocation == null) {
            return 0.5f
        }
        // Use local non-null references to avoid repeated '!!' and improve clarity
        val current = currentLocation!!
        val beacon = beaconLocation!!
        val distance = current.distanceTo(beacon)
        val normalizedDistance = (distance / BEACON_DISTANCE_MAX).coerceIn(0.0f, 1.0f)

        // Map the normalized distance to the volume range
        // Closer = louder
        return 1.0f - normalizedDistance * 0.7f
    }

    private fun updateBeaconStatus() {
        val current = currentLocation ?: return
        val beacon = beaconLocation ?: return

        val distance = current.distanceTo(beacon)
        val direction = calculateDirection(current, beacon)

        val statusText = getString(R.string.beacon_distance_direction, distance.toInt(), direction)
        beaconStatusTextView.text = statusText

        // If the beacon is active and we're very close, announce it
        if (isBeaconActive && distance < 3.0f) {
            speak(getString(R.string.beacon_reached))
            vibrate(500)

            // Stop the beacon sound when reached
            stopBeacon()
        }
    }

    private fun calculateDirection(currentLoc: Location, targetLoc: Location): String {
        val bearing = currentLoc.bearingTo(targetLoc)
        val normalizedBearing = (bearing + 360) % 360

        return when {
            normalizedBearing >= 337.5 || normalizedBearing < 22.5 -> getString(R.string.direction_north)
            normalizedBearing >= 22.5 && normalizedBearing < 67.5 -> getString(R.string.direction_northeast)
            normalizedBearing >= 67.5 && normalizedBearing < 112.5 -> getString(R.string.direction_east)
            normalizedBearing >= 112.5 && normalizedBearing < 157.5 -> getString(R.string.direction_southeast)
            normalizedBearing >= 157.5 && normalizedBearing < 202.5 -> getString(R.string.direction_south)
            normalizedBearing >= 202.5 && normalizedBearing < 247.5 -> getString(R.string.direction_southwest)
            normalizedBearing >= 247.5 && normalizedBearing < 292.5 -> getString(R.string.direction_west)
            else -> getString(R.string.direction_northwest)
        }
    }

    private fun updateButtonStates() {
        if (beaconLocation == null) {
            // No beacon set
            setBeaconButton.isEnabled = true
            activateBeaconButton.isEnabled = false
            deactivateBeaconButton.isEnabled = false
        } else if (isBeaconActive) {
            // Beacon is active
            setBeaconButton.isEnabled = false
            activateBeaconButton.isEnabled = false
            deactivateBeaconButton.isEnabled = true
        } else {
            // Beacon is set but not active
            setBeaconButton.isEnabled = true
            activateBeaconButton.isEnabled = true
            deactivateBeaconButton.isEnabled = false
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = location
        updateBeaconStatus()

        // If the beacon is active, update the sound interval
        if (isBeaconActive) {
            // Restart the beacon sound with the new interval
            startBeaconSound()
        }
    }
    
    // Required implementations for LocationListener interface
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER || provider == LocationManager.NETWORK_PROVIDER) {
            Toast.makeText(this, "Location services disabled. Please enable for beacon functionality.", Toast.LENGTH_SHORT).show()
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
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Location permission is required for audio beacon.", Toast.LENGTH_LONG).show()
                speak("Location permission is required for audio beacon.")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Restart location updates if needed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }

        // Restart the beacon sound if active
        if (isBeaconActive) {
            startBeaconSound()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop location updates to save battery
        locationManager.removeUpdates(this)

        // Stop the beacon sound
        stopBeaconSound()
    }

    override fun onDestroy() {
        try {
            // Stop location updates
            locationManager.removeUpdates(this)
            
            // Stop beacon sound and executor
            stopBeaconSound()
            
            // Release sound pool
            soundPool.release()
        } catch (e: Exception) {
            // Silently handle any exceptions during cleanup
        }
        
        super.onDestroy()
    }
}
