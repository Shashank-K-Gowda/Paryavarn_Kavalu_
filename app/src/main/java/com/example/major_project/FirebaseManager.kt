package com.example.major_project

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.tasks.await

class FirebaseManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val currentUser get() = auth.currentUser

    suspend fun ensureProfile(preferredRole: UserRole) {
        val user = currentUser ?: return
        val profileRef = db.collection("users").document(user.uid)
        
        try {
            val snapshot = profileRef.get().await()
            if (!snapshot.exists()) {
                val profile = UserProfile(
                    uid = user.uid,
                    email = user.email ?: "",
                    displayName = user.displayName ?: "Anonymous",
                    photoURL = user.photoUrl?.toString() ?: "",
                    ecoKarmaPoints = 0,
                    role = preferredRole
                )
                profileRef.set(profile).await()
            } else {
                val currentRole = snapshot.getString("role")
                if (currentRole != preferredRole.name) {
                    profileRef.update("role", preferredRole.name).await()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getProfile(onUpdate: (UserProfile?) -> Unit) {
        val user = currentUser ?: return
        db.collection("users").document(user.uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                onUpdate(snapshot?.toObject<UserProfile>())
            }
    }

    suspend fun submitReport(
        wasteType: String,
        description: String,
        photoUrl: String,
        location: Location
    ) {
        val user = currentUser ?: return
        val report = hashMapOf(
            "reporterUid" to user.uid,
            "wasteType" to wasteType,
            "description" to description,
            "photoUrl" to photoUrl,
            "location" to location,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("reports").add(report).await()
        
        // Award points
        db.collection("users").document(user.uid)
            .update("ecoKarmaPoints", FieldValue.increment(10))
            .await()
    }

    fun getReports(onUpdate: (List<WasteReport>) -> Unit) {
        db.collection("reports")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val reports = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject<WasteReport>()?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(reports)
            }
    }

    suspend fun markAsCleaned(report: WasteReport, cleanerId: String) {
        val batch = db.batch()
        
        val reportRef = db.collection("reports").document(report.id)
        val cleanerRef = db.collection("users").document(cleanerId)
        val reporterRef = db.collection("users").document(report.reporterUid)
        val notificationRef = db.collection("notifications").document()

        batch.update(reportRef, mapOf(
            "status" to "cleaned",
            "cleanedAt" to FieldValue.serverTimestamp(),
            "cleanedByUid" to cleanerId
        ))

        batch.update(cleanerRef, "ecoKarmaPoints", FieldValue.increment(50))
        batch.update(reporterRef, "ecoKarmaPoints", FieldValue.increment(10))

        batch.set(notificationRef, mapOf(
            "userId" to report.reporterUid,
            "title" to "Spot Cleaned! ✨",
            "message" to "Your reported ${report.wasteType} spot has been cleaned. You earned 10 bonus points!",
            "type" to "cleaned",
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp()
        ))

        batch.commit().await()
    }
}
