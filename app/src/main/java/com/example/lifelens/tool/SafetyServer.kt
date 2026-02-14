package com.example.lifelens.tool

class SafetyServer {

    fun assess(label: String, category: String, audience: Audience): String {
        val l = label.lowercase()

        val warnings = mutableListOf<String>()
        if (listOf("knife", "scissors", "blade").any { it in l }) warnings += "Sharp object: keep fingers away."
        if (listOf("stove", "kettle", "microwave", "oven").any { it in l }) warnings += "Hot surface risk: avoid touching metal parts."
        if (listOf("pill", "medicine", "tablet", "capsule").any { it in l }) warnings += "Medication: follow label and consult an adult/doctor."

        if (warnings.isEmpty()) {
            return when (audience) {
                Audience.ELDERLY -> "No obvious safety warnings detected."
                Audience.CHILD -> "Looks safe, but ask an adult if unsure."
            }
        }

        return when (audience) {
            Audience.ELDERLY -> "Safety: " + warnings.joinToString(" ")
            Audience.CHILD -> "Be careful: " + warnings.joinToString(" ")
        }
    }
}
