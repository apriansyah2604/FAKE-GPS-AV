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
    private val _searchResult = MutableStateFlow<GeocodeResult?>(null)
    val searchResult: StateFlow<GeocodeResult?> = _searchResult.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

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

    fun updateCoordinates(lat: Double, lng: Double, context: Context? = null) {
        _targetLat.value = lat
        _targetLng.value = lng

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

    // Geocoding Search using OpenStreetMap Nominatim API (No API keys needed, fully functional)
    fun searchPlace(query: String) {
        if (query.isBlank()) return
        _isSearching.value = true
        _searchError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1"
                val connection = URL(urlString).openConnection()
                connection.setRequestProperty("User-Agent", "FakeGpsStealthApp/1.0 (Android Client)")

                val responseText = connection.getInputStream().bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(responseText)

                if (jsonArray.length() > 0) {
                    val obj = jsonArray.getJSONObject(0)
                    val lat = obj.getDouble("lat")
                    val lon = obj.getDouble("lon")
                    val displayName = obj.getString("display_name")

                    _searchResult.value = GeocodeResult(lat, lon, displayName)
                    _targetLat.value = lat
                    _targetLng.value = lon
                } else {
                    _searchError.value = "Lokasi tidak ditemukan."
                }
            } catch (e: Exception) {
                _searchError.value = "Error koneksi: ${e.localizedMessage}"
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
