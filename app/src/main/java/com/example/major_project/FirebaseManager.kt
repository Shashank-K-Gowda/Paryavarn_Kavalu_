package com.example.major_project

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FirebaseManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow(auth.currentUser)
    val authState: StateFlow<FirebaseUser?> = _authState

    init {
        auth.addAuthStateListener { _authState.value = it.currentUser }
    }

    suspend fun loginAndSetRole(role: UserRole): Result<Unit> {
        return try {
            Log.d("FirebaseManager", "Login started for role: $role")
            val user = auth.currentUser ?: auth.signInAnonymously().await().user 
                ?: throw Exception("Firebase Authentication failed")
            
            val profile = mapOf(
                "uid" to user.uid,
                "role" to role.name,
                "displayName" to (if (role == UserRole.cleaner) "Cleaner" else "Citizen")
            )
            
            db.collection("users").document(user.uid).set(profile, SetOptions.merge()).await()
            Log.d("FirebaseManager", "Login successful for UID: ${user.uid}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Login Error", e)
            Result.failure(e)
        }
    }

    fun getProfile(onUpdate: (UserProfile?) -> Unit) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).addSnapshotListener { s, e ->
            if (e != null) {
                Log.e("FirebaseManager", "Profile listener error", e)
                return@addSnapshotListener
            }
            if (s != null && s.exists()) {
                try { 
                    onUpdate(s.toObject<UserProfile>()) 
                } catch (ex: Exception) { 
                    Log.e("FirebaseManager", "Error parsing profile", ex)
                }
            }
        }
    }

    suspend fun submitReport(type: String, desc: String, photo: String, loc: Location): Result<Unit> {
        return try {
            Log.d("FirebaseManager", "submitReport: Starting submission. Photo length: ${photo.length}")
            val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated. Please log in."))
            
            // Firestore documents have a 1MB limit. 
            if (photo.length > 900000) {
                return Result.failure(Exception("Image is too large for Firestore. Please use a lower resolution photo."))
            }

            val report = mapOf(
                "reporterUid" to user.uid,
                "wasteType" to type.lowercase(),
                "description" to desc,
                "photoUrl" to photo,
                "location" to mapOf("lat" to loc.lat, "lng" to loc.lng),
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp()
            )
            
            val batch = db.batch()
            val reportRef = db.collection("reports").document()
            val userRef = db.collection("users").document(user.uid)
            
            batch.set(reportRef, report)
            batch.set(userRef, mapOf("ecoKarmaPoints" to FieldValue.increment(10L)), SetOptions.merge())
            
            Log.d("FirebaseManager", "submitReport: Committing batch...")
            batch.commit().await()
            Log.d("FirebaseManager", "submitReport: Batch commit successful.")
            
            Result.success(Unit)
        } catch (e: Exception) { 
            Log.e("FirebaseManager", "Submit Report Error", e)
            Result.failure(e) 
        }
    }

    fun getReports(onUpdate: (List<WasteReport>) -> Unit) {
        Log.d("FirebaseManager", "getReports: Starting listener...")
        db.collection("reports").addSnapshotListener { s, e ->
            if (e != null) {
                Log.e("FirebaseManager", "Reports listener error", e)
                return@addSnapshotListener
            }
            
            if (s == null) {
                Log.d("FirebaseManager", "Snapshot is null")
                onUpdate(emptyList())
                return@addSnapshotListener
            }

            Log.d("FirebaseManager", "Received ${s.size()} documents from 'reports' collection.")

            val list = s.documents.mapNotNull { doc ->
                try {
                    // ROBUST PARSING: Handles both Map and GeoPoint location formats
                    val locationData = doc.get("location")
                    val parsedLocation = when (locationData) {
                        is Map<*, *> -> Location(
                            (locationData["lat"] as? Number)?.toDouble() ?: 0.0,
                            (locationData["lng"] as? Number)?.toDouble() ?: 0.0
                        )
                        is GeoPoint -> Location(locationData.latitude, locationData.longitude)
                        else -> {
                            Log.w("FirebaseManager", "Unknown location format for doc ${doc.id}")
                            Location()
                        }
                    }

                    WasteReport(
                        id = doc.id,
                        reporterUid = doc.getString("reporterUid") ?: "",
                        wasteType = doc.getString("wasteType") ?: "",
                        description = doc.getString("description") ?: "",
                        photoUrl = doc.getString("photoUrl") ?: "",
                        location = parsedLocation,
                        status = try { 
                            val statusStr = doc.getString("status") ?: "pending"
                            if (statusStr.lowercase() == "cleaned") ReportStatus.cleaned else ReportStatus.pending
                        } catch (ex: Exception) { ReportStatus.pending },
                        createdAt = doc.getTimestamp("createdAt"),
                        cleanedAt = doc.getTimestamp("cleanedAt"),
                        cleanedByUid = doc.getString("cleanedByUid") ?: ""
                    )
                } catch (ex: Exception) {
                    Log.e("FirebaseManager", "CRITICAL: Error parsing report doc ${doc.id}", ex)
                    null 
                }
            }
            
            Log.d("FirebaseManager", "Successfully parsed ${list.size} reports.")
            onUpdate(list)
        }
    }

    suspend fun markAsCleaned(report: WasteReport): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
            if (report.id.isEmpty()) return Result.failure(Exception("Invalid report reference"))

            val batch = db.batch()
            val reportRef = db.collection("reports").document(report.id)
            val userRef = db.collection("users").document(user.uid)

            batch.update(reportRef, mapOf(
                "status" to "cleaned", 
                "cleanedByUid" to user.uid,
                "cleanedAt" to FieldValue.serverTimestamp()
            ))
            batch.set(userRef, mapOf("ecoKarmaPoints" to FieldValue.increment(50L)), SetOptions.merge())
            
            Log.d("FirebaseManager", "markAsCleaned: Committing batch...")
            batch.commit().await()
            Log.d("FirebaseManager", "markAsCleaned: Success!")
            Result.success(Unit)
        } catch (e: Exception) { 
            Log.e("FirebaseManager", "Mark Cleaned Error", e)
            Result.failure(e) 
        }
    }

    fun logout() = auth.signOut()
}
