package com.example.ui

import android.net.Uri

/** Links that open a shared project in the app (Android App Links served by Firebase Hosting). */
object ShareLinks {
    private const val HOST = "check-app-lists.web.app"

    fun projectLink(projectId: String): String = "https://$HOST/p/$projectId"

    /**
     * Project id from either link form: `https://<host>/p/<id>` or the fallback
     * `checkapp://open?project=<id>`. Null for anything else.
     */
    fun projectIdFrom(uri: Uri?): String? {
        if (uri == null) return null
        val id = when (uri.scheme) {
            "https" -> {
                if (uri.host != HOST) return null
                val segments = uri.pathSegments
                if (segments.size >= 2 && segments[0] == "p") segments[1] else null
            }
            "checkapp" -> uri.getQueryParameter("project")
            else -> null
        }
        return id?.trim()?.ifBlank { null }
    }
}
