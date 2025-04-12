package com.wayfinder.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * Activity that provides emergency SOS functionality.
 * Allows the user to quickly contact emergency services or designated emergency contacts.
 */
class EmergencySOSActivity : BaseAccessibilityActivity() {

    private lateinit var emergencyInstructionsTextView: TextView
    private lateinit var emergencyContactTextView: TextView
    private lateinit var callEmergencyServicesButton: Button
    private lateinit var callEmergencyContactButton: Button
    private lateinit var sendLocationButton: Button
    private lateinit var configureContactsButton: Button
    private lateinit var backButton: Button
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    
    private val LOCATION_PERMISSION_CODE = 101
    private val CALL_PERMISSION_CODE = 102
    private val SMS_PERMISSION_CODE = 103
    
    // Emergency contact information (in a real app, this would be stored in a database or preferences)
    private val emergencyContact = EmergencyContact("Family Member", "555-123-4567")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_sos)
        
        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Initialize views
        emergencyInstructionsTextView = findViewById(R.id.emergencyInstructionsTextView)
        emergencyContactTextView = findViewById(R.id.emergencyContactTextView)
        callEmergencyServicesButton = findViewById(R.id.callEmergencyServicesButton)
        callEmergencyContactButton = findViewById(R.id.callEmergencyContactButton)
        sendLocationButton = findViewById(R.id.sendLocationButton)
        configureContactsButton = findViewById(R.id.configureContactsButton)
        backButton = findViewById(R.id.backButton)
        
        // Display emergency contact information
        updateEmergencyContactDisplay()
        
        // Set up button click listeners
        callEmergencyServicesButton.setOnClickListener {
            checkCallPermissionAndCallEmergencyServices()
        }
        
        callEmergencyContactButton.setOnClickListener {
            checkCallPermissionAndCallEmergencyContact()
        }
        
        sendLocationButton.setOnClickListener {
            checkLocationAndSmsPermissionsAndSendLocation()
        }
        
        configureContactsButton.setOnClickListener {
            // In a real app, this would open a configuration screen
            Toast.makeText(this, "This would open a screen to configure emergency contacts", Toast.LENGTH_SHORT).show()
            speak("This would open a screen to configure emergency contacts")
        }
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Request location permission immediately to have location ready in case of emergency
        checkLocationPermission()
    }
    
    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.emergency_sos_instructions))
    }
    
    private fun updateEmergencyContactDisplay() {
        val contactText = getString(R.string.emergency_contact_name, emergencyContact.name) + "\n" +
                getString(R.string.emergency_contact_number, emergencyContact.phoneNumber)
        emergencyContactTextView.text = contactText
    }
    
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
        } else {
            getCurrentLocation()
        }
    }
    
    private fun checkCallPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                CALL_PERMISSION_CODE
            )
            return false
        }
        return true
    }
    
    private fun checkSmsPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                SMS_PERMISSION_CODE
            )
            return false
        }
        return true
    }
    
    private fun getCurrentLocation() {
        try {
            val cancellationTokenSource = CancellationTokenSource()
            
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            currentLocation = location
                        }
                    }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error getting location: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkCallPermissionAndCallEmergencyServices() {
        if (checkCallPermission()) {
            callEmergencyServices()
        }
    }
    
    private fun checkCallPermissionAndCallEmergencyContact() {
        if (checkCallPermission()) {
            callEmergencyContact()
        }
    }
    
    private fun checkLocationAndSmsPermissionsAndSendLocation() {
        if (checkSmsPermission()) {
            if (currentLocation == null) {
                checkLocationPermission()
                Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show()
                speak("Getting your location")
            } else {
                sendLocationToEmergencyContact()
            }
        }
    }
    
    private fun callEmergencyServices() {
        try {
            val emergencyNumber = "911" // Use appropriate emergency number for your region
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$emergencyNumber")
            
            // Confirm with the user before making the call
            speak(getString(R.string.emergency_call_initiated), TextToSpeech.QUEUE_FLUSH)
            Toast.makeText(this, getString(R.string.emergency_call_initiated), Toast.LENGTH_LONG).show()
            
            // Vibrate to provide tactile feedback
            vibrate(500)
            
            // Start the call
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error making emergency call: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun callEmergencyContact() {
        try {
            val phoneNumber = emergencyContact.phoneNumber
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$phoneNumber")
            
            // Confirm with the user before making the call
            speak("Calling emergency contact ${emergencyContact.name}", TextToSpeech.QUEUE_FLUSH)
            Toast.makeText(this, "Calling emergency contact ${emergencyContact.name}", Toast.LENGTH_LONG).show()
            
            // Vibrate to provide tactile feedback
            vibrate(500)
            
            // Start the call
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error calling emergency contact: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendLocationToEmergencyContact() {
        try {
            val location = currentLocation
            if (location != null) {
                val phoneNumber = emergencyContact.phoneNumber
                val message = "EMERGENCY: I need help. My current location is: " +
                        "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                
                speak(getString(R.string.emergency_message_sent), TextToSpeech.QUEUE_FLUSH)
                Toast.makeText(this, getString(R.string.emergency_message_sent), Toast.LENGTH_LONG).show()
                
                // Vibrate to provide tactile feedback
                vibrate(500)
            } else {
                Toast.makeText(this, "Location not available. Please try again.", Toast.LENGTH_SHORT).show()
                speak("Location not available. Please try again.")
                
                // Try to get the location again
                checkLocationPermission()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error sending location: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCurrentLocation()
                } else {
                    Toast.makeText(this, "Location permission is required for sending your location in emergencies.", Toast.LENGTH_LONG).show()
                    speak("Location permission is required for sending your location in emergencies.")
                }
            }
            CALL_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, retry the call
                    Toast.makeText(this, "Call permission granted. Please try again.", Toast.LENGTH_SHORT).show()
                    speak("Call permission granted. Please try again.")
                } else {
                    Toast.makeText(this, "Call permission is required for emergency calls.", Toast.LENGTH_LONG).show()
                    speak("Call permission is required for emergency calls.")
                }
            }
            SMS_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, retry sending SMS
                    Toast.makeText(this, "SMS permission granted. Please try again.", Toast.LENGTH_SHORT).show()
                    speak("SMS permission granted. Please try again.")
                } else {
                    Toast.makeText(this, "SMS permission is required for sending emergency messages.", Toast.LENGTH_LONG).show()
                    speak("SMS permission is required for sending emergency messages.")
                }
            }
        }
    }
    
    // Simple data class for emergency contact information
    data class EmergencyContact(val name: String, val phoneNumber: String)
}
