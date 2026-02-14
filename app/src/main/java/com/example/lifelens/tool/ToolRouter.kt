package com.example.lifelens.tool

class ToolRouter(
    private val knowledge: KnowledgeServer,
    private val safety: SafetyServer,
    private val action: ActionServer
) {
    fun run(detect: VisionDetectResult, ctx: ToolContext): ToolBundle {
        // 1) Safety first (gating later)
        val s = safety.assess(detect.label, detect.category, ctx.audience)

        // 2) Knowledge only if somewhat confident (simple heuristic)
        val k = if (detect.confidence >= 0.45) {
            knowledge.lookup(detect.label, ctx.audience)
        } else {
            "Not confident enough to lookup offline knowledge yet."
        }

        // 3) Actions
        val a = action.suggestedActions(detect.label, detect.category, ctx.audience)

        return ToolBundle(knowledge = k, safety = s, actions = a)
    }
}
