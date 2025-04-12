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
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Activity that provides weather information.
 * Gets current weather conditions and forecasts based on the user's location.
 */
class WeatherInformationActivity : BaseAccessibilityActivity(), LocationListener {

    private lateinit var weatherInstructionsTextView: TextView
    private lateinit var weatherStatusTextView: TextView
    private lateinit var currentWeatherButton: Button
    private lateinit var weatherForecastButton: Button
    private lateinit var backButton: Button
    
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    
    private val LOCATION_PERMISSION_CODE = 101
    private val MIN_DISTANCE_CHANGE_FOR_UPDATES = 100.0f // 100 meters
    private val MIN_TIME_BETWEEN_UPDATES = 10000L // 10 seconds
    
    // Weather API settings
    private val WEATHER_API_KEY = "demo" // Using OpenWeatherMap demo key for demonstration
    private val WEATHER_API_URL = "https://api.openweathermap.org/data/2.5/weather"
    private val FORECAST_API_URL = "https://api.openweathermap.org/data/2.5/forecast"
    
    // Executor for network operations
    private val networkExecutor = Executors.newSingleThreadExecutor()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_information)
        
        // Initialize location services
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Initialize views
        weatherInstructionsTextView = findViewById(R.id.weatherInstructionsTextView)
        weatherStatusTextView = findViewById(R.id.weatherStatusTextView)
        currentWeatherButton = findViewById(R.id.currentWeatherButton)
        weatherForecastButton = findViewById(R.id.weatherForecastButton)
        backButton = findViewById(R.id.backButton)
        
        // Set up button click listeners
        currentWeatherButton.setOnClickListener {
            getCurrentWeather()
        }
        
        weatherForecastButton.setOnClickListener {
            getWeatherForecast()
        }
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Request location permission immediately
        checkLocationPermission()
    }
    
    override fun onTtsInitialized() {
        // Speak instructions when TTS is ready
        speak(getString(R.string.weather_information_instructions))
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
                weatherStatusTextView.text = getString(R.string.location_found)
            } else {
                weatherStatusTextView.text = getString(R.string.waiting_for_location)
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location permission is required for weather information.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting location updates: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getCurrentWeather() {
        val location = currentLocation
        if (location == null) {
            Toast.makeText(this, getString(R.string.waiting_for_location), Toast.LENGTH_SHORT).show()
            speak(getString(R.string.waiting_for_location))
            return
        }
        
        // Update UI
        weatherStatusTextView.text = getString(R.string.fetching_weather)
        speak(getString(R.string.fetching_weather))
        
        // Fetch weather data in background
        networkExecutor.execute {
            try {
                // Build the API URL with location and API key
                val urlString = "$WEATHER_API_URL?lat=${location.latitude}&lon=${location.longitude}&units=metric&appid=$WEATHER_API_KEY"
                val url = URL(urlString)
                
                // Open connection and get response
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read the response
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    // Parse the JSON response
                    val weatherData = parseWeatherData(response.toString())
                    
                    // Update UI on the main thread
                    runOnUiThread {
                        weatherStatusTextView.text = weatherData
                        speak(weatherData)
                    }
                } else {
                    // Handle error
                    runOnUiThread {
                        val errorMessage = getString(R.string.weather_fetch_error, responseCode)
                        weatherStatusTextView.text = errorMessage
                        speak(errorMessage)
                    }
                }
            } catch (e: Exception) {
                // Handle network or parsing errors
                runOnUiThread {
                    val errorMessage = getString(R.string.weather_fetch_error_exception, e.message)
                    weatherStatusTextView.text = errorMessage
                    speak(errorMessage)
                }
            }
        }
    }
    
    private fun getWeatherForecast() {
        val location = currentLocation
        if (location == null) {
            Toast.makeText(this, getString(R.string.waiting_for_location), Toast.LENGTH_SHORT).show()
            speak(getString(R.string.waiting_for_location))
            return
        }
        
        // Update UI
        weatherStatusTextView.text = getString(R.string.fetching_forecast)
        speak(getString(R.string.fetching_forecast))
        
        // Fetch forecast data in background
        networkExecutor.execute {
            try {
                // Build the API URL with location and API key
                val urlString = "$FORECAST_API_URL?lat=${location.latitude}&lon=${location.longitude}&units=metric&cnt=8&appid=$WEATHER_API_KEY"
                val url = URL(urlString)
                
                // Open connection and get response
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read the response
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    // Parse the JSON response
                    val forecastData = parseForecastData(response.toString())
                    
                    // Update UI on the main thread
                    runOnUiThread {
                        weatherStatusTextView.text = forecastData
                        speak(forecastData)
                    }
                } else {
                    // Handle error
                    runOnUiThread {
                        val errorMessage = getString(R.string.forecast_fetch_error, responseCode)
                        weatherStatusTextView.text = errorMessage
                        speak(errorMessage)
                    }
                }
            } catch (e: Exception) {
                // Handle network or parsing errors
                runOnUiThread {
                    val errorMessage = getString(R.string.forecast_fetch_error_exception, e.message)
                    weatherStatusTextView.text = errorMessage
                    speak(errorMessage)
                }
            }
        }
    }
    
    private fun parseWeatherData(jsonData: String): String {
        try {
            val json = JSONObject(jsonData)
            
            // Get location name
            val cityName = json.getString("name")
            
            // Get weather description
            val weatherArray = json.getJSONArray("weather")
            val weatherObject = weatherArray.getJSONObject(0)
            val weatherDescription = weatherObject.getString("description")
            
            // Get temperature
            val mainObject = json.getJSONObject("main")
            val temperature = mainObject.getDouble("temp")
            val feelsLike = mainObject.getDouble("feels_like")
            val humidity = mainObject.getInt("humidity")
            
            // Get wind
            val windObject = json.getJSONObject("wind")
            val windSpeed = windObject.getDouble("speed")
            
            // Format the weather information
            return getString(
                R.string.current_weather_info,
                cityName,
                weatherDescription,
                temperature.toInt(),
                feelsLike.toInt(),
                humidity,
                windSpeed
            )
        } catch (e: Exception) {
            return getString(R.string.weather_parse_error, e.message)
        }
    }
    
    private fun parseForecastData(jsonData: String): String {
        try {
            val json = JSONObject(jsonData)
            
            // Get location name
            val cityObject = json.getJSONObject("city")
            val cityName = cityObject.getString("name")
            
            // Get forecast list
            val forecastArray = json.getJSONArray("list")
            
            // Build the forecast information
            val forecastBuilder = StringBuilder()
            forecastBuilder.append(getString(R.string.forecast_for, cityName))
            forecastBuilder.append("\n\n")
            
            // Get the next 24 hours (8 entries, 3 hours each)
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            formatter.timeZone = TimeZone.getDefault()
            
            for (i in 0 until minOf(forecastArray.length(), 8)) {
                val forecastObject = forecastArray.getJSONObject(i)
                
                // Get time
                val timestamp = forecastObject.getLong("dt") * 1000 // Convert to milliseconds
                val date = Date(timestamp)
                val timeString = formatter.format(date)
                
                // Get weather description
                val weatherArray = forecastObject.getJSONArray("weather")
                val weatherObject = weatherArray.getJSONObject(0)
                val weatherDescription = weatherObject.getString("description")
                
                // Get temperature
                val mainObject = forecastObject.getJSONObject("main")
                val temperature = mainObject.getDouble("temp")
                
                // Add to forecast
                forecastBuilder.append("$timeString: ${temperature.toInt()}°C, $weatherDescription\n")
            }
            
            return forecastBuilder.toString()
        } catch (e: Exception) {
            return getString(R.string.forecast_parse_error, e.message)
        }
    }
    
    override fun onLocationChanged(location: Location) {
        currentLocation = location
        weatherStatusTextView.text = getString(R.string.location_updated)
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Location permission is required for weather information.", Toast.LENGTH_LONG).show()
                speak("Location permission is required for weather information.")
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
    
    override fun onDestroy() {
        super.onDestroy()
        networkExecutor.shutdown()
    }
}
