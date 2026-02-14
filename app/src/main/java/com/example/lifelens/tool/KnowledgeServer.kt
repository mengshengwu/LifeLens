package com.example.lifelens.tool

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class KnowledgeEntry(
    val label: String,
    val shortElderly: String,
    val shortChild: String
)

class KnowledgeServer(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val entries: List<KnowledgeEntry> by lazy {
        val text = context.assets.open("knowledge_seed.json")
            .bufferedReader()
            .use { it.readText() }

        json.decodeFromString(ListSerializer(KnowledgeEntry.serializer()), text)
    }

    fun lookup(label: String, audience: Audience): String {
        val hit = entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
        if (hit == null) return "No offline knowledge found for \"$label\" yet."

        return when (audience) {
            Audience.ELDERLY -> hit.shortElderly
            Audience.CHILD -> hit.shortChild
        }
    }
}
