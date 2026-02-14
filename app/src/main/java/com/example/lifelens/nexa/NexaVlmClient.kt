package com.example.lifelens.nexa

import android.content.Context
import android.util.Log
import com.nexa.sdk.NexaSdk
import com.nexa.sdk.VlmWrapper
import com.nexa.sdk.bean.GenerationConfig
import com.nexa.sdk.bean.LlmStreamResult
import com.nexa.sdk.bean.ModelConfig
import com.nexa.sdk.bean.VlmCreateInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File

class NexaVlmClient(
    private val context: Context,
    // 必须是 files-1-1.nexa 的绝对路径
    private val modelPath: String
) {

    private var vlmWrapper: VlmWrapper? = null

    suspend fun init() = withContext(Dispatchers.IO) {

        // 1️⃣ 初始化 SDK（官网 demo）
        NexaSdk.getInstance().init(context)

        // 2️⃣ 校验入口文件存在
        val entryFile = File(modelPath)
        require(entryFile.exists() && entryFile.isFile) {
            "Model entry file not found: ${entryFile.absolutePath}"
        }

        Log.d(TAG, "Loading model from: ${entryFile.absolutePath}")

        // 3️⃣ 完全按照官网 demo 创建
        val input = VlmCreateInput(
            model_name = "omni-neural",
            model_path = entryFile.absolutePath,
            config = ModelConfig(
                max_tokens = 2048,
                enable_thinking = false
            ),
            plugin_id = "npu"
        )

        vlmWrapper = VlmWrapper.builder()
            .vlmCreateInput(input)
            .build()
            .getOrThrow()

        Log.d(TAG, "Model loaded successfully (NPU)")
    }

    suspend fun generate(prompt: String): String =
        withContext(Dispatchers.IO) {

            val wrapper = vlmWrapper
                ?: error("Model not initialized. Call init() first.")

            val sb = StringBuilder()

            wrapper.generateStreamFlow(
                prompt,
                GenerationConfig()
            ).collect { result ->
                when (result) {
                    is LlmStreamResult.Token -> sb.append(result.text)
                    is LlmStreamResult.Completed -> { }
                    is LlmStreamResult.Error ->
                        throw RuntimeException("Generation error: $result")
                }
            }

            sb.toString()
        }

    suspend fun destroy() = withContext(Dispatchers.IO) {
        try { vlmWrapper?.stopStream() } catch (_: Throwable) {}
        try { vlmWrapper?.destroy() } catch (_: Throwable) {}
        vlmWrapper = null
    }

    companion object {
        private const val TAG = "NexaVlmClient"
    }
}
