package com.example.major_project

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.major_project.ui.theme.Major_ProjectTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    private val firebaseManager = FirebaseManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Major_ProjectTheme { MainScreen(firebaseManager) } }
    }
}

sealed class AppScreen(val route: String, val label: String, val icon: ImageVector) {
    data object Map : AppScreen("map", "Map", Icons.Default.Map)
    data object Report : AppScreen("report", "Report", Icons.Default.AddCircle)
    data object Profile : AppScreen("profile", "Profile", Icons.Default.Person)
}

@Composable
fun MainScreen(firebaseManager: FirebaseManager) {
    val navController = rememberNavController()
    val currentUser by firebaseManager.authState.collectAsState()
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isGuestMode by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            firebaseManager.getProfile { profile -> userProfile = profile }
        }
    }

    if (currentUser == null && !isGuestMode) {
        LoginScreen(
            onLoginSuccess = { role ->
                coroutineScope.launch {
                    val result = firebaseManager.loginAndSetRole(role)
                    if (result.isFailure) {
                        Toast.makeText(context, "Auth error: ${result.exceptionOrNull()?.message}. Entering Guest Mode.", Toast.LENGTH_SHORT).show()
                        isGuestMode = true
                    }
                }
            },
            onSkip = { isGuestMode = true }
        )
    } else {
        Scaffold(
            bottomBar = { BottomNavigationBar(navController) }
        ) { innerPadding ->
            NavHost(navController = navController, startDestination = AppScreen.Map.route, modifier = Modifier.padding(innerPadding)) {
                composable(AppScreen.Map.route) { MapViewScreen(firebaseManager, userProfile) }
                composable(AppScreen.Report.route) {
                    if (userProfile?.role == UserRole.cleaner) {
                        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text("Cleaners view hazards on the map.", textAlign = TextAlign.Center) }
                    } else {
                        ReportScreen(firebaseManager) { navController.navigate(AppScreen.Map.route) { popUpTo(AppScreen.Map.route) { inclusive = true } } }
                    }
                }
                composable(AppScreen.Profile.route) {
                    ProfileScreen(userProfile) { 
                        if (isGuestMode) isGuestMode = false else firebaseManager.logout()
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(AppScreen.Map, AppScreen.Report, AppScreen.Profile)
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (UserRole) -> Unit, onSkip: () -> Unit) {
    var loadingRole by remember { mutableStateOf<UserRole?>(null) }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF0FDF4)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Surface(modifier = Modifier.size(80.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Eco, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Paryavaran Kavalu", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color(0xFF064E3B))
            Text("Cleaning India, one spot at a time", color = Color(0xFF065F46), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(48.dp))
            if (loadingRole != null) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = { loadingRole = UserRole.citizen; onLoginSuccess(UserRole.citizen) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Login as Citizen", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = { loadingRole = UserRole.cleaner; onLoginSuccess(UserRole.cleaner) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Login as Cleaner", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onSkip) { Text("Skip to Map (Guest Mode)", color = Color.Gray) }
            }
        }
    }
}

@Composable
fun MapViewScreen(firebaseManager: FirebaseManager, profile: UserProfile?) {
    var reports by remember { mutableStateOf(emptyList<WasteReport>()) }
    var selectedReport by remember { mutableStateOf<WasteReport?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 5f) }
    var locationPermissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms -> locationPermissionGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true }

    LaunchedEffect(Unit) {
        firebaseManager.getReports { reports = it }
        if (!locationPermissionGranted) permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                    loc?.let { cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 15f) }
                }
            } catch (e: SecurityException) { e.printStackTrace() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.LightGray)) {
        GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState, properties = MapProperties(isMyLocationEnabled = locationPermissionGranted), uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true)) {
            reports.forEach { report ->
                Marker(state = MarkerState(position = LatLng(report.location.lat, report.location.lng)), title = "${report.wasteType.uppercase()} Waste", icon = BitmapDescriptorFactory.defaultMarker(if (report.status == ReportStatus.pending) BitmapDescriptorFactory.HUE_RED else BitmapDescriptorFactory.HUE_GREEN), onClick = { selectedReport = report; false })
            }
        }
        Row(modifier = Modifier.padding(16.dp).align(Alignment.TopCenter)) {
            StatusChip(label = "Hazard: ${reports.count { it.status == ReportStatus.pending }}", color = Color.Red)
            Spacer(modifier = Modifier.width(8.dp))
            StatusChip(label = "Cleaned: ${reports.count { it.status == ReportStatus.cleaned }}", color = Color.Green)
        }
        selectedReport?.let { report ->
            AlertDialog(onDismissRequest = { selectedReport = null }, title = { Text("${report.wasteType.uppercase()} Waste") },
                text = {
                    Column {
                        if (report.photoUrl.isNotEmpty()) { AsyncImage(model = report.photoUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop); Spacer(modifier = Modifier.height(12.dp)) }
                        Text(report.description); Spacer(modifier = Modifier.height(4.dp))
                        Text("Status: ${report.status.name.uppercase()}", fontWeight = FontWeight.Bold, color = if (report.status == ReportStatus.pending) Color.Red else Color(0xFF059669))
                    }
                },
                confirmButton = {
                    if (profile?.role == UserRole.cleaner && report.status == ReportStatus.pending) {
                        Button(onClick = { coroutineScope.launch { val result = firebaseManager.markAsCleaned(report); if (result.isSuccess) { Toast.makeText(context, "Marked Cleaned!", Toast.LENGTH_SHORT).show(); selectedReport = null } else { Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show() } } }) { Text("Mark Cleaned") }
                    }
                },
                dismissButton = { TextButton(onClick = { selectedReport = null }) { Text("Close") } }
            )
        }
    }
}

@Composable
fun StatusChip(label: String, color: Color) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.width(8.dp)); Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
fun ReportScreen(firebaseManager: FirebaseManager, onReportSubmitted: () -> Unit) {
    var selectedType by remember { mutableStateOf<WasteType?>(null) }
    var description by remember { mutableStateOf("") }
    var photoBase64 by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { coroutineScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(stream, null, options)
                        var sampleSize = 1
                        while (options.outWidth / sampleSize > 1000 || options.outHeight / sampleSize > 1000) { sampleSize *= 2 }
                        context.contentResolver.openInputStream(it)?.use { actualStream ->
                            val finalOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                            BitmapFactory.decodeStream(actualStream, null, finalOptions)
                        }
                    }
                }
                if (bitmap != null) photoBase64 = withContext(Dispatchers.Default) { bitmapToBase64(bitmap) }
            } catch (e: Exception) { e.printStackTrace() }
        } }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        item {
            Text("Report Waste Hazard", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF064E3B))
            Spacer(modifier = Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WasteType.entries.chunked(2).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { type ->
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (selectedType == type) Color(type.color).copy(alpha = 0.2f) else Color(0xFFF3F4F6)).border(1.dp, if (selectedType == type) Color(type.color) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { selectedType = type }.padding(12.dp), contentAlignment = Alignment.Center) { Text(type.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (photoBase64.isEmpty()) "Add Photo" else "Photo Captured") }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { coroutineScope.launch {
                    try {
                        isSubmitting = true
                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                        val locResult = withTimeoutOrNull(5000L) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                            } else null
                        } ?: withTimeoutOrNull(2000L) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                fusedLocationClient.lastLocation.await()
                            } else null
                        }
                        val reportLocation = if (locResult != null) Location(locResult.latitude, locResult.longitude) else Location(20.5937, 78.9629)
                        val result = firebaseManager.submitReport(selectedType?.value ?: "other", description, photoBase64, reportLocation)
                        if (result.isSuccess) { Toast.makeText(context, "Reported! +10 Points", Toast.LENGTH_SHORT).show(); onReportSubmitted() }
                        else { Toast.makeText(context, "Submit Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show() }
                    } catch (e: Exception) { Toast.makeText(context, "App Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                    finally { isSubmitting = false }
                } },
                modifier = Modifier.fillMaxWidth().height(56.dp), enabled = selectedType != null && !isSubmitting, shape = RoundedCornerShape(16.dp)
            ) {
                if (isSubmitting) CircularProgressIndicator(color = Color.White) else Text("Submit Report", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProfileScreen(profile: UserProfile?, onLogout: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Surface(modifier = Modifier.size(100.dp), shape = CircleShape, color = Color.LightGray) {
                if (profile?.photoURL?.isNotEmpty() == true) AsyncImage(model = profile.photoURL, contentDescription = null, contentScale = ContentScale.Crop)
                else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(60.dp), tint = Color.White) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(profile?.displayName ?: "Guest User", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(profile?.role?.name?.uppercase() ?: "GUEST", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Eco-Karma Points", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                        Text("${profile?.ecoKarmaPoints ?: 0L} ✨", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF059669))
                    }
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFF10B981))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2), contentColor = Color.Red), shape = RoundedCornerShape(16.dp)) {
                Icon(if (profile == null) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.Logout, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text(if (profile == null) "Back to Login" else "Logout", fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun bitmapToBase64(bitmap: Bitmap?): String {
    if (bitmap == null) return ""
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
}

fun base64ToBitmap(base64: String): Bitmap {
    val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
}
