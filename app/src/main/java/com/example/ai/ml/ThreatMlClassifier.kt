package com.example.ai.ml

/** Result of running the model over one piece of text. */
class MlInference(
    /** Per-label probability, max-pooled over all windows. Same order as config.labels. */
    val probs: FloatArray,
    val windowCount: Int,
    /** Measured wall-clock time of the model runs (tokenisation excluded). */
    val latencyMs: Double,
)

/** Abstraction so the engine can be unit-tested with a fake and so the runtime can be swapped. */
interface ThreatMlClassifier {
    val config: ThreatMlConfig

    /** Blocking. Throws if the model cannot run; the engine then falls back to heuristics. */
    fun classify(text: String): MlInference
}
