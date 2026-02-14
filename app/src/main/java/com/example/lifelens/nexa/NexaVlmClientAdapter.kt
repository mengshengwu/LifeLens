package com.example.lifelens.nexa

import com.example.lifelens.tool.VisionClient

class NexaVlmClientAdapter(
    private val client: NexaVlmClient
) : VisionClient {

    override suspend fun explain(imagePath: String, prompt: String): String {
        // demo-only: official quickstart uses text generation first
        // imagePath is ignored here; we just run a text prompt.
        return client.generate(prompt)
    }
}
