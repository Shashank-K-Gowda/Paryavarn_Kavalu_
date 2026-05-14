package com.example.major_project

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

enum class UserRole {
    @PropertyName("citizen") citizen,
    @PropertyName("cleaner") cleaner,
    @PropertyName("volunteer") volunteer
}

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoURL: String = "",
    val ecoKarmaPoints: Long = 0L,
    val role: UserRole = UserRole.citizen,
    val createdAt: Timestamp? = null
)

enum class WasteType(val value: String, val label: String, val color: Long) {
    PLASTIC("plastic", "Plastic Waste", 0xFF3B82F6),
    ORGANIC("organic", "Organic/Food", 0xFF10B981),
    ELECTRONIC("electronic", "E-Waste", 0xFFF59E0B),
    CONSTRUCTION("construction", "Debris/Construction", 0xFF6B7280),
    INDUSTRIAL("industrial", "Industrial", 0xFFEF4444),
    OTHER("other", "Other", 0xFF8B5CF6);

    companion object {
        fun fromString(value: String): WasteType = entries.find { it.value == value } ?: OTHER
    }
}

data class Location(
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

enum class ReportStatus {
    @PropertyName("pending") pending,
    @PropertyName("cleaned") cleaned
}

data class WasteReport(
    val id: String = "",
    val reporterUid: String = "",
    val wasteType: String = "",
    val description: String = "",
    val photoUrl: String = "",
    val location: Location = Location(),
    val status: ReportStatus = ReportStatus.pending,
    val createdAt: Timestamp? = null,
    val cleanedAt: Timestamp? = null,
    val cleanedByUid: String = ""
)
