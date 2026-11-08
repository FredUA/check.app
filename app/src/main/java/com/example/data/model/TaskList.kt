package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TaskList(
    // No @DocumentId here: list documents store their own "id" field, and Firestore
    // throws when @DocumentId collides with a stored field. The repository sets id from doc.id.
    val id: String = "",
    val projectId: String = "",
    val ownerId: String = "",
    val name: String = "",
    val title: String = "",
    val colorHex: String = "#00D2FF",
    val memberEmails: List<String> = emptyList(),
    val lastUpdatedBy: String = "",
    val tasks: Map<String, Any?> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    // Firestore snapshot metadata, filled in by the repository (never stored in the database).
    // hasPendingWrites: this snapshot includes a change made on this device that the server hasn't confirmed.
    // fromCache: the snapshot came from the local cache and may be stale.
    @get:Exclude val hasPendingWrites: Boolean = false,
    @get:Exclude val fromCache: Boolean = false
) {
    val displayTitle: String get() = name.ifBlank { title }

    @Suppress("UNCHECKED_CAST")
    fun parseTasks(): List<TaskItem> {
        val result = mutableListOf<TaskItem>()
        for ((key, value) in tasks) {
            if (value is Map<*, *>) {
                try {
                    val map = value as Map<String, Any?>
                    val task = TaskItem.fromMap(key, map, id, projectId)
                    result.add(task)
                } catch (_: Exception) {}
            }
        }
        return result
    }
}
