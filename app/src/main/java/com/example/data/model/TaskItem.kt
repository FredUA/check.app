package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Subtask(
    val id: String = "",
    val title: String = "",
    @get:PropertyName("isCompleted") @set:PropertyName("isCompleted") var isCompleted: Boolean = false
)

@IgnoreExtraProperties
data class TaskItem(
    @DocumentId val id: String = "",
    val listId: String = "",
    val projectId: String = "",
    val ownerId: String = "",
    val title: String = "",
    val notes: String = "",
    @get:PropertyName("isCompleted") @set:PropertyName("isCompleted") var isCompleted: Boolean = false,
    val completedAt: Timestamp? = null,
    val subtasks: List<Map<String, Any>> = emptyList(),
    val memberEmails: List<String> = emptyList(),
    val lastUpdatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parseSubtasks(): List<Subtask> {
        return subtasks.mapNotNull { map ->
            val subId = map["id"] as? String ?: return@mapNotNull null
            val subTitle = map["title"] as? String ?: ""
            val subCompleted = (map["isCompleted"] as? Boolean)
                ?: (map["completed"] as? Boolean)
                ?: false
            Subtask(id = subId, title = subTitle, isCompleted = subCompleted)
        }
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "listId" to listId,
            "projectId" to projectId,
            "ownerId" to ownerId,
            "title" to title,
            "notes" to notes,
            "isCompleted" to isCompleted,
            "completed" to isCompleted,
            "completedAt" to completedAt,
            "subtasks" to subtasks,
            "memberEmails" to memberEmails,
            "lastUpdatedBy" to lastUpdatedBy,
            "createdAt" to (createdAt ?: Timestamp.now()),
            "updatedAt" to Timestamp.now()
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(key: String, map: Map<String, Any?>, fallbackListId: String, fallbackProjectId: String): TaskItem {
            val id = (map["id"] as? String)?.ifBlank { null } ?: key
            val listId = (map["listId"] as? String)?.ifBlank { null } ?: fallbackListId
            val projectId = (map["projectId"] as? String)?.ifBlank { null } ?: fallbackProjectId
            val ownerId = map["ownerId"] as? String ?: ""
            val title = (map["title"] as? String) ?: (map["name"] as? String) ?: ""
            val notes = map["notes"] as? String ?: ""
            val isCompleted = (map["isCompleted"] as? Boolean) ?: (map["completed"] as? Boolean) ?: false
            val completedAt = map["completedAt"] as? Timestamp
            val subtasks = (map["subtasks"] as? List<Map<String, Any>>) ?: emptyList()
            val memberEmails = (map["memberEmails"] as? List<String>) ?: emptyList()
            val lastUpdatedBy = map["lastUpdatedBy"] as? String ?: ""
            val createdAt = map["createdAt"] as? Timestamp
            val updatedAt = map["updatedAt"] as? Timestamp
            return TaskItem(
                id = id,
                listId = listId,
                projectId = projectId,
                ownerId = ownerId,
                title = title,
                notes = notes,
                isCompleted = isCompleted,
                completedAt = completedAt,
                subtasks = subtasks,
                memberEmails = memberEmails,
                lastUpdatedBy = lastUpdatedBy,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}
