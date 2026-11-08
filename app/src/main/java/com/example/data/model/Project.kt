package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Project(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerEmail: String = "",
    val name: String = "",
    val title: String = "",
    val description: String = "",
    val colorHex: String = "#FF2A85",
    val memberIds: List<String> = emptyList(),
    val memberEmails: List<String> = emptyList(),
    val sharedWith: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val displayTitle: String get() = name.ifBlank { title }
}
