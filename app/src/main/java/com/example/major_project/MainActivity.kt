package com.example.major_project

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.major_project.ui.theme.Major_ProjectTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    private val firebaseManager = FirebaseManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Major_ProjectTheme {
                MainScreen(firebaseManager)
            }
        }
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
    var currentUser by remember { mutableStateOf(firebaseManager.currentUser) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            firebaseManager.ensureProfile(UserRole.citizen)
            firebaseManager.getProfile { profile ->
                userProfile = profile
            }
        }
    }

    if (currentUser == null) {
        LoginScreen(onLoginSuccess = { currentUser = firebaseManager.currentUser })
    } else {
        Scaffold(
            bottomBar = { BottomNavigationBar(navController) }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppScreen.Map.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(AppScreen.Map.route) {
                    MapViewScreen(firebaseManager)
                }
                composable(AppScreen.Report.route) {
                    ReportScreen(firebaseManager) {
                        navController.navigate(AppScreen.Map.route) {
                            popUpTo(AppScreen.Map.route) { inclusive = true }
                        }
                    }
                }
                composable(AppScreen.Profile.route) {
                    ProfileScreen(userProfile) {
                        FirebaseAuth.getInstance().signOut()
                        currentUser = null
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
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF0FDF4)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Eco, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Paryavaran Kavalu", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color(0xFF064E3B))
            Text("Cleaning India, one spot at a time", color = Color(0xFF065F46), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = { onLoginSuccess() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with Google", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MapViewScreen(firebaseManager: FirebaseManager) {
    var reports by remember { mutableStateOf(emptyList<WasteReport>()) }
    val india = LatLng(20.5937, 78.9629)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(india, 5f)
    }

    LaunchedEffect(Unit) {
        firebaseManager.getReports { reports = it }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            reports.forEach { report ->
                Marker(
                    state = MarkerState(position = LatLng(report.location.lat, report.location.lng)),
                    title = "${report.wasteType} Waste",
                    snippet = report.description,
                    icon = BitmapDescriptorFactory.defaultMarker(
                        if (report.status == ReportStatus.pending) BitmapDescriptorFactory.HUE_RED 
                        else BitmapDescriptorFactory.HUE_GREEN
                    )
                )
            }
        }
        
        Row(modifier = Modifier.padding(16.dp).align(Alignment.TopCenter)) {
            StatusChip(label = "Pending: ${reports.count { it.status == ReportStatus.pending }}", color = Color.Red)
            Spacer(modifier = Modifier.width(8.dp))
            StatusChip(label = "Cleaned: ${reports.count { it.status == ReportStatus.cleaned }}", color = Color.Green)
        }
    }
}

@Composable
fun StatusChip(label: String, color: Color) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(firebaseManager: FirebaseManager, onReportSubmitted: () -> Unit) {
    var selectedType by remember { mutableStateOf<WasteType?>(null) }
    var description by remember { mutableStateOf("") }
    var photoBase64 by remember { mutableStateOf("") }
    var reportLocation by remember { mutableStateOf<Location?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    photoBase64 = bitmapToBase64(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    loc?.let { reportLocation = Location(it.latitude, it.longitude) }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        item {
            Text("Report Waste Blackspot", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF064E3B))
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Waste Type", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WasteType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(type.color).copy(alpha = 0.2f))
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Location Details") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.LightGray.copy(alpha = 0.3f))
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (photoBase64.isNotEmpty()) {
                    val bitmap = base64ToBitmap(photoBase64)
                    Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Text("Add Photo", color = Color.Gray)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        isSubmitting = true
                        firebaseManager.submitReport(
                            selectedType?.value ?: "other",
                            description,
                            "data:image/jpeg;base64,$photoBase64",
                            reportLocation ?: Location(20.5937, 78.9629)
                        )
                        isSubmitting = false
                        onReportSubmitted()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = selectedType != null && !isSubmitting,
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Submit Report", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProfileScreen(profile: UserProfile?, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = profile?.photoURL,
            contentDescription = null,
            modifier = Modifier.size(100.dp).clip(CircleShape).background(Color.LightGray),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(profile?.displayName ?: "Anonymous", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(profile?.email ?: "", color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Eco-Karma Points", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                    Text("${profile?.ecoKarmaPoints ?: 0} ✨", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF059669))
                }
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFF10B981))
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2), contentColor = Color.Red),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout", fontWeight = FontWeight.Bold)
        }
    }
}

fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
}

fun base64ToBitmap(base64: String): Bitmap {
    val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
}
