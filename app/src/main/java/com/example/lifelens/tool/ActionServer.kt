package com.example.lifelens.tool

import android.content.Context
import android.content.Intent
import android.net.Uri

class ActionServer(private val context: Context) {

    fun suggestedActions(label: String, category: String, audience: Audience): List<ActionItem> {
        val actions = mutableListOf<ActionItem>()

        actions += ActionItem(
            id = "show_steps",
            title = "Show step-by-step instructions",
            payload = label
        )

        actions += ActionItem(
            id = "open_video",
            title = "Open a short tutorial video",
            payload = label
        )

        if (category == "building") {
            actions += ActionItem(
                id = "nearby_info",
                title = "Show nearby info (requires GPS)",
                payload = label
            )
        }

        return actions
    }

    fun openTutorialVideo(label: String) {
        val q = Uri.encode("$label how to use")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
