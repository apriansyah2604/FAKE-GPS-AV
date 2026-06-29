package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.LocationRepository
import com.example.data.SavedLocation
import com.example.service.MockLocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import com.example.BuildConfig

class LocationViewModel(context: Context) : ViewModel() {

    private val db = AppDatabase.getDatabase(context)
    private val repository = LocationRepository(db.locationDao())

    // UI States
    val favorites: StateFlow<List<SavedLocation>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<SavedLocation>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active target coordinates (defaults to Jakarta Monas)
    private val _targetLat = MutableStateFlow(-6.175392)
    val targetLat: StateFlow<Double> = _targetLat.asStateFlow()

    private val _targetLng = MutableStateFlow(106.827153)
    val targetLng: StateFlow<Double> = _targetLng.asStateFlow()

    // Config options
    private val _isJitterEnabled = MutableStateFlow(true)
    val isJitterEnabled: StateFlow<Boolean> = _isJitterEnabled.asStateFlow()

    private val _isDriftEnabled = MutableStateFlow(true)
    val isDriftEnabled: StateFlow<Boolean> = _isDriftEnabled.asStateFlow()

    private val _isAccuracyVarEnabled = MutableStateFlow(true)
    val isAccuracyVarEnabled: StateFlow<Boolean> = _isAccuracyVarEnabled.asStateFlow()

    private val _isMockingActive = MutableStateFlow(false)
    val isMockingActive: StateFlow<Boolean> = _isMockingActive.asStateFlow()

    // Geocoder Search state
    private val _searchResults = MutableStateFlow<List<GeocodeResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodeResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    // Reverse Geocoding state
    private val _reverseGeocodedAddress = MutableStateFlow<String?>(null)
    val reverseGeocodedAddress: StateFlow<String?> = _reverseGeocodedAddress.asStateFlow()

    // AI suggestions & analysis state
    private val _aiRecommendation = MutableStateFlow<String?>(null)
    val aiRecommendation: StateFlow<String?> = _aiRecommendation.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    init {
        // Regularly synchronize standard service run status
        viewModelScope.launch {
            while (true) {
                _isMockingActive.value = MockLocationService.isRunning
                if (MockLocationService.isRunning) {
                    _targetLat.value = MockLocationService.currentMockLat
                    _targetLng.value = MockLocationService.currentMockLng
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    // Track if the latest coordinate update originated from the map itself (to prevent infinite loop or view jittering)
    private val _isUpdateFromMap = MutableStateFlow(false)
    val isUpdateFromMap: StateFlow<Boolean> = _isUpdateFromMap.asStateFlow()

    fun updateCoordinates(lat: Double, lng: Double, context: Context? = null, fromMap: Boolean = false) {
        _isUpdateFromMap.value = fromMap
        _targetLat.value = lat
        _targetLng.value = lng
        reverseGeocode(lat, lng)

        // If currently mocking, instantly stream updated parameters to service
        if (MockLocationService.isRunning && context != null) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_UPDATE
                putExtra(MockLocationService.EXTRA_LATITUDE, lat)
                putExtra(MockLocationService.EXTRA_LONGITUDE, lng)
                putExtra(MockLocationService.EXTRA_JITTER, _isJitterEnabled.value)
                putExtra(MockLocationService.EXTRA_DRIFT, _isDriftEnabled.value)
                putExtra(MockLocationService.EXTRA_ACCURACY_VAR, _isAccuracyVarEnabled.value)
            }
            context.startService(intent)
        }
    }

    fun resetUpdateFromMap() {
        _isUpdateFromMap.value = false
    }

    fun reverseGeocode(lat: Double, lng: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mapsApiKey = try {
                    BuildConfig.MAPS_API_KEY
                } catch (e: Exception) {
                    ""
                }
                val hasValidGoogleKey = mapsApiKey.isNotBlank() && mapsApiKey != "MY_MAPS_API_KEY"
                
                var addressFound = ""
                
                if (hasValidGoogleKey) {
                    val encodedLatLng = URLEncoder.encode("$lat,$lng", "UTF-8")
                    val urlString = "https://maps.googleapis.com/maps/api/geocode/json?latlng=$encodedLatLng&key=$mapsApiKey&language=id"
                    val connection = URL(urlString).openConnection()
                    connection.setRequestProperty("User-Agent", "FakeGpsStealthApp/1.0 (Android Client)")
                    val responseText = connection.getInputStream().bufferedReader().use { it.readText() }
                    val jsonResponse = org.json.JSONObject(responseText)
                    if (jsonResponse.optString("status", "") == "OK") {
                        val results = jsonResponse.getJSONArray("results")
                        if (results.length() > 0) {
                            addressFound = results.getJSONObject(0).getString("formatted_address")
                        }
                    }
                }
                
                if (addressFound.isBlank()) {
                    // Fallback to OSM Nominatim reverse geocoding
                    val urlString = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json&accept-language=id,en"
                    val connection = URL(urlString).openConnection()
                    connection.setRequestProperty("User-Agent", "FakeGpsStealthApp/1.0 (Android Client)")
                    val responseText = connection.getInputStream().bufferedReader().use { it.readText() }
                    val jsonResponse = org.json.JSONObject(responseText)
                    addressFound = jsonResponse.optString("display_name", "")
                }
                
                if (addressFound.isNotBlank()) {
                    _reverseGeocodedAddress.value = addressFound
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleJitter(enabled: Boolean, context: Context? = null) {
        _isJitterEnabled.value = enabled
        if (context != null) updateServiceConfig(context)
    }

    fun toggleDrift(enabled: Boolean, context: Context? = null) {
        _isDriftEnabled.value = enabled
        if (context != null) updateServiceConfig(context)
    }

    fun toggleAccuracyVar(enabled: Boolean, context: Context? = null) {
        _isAccuracyVarEnabled.value = enabled
        if (context != null) updateServiceConfig(context)
    }

    private fun updateServiceConfig(context: Context) {
        if (MockLocationService.isRunning) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_UPDATE
                putExtra(MockLocationService.EXTRA_JITTER, _isJitterEnabled.value)
                putExtra(MockLocationService.EXTRA_DRIFT, _isDriftEnabled.value)
                putExtra(MockLocationService.EXTRA_ACCURACY_VAR, _isAccuracyVarEnabled.value)
            }
            context.startService(intent)
        }
    }

    // Check if the app is selected as Mock Location App in Android Developer Options
    fun isMockLocationAppSelected(context: Context): Boolean {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            // Attempting to remove and add test provider checks permission
            locationManager.addTestProvider(
                "temp_check_provider",
                false, false, false, false, false, false, false,
                android.location.Criteria.POWER_LOW,
                android.location.Criteria.ACCURACY_FINE
            )
            locationManager.removeTestProvider("temp_check_provider")
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            // If other errors happen, fallback to true so we don't block
            true
        }
    }

    fun startMocking(context: Context) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_LATITUDE, _targetLat.value)
            putExtra(MockLocationService.EXTRA_LONGITUDE, _targetLng.value)
            putExtra(MockLocationService.EXTRA_JITTER, _isJitterEnabled.value)
            putExtra(MockLocationService.EXTRA_DRIFT, _isDriftEnabled.value)
            putExtra(MockLocationService.EXTRA_ACCURACY_VAR, _isAccuracyVarEnabled.value)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        _isMockingActive.value = true

        // Insert coordinates into History
        viewModelScope.launch {
            repository.insert(
                SavedLocation(
                    label = "Mocking: ${String.format("%.4f", _targetLat.value)}, ${String.format("%.4f", _targetLng.value)}",
                    latitude = _targetLat.value,
                    longitude = _targetLng.value,
                    isHistory = true
                )
            )
        }
    }

    fun stopMocking(context: Context) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        context.startService(intent)
        _isMockingActive.value = false
    }

    fun saveFavorite(label: String) {
        viewModelScope.launch {
            repository.insert(
                SavedLocation(
                    label = label,
                    latitude = _targetLat.value,
                    longitude = _targetLng.value,
                    isHistory = false
                )
            )
        }
    }

    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch {
            repository.deleteById(location.id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    // Geocoding Search using OpenStreetMap Nominatim API (No API keys needed, fully functional)
    fun searchPlace(query: String) {
        if (query.isBlank()) return
        _isSearching.value = true
        _searchError.value = null
        _searchResults.value = emptyList()

        // 1. Direct coordinate check: matches "-6.175392, 106.827153" or "-6.175392 106.827153"
        val coordRegex = """^\s*(-?\d+(?:\.\d+)?)\s*[,;\s]\s*(-?\d+(?:\.\d+)?)\s*$""".toRegex()
        val matchResult = coordRegex.find(query)
        if (matchResult != null) {
            try {
                val lat = matchResult.groupValues[1].toDouble()
                val lon = matchResult.groupValues[2].toDouble()
                val customResult = GeocodeResult(lat, lon, "Koordinat Kustom: $lat, $lon")
                _searchResults.value = listOf(customResult)
                _targetLat.value = lat
                _targetLng.value = lon
                _isSearching.value = false
                return
            } catch (e: Exception) {
                // Fail-safe fallback to normal OSM query if parsing has issues
            }
        }

        // 2. OSM Nominatim or Google Geocoding Search (Indonesian language preference, with spelling/abbreviation fallbacks)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mapsApiKey = try {
                    BuildConfig.MAPS_API_KEY
                } catch (e: Exception) {
                    ""
                }
                val hasValidGoogleKey = mapsApiKey.isNotBlank() && mapsApiKey != "MY_MAPS_API_KEY"

                var foundResults = false
                var lastException: Exception? = null

                // Generate alternative candidate queries to make searches extremely resilient
                val queriesToTry = mutableListOf<String>()
                queriesToTry.add(query)

                // Alternate between Indonesian spellings "Grha" and "Graha"
                if (query.contains("grha", ignoreCase = true)) {
                    queriesToTry.add(query.replace("grha", "graha", ignoreCase = true))
                    queriesToTry.add(query.replace("grha", "Graha", ignoreCase = true))
                } else if (query.contains("graha", ignoreCase = true)) {
                    queriesToTry.add(query.replace("graha", "grha", ignoreCase = true))
                    queriesToTry.add(query.replace("graha", "Grha", ignoreCase = true))
                }

                // Alternate between British and American English spellings "Centre" and "Center"
                if (query.contains("centre", ignoreCase = true)) {
                    queriesToTry.add(query.replace("centre", "center", ignoreCase = true))
                } else if (query.contains("center", ignoreCase = true)) {
                    queriesToTry.add(query.replace("center", "centre", ignoreCase = true))
                }

                // Combined substitution variations (e.g. Grha ... Centre -> Graha ... Center)
                var combined = query
                if (combined.contains("grha", ignoreCase = true)) {
                    combined = combined.replace("grha", "graha", ignoreCase = true)
                } else if (combined.contains("graha", ignoreCase = true)) {
                    combined = combined.replace("graha", "grha", ignoreCase = true)
                }
                if (combined.contains("centre", ignoreCase = true)) {
                    combined = combined.replace("centre", "center", ignoreCase = true)
                } else if (combined.contains("center", ignoreCase = true)) {
                    combined = combined.replace("center", "centre", ignoreCase = true)
                }
                if (combined != query && !queriesToTry.contains(combined)) {
                    queriesToTry.add(combined)
                }

                // Try stripping prefix descriptors (e.g. "Grha ", "Graha ", "Gedung ", "Hotel ") so we search for the core name
                val cleanPrefixes = listOf("grha ", "graha ", "gedung ", "hotel ", "rs ", "rsu ", "klinik ")
                for (prefix in cleanPrefixes) {
                    if (query.lowercase().startsWith(prefix)) {
                        val stripped = query.substring(prefix.length).trim()
                        if (stripped.isNotEmpty() && !queriesToTry.contains(stripped)) {
                            queriesToTry.add(stripped)
                            if (stripped.contains("centre", ignoreCase = true)) {
                                queriesToTry.add(stripped.replace("centre", "center", ignoreCase = true))
                            } else if (stripped.contains("center", ignoreCase = true)) {
                                queriesToTry.add(stripped.replace("center", "centre", ignoreCase = true))
                            }
                        }
                    }
                }

                // First, try Google Maps Geocoding API if a valid key is provided
                if (hasValidGoogleKey) {
                    for (candidate in queriesToTry.distinct()) {
                        try {
                            val encodedQuery = URLEncoder.encode(candidate, "UTF-8")
                            val urlString = "https://maps.googleapis.com/maps/api/geocode/json?address=$encodedQuery&key=$mapsApiKey&language=id"
                            val connection = URL(urlString).openConnection()
                            connection.setRequestProperty("User-Agent", "FakeGpsStealthApp/1.0 (Android Client)")

                            val responseText = connection.getInputStream().bufferedReader().use { it.readText() }
                            val jsonResponse = org.json.JSONObject(responseText)
                            val status = jsonResponse.optString("status", "")

                            if (status == "OK") {
                                val resultsArray = jsonResponse.getJSONArray("results")
                                if (resultsArray.length() > 0) {
                                    val list = mutableListOf<GeocodeResult>()
                                    for (i in 0 until resultsArray.length()) {
                                        val resultObj = resultsArray.getJSONObject(i)
                                        val formattedAddress = resultObj.getString("formatted_address")
                                        val geometry = resultObj.getJSONObject("geometry")
                                        val location = geometry.getJSONObject("location")
                                        val lat = location.getDouble("lat")
                                        val lon = location.getDouble("lng")
                                        list.add(GeocodeResult(lat, lon, formattedAddress))
                                    }
                                    _searchResults.value = list
                                    val bestMatch = list.first()
                                    _targetLat.value = bestMatch.latitude
                                    _targetLng.value = bestMatch.longitude
                                    foundResults = true
                                    break // Found results with Google, stop the loop!
                                }
                            }
                        } catch (e: Exception) {
                            lastException = e
                            e.printStackTrace()
                        }
                    }
                }

                // Fallback to OSM Nominatim if Google Maps Geocoding didn't find results or wasn't configured
                if (!foundResults) {
                    // Sequentially try each query candidate until we get a hit
                    for (candidate in queriesToTry.distinct()) {
                        try {
                            val encodedQuery = URLEncoder.encode(candidate, "UTF-8")
                            val urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5&addressdetails=1&accept-language=id,en"
                            val connection = URL(urlString).openConnection()
                            connection.setRequestProperty("User-Agent", "FakeGpsStealthApp/1.0 (Android Client)")

                            val responseText = connection.getInputStream().bufferedReader().use { it.readText() }
                            val jsonArray = org.json.JSONArray(responseText)

                            if (jsonArray.length() > 0) {
                                val list = mutableListOf<GeocodeResult>()
                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    val lat = obj.getDouble("lat")
                                    val lon = obj.getDouble("lon")
                                    val displayName = obj.getString("display_name")
                                    list.add(GeocodeResult(lat, lon, displayName))
                                }
                                _searchResults.value = list
                                // Auto-focus the map on the very first/best match
                                val bestMatch = list.first()
                                _targetLat.value = bestMatch.latitude
                                _targetLng.value = bestMatch.longitude
                                foundResults = true
                                break // Successfully found results, stop the search loop!
                            }
                        } catch (e: Exception) {
                            lastException = e
                            e.printStackTrace()
                        }
                    }
                }

                if (!foundResults) {
                    if (lastException != null) {
                        _searchError.value = "Error koneksi: ${lastException.localizedMessage}"
                    } else {
                        _searchError.value = "Lokasi tidak ditemukan."
                    }
                }
            } catch (e: Exception) {
                _searchError.value = "Error: ${e.localizedMessage}"
                e.printStackTrace()
            } finally {
                _isSearching.value = false
            }
        }

    }

    // Call Gemini AI using Firebase AI models to give custom security tips about current stealth configurations
    fun fetchAiSecurityAnalysis(apiKey: String) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _aiRecommendation.value = "Kunci Gemini API belum diatur di Panel Secrets. Masukkan kunci API untuk mendapatkan analisis keamanan cerdas."
            return
        }

        _isAiLoading.value = true
        _aiRecommendation.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Initialize modern Gemini model (gemini-3.5-flash as specified in requirements)
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val prompt = """
                    Kamu adalah asisten keamanan siber ahli GPS dan privasi lokasi.
                    Menganalisis pengaturan simulasi lokasi saat ini:
                    - Garis Lintang: ${_targetLat.value}
                    - Garis Bujur: ${_targetLng.value}
                    - Jitter GPS Aktif: ${_isJitterEnabled.value}
                    - Simulasi Drift Berjalan: ${_isDriftEnabled.value}
                    - Variansi Akurasi Aktif: ${_isAccuracyVarEnabled.value}
                    
                    Berikan panduan taktis, singkat, ramah, dan mendalam (maksimal 3 paragraf) dalam bahasa Indonesia mengenai cara memaksimalkan privasi ini agar tidak terdeteksi oleh algoritma deteksi manipulasi sensor atau anti-mocking (misalnya: mengapa jitter dinamis & fluktuasi akurasi sangat krusial, tips agar tidak berpindah lokasi terlalu drastis di aplikasi kurir/ojek online guna menghindari anomali teleportasi).
                """.trimIndent()

                val requestJson = JSONObject()
                val contentsArray = org.json.JSONArray()
                val contentObj = JSONObject()
                val partsArray = org.json.JSONArray()
                val partObj = JSONObject()
                partObj.put("text", prompt)
                partsArray.put(partObj)
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                requestJson.put("contents", contentsArray)

                val requestBody = requestJson.toString()
                connection.outputStream.use { os ->
                    val input = requestBody.toByteArray(charset("utf-8"))
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val responseJson = JSONObject(responseText)
                    val candidates = responseJson.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val firstPart = parts?.optJSONObject(0)
                    val text = firstPart?.optString("text") ?: "Tidak ada respons teks dari AI."
                    _aiRecommendation.value = text
                } else {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error response"
                    _aiRecommendation.value = "Gagal menghubungi Gemini (HTTP $responseCode): $errorText"
                }
            } catch (e: Exception) {
                _aiRecommendation.value = "Gagal memuat rekomendasi AI: ${e.localizedMessage}"
                e.printStackTrace()
            } finally {
                _isAiLoading.value = false
            }
        }
    }
}

data class GeocodeResult(
    val latitude: Double,
    val longitude: Double,
    val displayName: String
)
