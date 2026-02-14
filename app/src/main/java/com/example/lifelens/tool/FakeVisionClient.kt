package com.example.lifelens.nexa

import kotlinx.coroutines.delay
import kotlin.random.Random

class FakeVisionClient {

    data class FakeResult(
        val rawJson: String,
        val actions: List<String>
    )

    suspend fun analyze(): FakeResult {
        // 模拟“模型在跑”的感觉
        delay(10)

        val samples = listOf(
            FakeResult(
                rawJson = """{"label":"microwave","category":"appliance","confidence":0.86,"hazards":["hot surface"]}""",
                actions = listOf("Show safety tips for hot surfaces", "Open appliance manual tutorial")
            ),
            FakeResult(
                rawJson = """{"label":"staircase","category":"building","confidence":0.80,"hazards":["fall risk"]}""",
                actions = listOf("Explain how to use handrail", "Suggest safer path / elevator")
            ),
            FakeResult(
                rawJson = """{"label":"houseplant","category":"plant","confidence":0.78,"hazards":[]}""",
                actions = listOf("Identify plant care basics", "Check if toxic to pets")
            )
        )

        return samples[Random.nextInt(samples.size)]
    }
}
