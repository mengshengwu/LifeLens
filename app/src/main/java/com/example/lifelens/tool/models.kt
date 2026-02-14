package com.example.lifelens.tool

import kotlinx.serialization.Serializable

@Serializable
data class VisionDetectResult(
    val label: String,
    val category: String,        // "appliance" | "plant" | "building" | "other"
    val confidence: Double = 0.5,
    val hazards: List<String> = emptyList()
)

enum class Audience { ELDERLY, CHILD }

data class ToolContext(
    val audience: Audience,
    val locale: String = "en-US",
    val gpsLat: Double? = null,
    val gpsLon: Double? = null
)

@Serializable
data class ActionItem(
    val id: String,              // "show_steps" | "open_video" | "nearby_info"
    val title: String,           // what user sees
    val payload: String? = null  // e.g. label or url
)

data class ToolBundle(
    val knowledge: String,
    val safety: String,
    val actions: List<ActionItem>
)
