package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TaskList(
    @DocumentId val id: String = "",
    val projectId: String = "",
    val ownerId: String = "",
    val name: String = "",
    val title: String = "",
    val colorHex: String = "#00D2FF",
    val memberEmails: List<String> = emptyList(),
    val tasks: Map<String, Any?> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
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
