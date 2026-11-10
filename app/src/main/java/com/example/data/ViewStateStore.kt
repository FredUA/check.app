package com.example.data

import android.content.Context

/**
 * Remembers, per signed-in user and only on this device, which project/list was open,
 * so the app comes back where the user left it.
 */
class ViewStateStore(context: Context, userId: String) {

    private val prefs = context.applicationContext
        .getSharedPreferences("view_state_$userId", Context.MODE_PRIVATE)

    data class Selection(val projectId: String?, val listId: String?)

    fun selection(): Selection =
        Selection(prefs.getString(KEY_PROJECT, null), prefs.getString(KEY_LIST, null))

    fun saveSelection(projectId: String?, listId: String?) {
        prefs.edit()
            .putNullable(KEY_PROJECT, projectId)
            .putNullable(KEY_LIST, listId)
            .apply()
    }

    private fun android.content.SharedPreferences.Editor.putNullable(key: String, value: String?) =
        if (value == null) remove(key) else putString(key, value)

    private companion object {
        const val KEY_PROJECT = "selected_project"
        const val KEY_LIST = "selected_list"
    }
}
