package com.example.lifelens.tool

interface VisionClient {
    suspend fun explain(imagePath: String, prompt: String): String
}
