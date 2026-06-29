package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SavedLocation
import com.example.service.MockLocationService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: LocationViewModel,
    geminiApiKey: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // State bindings
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val targetLat by viewModel.targetLat.collectAsStateWithLifecycle()
    val targetLng by viewModel.targetLng.collectAsStateWithLifecycle()

    val isJitterEnabled by viewModel.isJitterEnabled.collectAsStateWithLifecycle()
    val isDriftEnabled by viewModel.isDriftEnabled.collectAsStateWithLifecycle()
    val isAccuracyVarEnabled by viewModel.isAccuracyVarEnabled.collectAsStateWithLifecycle()
    val isMockingActive by viewModel.isMockingActive.collectAsStateWithLifecycle()

    val searchResult by viewModel.searchResult.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()

    val aiRecommendation by viewModel.aiRecommendation.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()

    // Local controller states
    var searchQuery by remember { mutableStateOf("") }
    var locationLabelInput by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0: Stealth Config, 1: Bookmarks, 2: History

    // Check authorization periodically
    var isDevOptionAuthorized by remember { mutableStateOf(true) }
    LaunchedEffect(isMockingActive) {
        isDevOptionAuthorized = viewModel.isMockLocationAppSelected(context)
    }

    // Reference to WebView to push coordinate updates
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // When coordinate updates from outside the map (e.g. bookmark/search), fly map marker
    LaunchedEffect(targetLat, targetLng) {
        webViewRef?.let { webView ->
            webView.post {
                webView.evaluateJavascript("setMapLocation($targetLat, $targetLng, 15, true)", null)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFDDE2F9), RoundedCornerShape(50))
                                    .clickable { /* Side action */ },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = Color(0xFF44474E),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "LocateHide Pro",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1B1B1F),
                                letterSpacing = (-0.2).sp
                            )
                        }

                        // Status pill badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .background(Color(0xFFE1E2EC), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isMockingActive) Color(0xFF4CAF50) else Color(0xFFBA1A1A),
                                        shape = RoundedCornerShape(50)
                                    )
                            )
                            Text(
                                text = if (isMockingActive) "STEALTH ACTIVE" else "STEALTH INACTIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF44474E),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF7F9FF),
                    titleContentColor = Color(0xFF1B1B1F)
                )
            )
        },
        containerColor = Color(0xFFF7F9FF)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF7F9FF))
        ) {
            // 1. Search Bar Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFDDE2F9)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari kota, negara, koordinat...", color = Color(0xFF74777F)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1B1B1F),
                                unfocusedTextColor = Color(0xFF1B1B1F),
                                focusedBorderColor = Color(0xFF1B6EF3),
                                unfocusedBorderColor = Color(0xFFE1E2EC),
                                focusedContainerColor = Color(0xFFF7F9FF),
                                unfocusedContainerColor = Color(0xFFF7F9FF)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, "Hapus", tint = Color(0xFF74777F))
                                    }
                                }
                            }
                        )

                        Button(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    viewModel.searchPlace(searchQuery)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6EF3)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("search_button")
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, "Cari", tint = Color.White)
                            }
                        }
                    }

                    searchError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    searchResult?.let { result ->
                        Text(
                            text = "Ditemukan: ${result.displayName}",
                            color = Color(0xFF34D399), // Emerald Accent
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            // 2. Interactive Map Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFDDE2F9), RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Push current coordinate to synchronize Leaflet initially
                                    evaluateJavascript("setMapLocation($targetLat, $targetLng, 13, true)", null)
                                }
                            }

                            // Inject JS-to-Kotlin callback
                            addJavascriptInterface(object {
                                @android.webkit.JavascriptInterface
                                fun onLocationSelected(lat: Double, lng: Double) {
                                    // Triggered on Map Click or Drag pin end
                                    viewModel.updateCoordinates(lat, lng, context)
                                }
                            }, "AndroidBridge")

                            loadUrl("file:///android_asset/map.html")
                            webViewRef = this
                        }
                    },
                    update = {
                        // Managed by state updates trigger flow
                    }
                )

                // Float Map Controls Overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Zoom Out to World Map (Peta Dunia)
                    SmallFloatingActionButton(
                        onClick = {
                            webViewRef?.let { webView ->
                                webView.post {
                                    webView.evaluateJavascript("setMapLocation($targetLat, $targetLng, 2, true)", null)
                                }
                            }
                        },
                        containerColor = Color.White,
                        contentColor = Color(0xFF10B981) // Emerald Green
                    ) {
                        Icon(Icons.Default.Public, "Lihat Peta Dunia")
                    }

                    // 2. Zoom In to Focus Pin (Fokus Koordinat)
                    SmallFloatingActionButton(
                        onClick = {
                            webViewRef?.let { webView ->
                                webView.post {
                                    webView.evaluateJavascript("setMapLocation($targetLat, $targetLng, 15, true)", null)
                                }
                            }
                        },
                        containerColor = Color.White,
                        contentColor = Color(0xFF1B6EF3) // Standard Blue
                    ) {
                        Icon(Icons.Default.LocationSearching, "Fokus Pin")
                    }

                    // 3. Reset to Jakarta Monas
                    SmallFloatingActionButton(
                        onClick = {
                            viewModel.updateCoordinates(-6.175392, 106.827153, context)
                        },
                        containerColor = Color.White,
                        contentColor = Color(0xFFE11D48) // Crimson Red
                    ) {
                        Icon(Icons.Default.Home, "Reset Jakarta")
                    }
                }
            }

            // 3. Main Mocking Control panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFDDE2F9)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3FB)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Coordinates Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "KOORDINAT AKTIF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF44474E),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = String.format("%.6f , %.6f", targetLat, targetLng),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B1B1F),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                             )
                        }

                        // Save coordinate bookmark icon
                        IconButton(
                            onClick = {
                                locationLabelInput = ""
                                showSaveDialog = true
                            },
                            modifier = Modifier
                                .background(Color(0xFFDDE2F9), RoundedCornerShape(50))
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Simpan Lokasi",
                                tint = Color(0xFFF43F5E),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Start / Stop Control Button with dynamic styling and ripple
                    Button(
                        onClick = {
                            if (isMockingActive) {
                                viewModel.stopMocking(context)
                            } else {
                                viewModel.startMocking(context)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("action_toggle_mock"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMockingActive) Color(0xFFBA1A1A) else Color(0xFF1B6EF3)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isMockingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Text(
                                text = if (isMockingActive) "HENTIKAN LOKASI PALSU" else "MULAI PEMALSUAN LOKASI",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = Color.White
                            )
                        }
                    }

                    // Developer Options Warning Warning Alert Banner
                    AnimatedVisibility(
                        visible = !isDevOptionAuthorized,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFDAD9)),
                            border = BorderStroke(1.dp, Color(0xFFBA1A1A)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                  ) {
                                    Icon(Icons.Default.Warning, "Peringatan", tint = Color(0xFFBA1A1A))
                                    Text(
                                        text = "Aplikasi Belum Terdaftar!",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFBA1A1A)
                                    )
                                }
                                Text(
                                    text = "Agar fake GPS bekerja, aktifkan 'Pilih aplikasi lokasi palsu' di Opsi Developer HP Anda dan pilih aplikasi ini.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF410002),
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                )
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val intent = Intent(Settings.ACTION_SETTINGS)
                                            context.startActivity(intent)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA1A1A)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Buka Opsi Developer", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Navigation tabs inside panel (Stealth Config, Favorites, History)
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color(0xFFFDFBFF),
                contentColor = Color(0xFF1B6EF3),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = Color(0xFF1B6EF3)
                    )
                }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Anti-Deteksi", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Security, "Stealth Config", modifier = Modifier.size(16.dp)) },
                    selectedContentColor = Color(0xFF1B6EF3),
                    unselectedContentColor = Color(0xFF44474E)
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Favorit", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Favorite, "Bookmarks", modifier = Modifier.size(16.dp)) },
                    selectedContentColor = Color(0xFF1B6EF3),
                    unselectedContentColor = Color(0xFF44474E)
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Riwayat", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.History, "History", modifier = Modifier.size(16.dp)) },
                    selectedContentColor = Color(0xFF1B6EF3),
                    unselectedContentColor = Color(0xFF44474E)
                )
            }

            // 5. Dynamic Tab Content View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> StealthSettingsTab(
                        viewModel = viewModel,
                        geminiApiKey = geminiApiKey,
                        isJitterEnabled = isJitterEnabled,
                        isDriftEnabled = isDriftEnabled,
                        isAccuracyVarEnabled = isAccuracyVarEnabled,
                        isAiLoading = isAiLoading,
                        aiRecommendation = aiRecommendation,
                        context = context
                    )
                    1 -> FavoritesTab(
                        favorites = favorites,
                        onSelectLocation = { fav ->
                            viewModel.updateCoordinates(fav.latitude, fav.longitude, context)
                        },
                        onDeleteLocation = { fav ->
                            viewModel.deleteLocation(fav)
                        }
                    )
                    2 -> HistoryTab(
                        history = history,
                        onSelectLocation = { hist ->
                            viewModel.updateCoordinates(hist.latitude, hist.longitude, context)
                        },
                        onClearHistory = {
                            viewModel.clearHistory()
                        }
                    )
                }
            }
        }
    }

    // Bookmark/Save Label Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Simpan Koordinat Ini", color = Color(0xFF1B1B1F), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Berikan label/nama untuk menandai lokasi ini:", color = Color(0xFF44474E), fontSize = 13.sp)
                    OutlinedTextField(
                        value = locationLabelInput,
                        onValueChange = { locationLabelInput = it },
                        placeholder = { Text("Contoh: Kantor Utama, Rumah, Paris", color = Color(0xFF74777F)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1B1B1F),
                            unfocusedTextColor = Color(0xFF1B1B1F),
                            focusedBorderColor = Color(0xFF1B6EF3),
                            unfocusedBorderColor = Color(0xFFDDE2F9),
                            focusedContainerColor = Color(0xFFF7F9FF),
                            unfocusedContainerColor = Color(0xFFF7F9FF)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val label = if (locationLabelInput.isNotBlank()) locationLabelInput else "Lokasi Kustom"
                        viewModel.saveFavorite(label)
                        showSaveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6EF3)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Simpan", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Batal", color = Color(0xFF44474E))
                }
            },
            containerColor = Color.White
        )
    }
}

// Stealth Config Settings Controls Tab Component
@Composable
fun StealthSettingsTab(
    viewModel: LocationViewModel,
    geminiApiKey: String,
    isJitterEnabled: Boolean,
    isDriftEnabled: Boolean,
    isAccuracyVarEnabled: Boolean,
    isAiLoading: Boolean,
    aiRecommendation: String?,
    context: Context
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDDE2F9)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ALGORITMA ANTI-DETEKSI GPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B6EF3),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Aktifkan teknik rekayasa sinyal palsu agar simulasi terlihat alami dan tidak dicurigai oleh program proteksi keamanan.",
                        fontSize = 11.sp,
                        color = Color(0xFF74777F),
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    // Jitter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Jitter Sinyal Satelit (Vibrasi)", color = Color(0xFF1B1B1F), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Simulasikan mikro-fluktuasi atmosfer (1-2 meter) alami chip GPS fisik.", color = Color(0xFF74777F), fontSize = 10.sp)
                        }
                        Switch(
                            checked = isJitterEnabled,
                            onCheckedChange = { viewModel.toggleJitter(it, context) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF1B6EF3),
                                checkedTrackColor = Color(0xFF1B6EF3).copy(alpha = 0.3f),
                                uncheckedThumbColor = Color(0xFF74777F),
                                uncheckedTrackColor = Color(0xFFE1E2EC)
                            )
                        )
                    }

                    Divider(color = Color(0xFFE1E2EC), modifier = Modifier.padding(vertical = 10.dp))

                    // Drift
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Simulasi Pergerakan Dinamis (Drifting)", color = Color(0xFF1B1B1F), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Simulasikan pergerakan lambat melingkar (1.2m/s) seolah-olah sedang berjalan kaki di tempat.", color = Color(0xFF74777F), fontSize = 10.sp)
                        }
                        Switch(
                            checked = isDriftEnabled,
                            onCheckedChange = { viewModel.toggleDrift(it, context) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF1B6EF3),
                                checkedTrackColor = Color(0xFF1B6EF3).copy(alpha = 0.3f),
                                uncheckedThumbColor = Color(0xFF74777F),
                                uncheckedTrackColor = Color(0xFFE1E2EC)
                            )
                        )
                    }

                    Divider(color = Color(0xFFE1E2EC), modifier = Modifier.padding(vertical = 10.dp))

                    // Accuracy variance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Variansi Akurasi Sensor", color = Color(0xFF1B1B1F), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Fiksasi akurasi acak (3m-12m) meniru interferensi gedung atau cuaca mendung.", color = Color(0xFF74777F), fontSize = 10.sp)
                        }
                        Switch(
                            checked = isAccuracyVarEnabled,
                            onCheckedChange = { viewModel.toggleAccuracyVar(it, context) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF1B6EF3),
                                checkedTrackColor = Color(0xFF1B6EF3).copy(alpha = 0.3f),
                                uncheckedThumbColor = Color(0xFF74777F),
                                uncheckedTrackColor = Color(0xFFE1E2EC)
                            )
                        )
                    }
                }
            }
        }

        // Gemini AI Cyber Advisor Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, "AI Advisor", tint = Color(0xFF10B981))
                        Text(
                            text = "ANALISIS ANTIDETEKSI GEMINI AI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "Konsultasikan pengaturan lokasi dan koordinat saat ini secara langsung untuk menguji ketahanan stealth.",
                        fontSize = 11.sp,
                        color = Color(0xFF74777F),
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Button(
                        onClick = { viewModel.fetchAiSecurityAnalysis(geminiApiKey) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAiLoading
                    ) {
                        if (isAiLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Jalankan Audit Keamanan Lokasi", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    aiRecommendation?.let { advice ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3FB)),
                            border = BorderStroke(1.dp, Color(0xFFDDE2F9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = advice,
                                fontSize = 12.sp,
                                color = Color(0xFF1B1B1F),
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Favorites Bookmarks Tab Component
@Composable
fun FavoritesTab(
    favorites: List<SavedLocation>,
    onSelectLocation: (SavedLocation) -> Unit,
    onDeleteLocation: (SavedLocation) -> Unit
) {
    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = Color(0xFF74777F),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Belum ada lokasi favorit.",
                    color = Color(0xFF1B1B1F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Ketuk ikon hati di atas untuk menandai lokasi penting.",
                    color = Color(0xFF74777F),
                    fontSize = 11.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(favorites) { fav ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectLocation(fav) },
                    border = BorderStroke(1.dp, Color(0xFFDDE2F9)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LocationOn, "Pin", tint = Color(0xFF1B6EF3))
                            Column {
                                Text(fav.label, color = Color(0xFF1B1B1F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = String.format("%.5f, %.5f", fav.latitude, fav.longitude),
                                    color = Color(0xFF74777F),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        IconButton(onClick = { onDeleteLocation(fav) }) {
                            Icon(Icons.Default.Delete, "Hapus", tint = Color(0xFFBA1A1A))
                        }
                    }
                }
            }
        }
    }
}

// History Records Tab Component
@Composable
fun HistoryTab(
    history: List<SavedLocation>,
    onSelectLocation: (SavedLocation) -> Unit,
    onClearHistory: () -> Unit
) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = Color(0xFF74777F),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Riwayat pemalsuan masih kosong.",
                    color = Color(0xFF74777F),
                    fontSize = 13.sp
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClearHistory) {
                    Icon(Icons.Default.Delete, "Clear History", tint = Color(0xFFBA1A1A), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bersihkan Riwayat", color = Color(0xFFBA1A1A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(history) { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLocation(record) },
                        border = BorderStroke(1.dp, Color(0xFFDDE2F9)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.History, "History Item", tint = Color(0xFF74777F), modifier = Modifier.size(18.dp))
                            Column {
                                Text(record.label, color = Color(0xFF1B1B1F), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                Text(
                                    text = "Koordinat: ${record.latitude}, ${record.longitude}",
                                    color = Color(0xFF74777F),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
