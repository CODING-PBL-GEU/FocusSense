package com.example.ai.ml

/** How dangerous a label is if it fires. Drives severity and who gets alerted. */
enum class Criticality(val rank: Int) {
    CRITICAL(2), HIGH(1), MODERATE(0);

    companion object {
        fun parse(value: String): Criticality =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HIGH
    }
}

/** One output unit of the classifier (multi-label: each label has its own sigmoid + threshold). */
data class LabelSpec(
    val id: String,
    val displayName: String,
    /** String used across the app/UI (e.g. "Stranger Risk"). */
    val appCategory: String,
    val riskType: String,
    val criticality: Criticality,
    /** Decision threshold tuned on the validation set by ml/train.py. */
    val threshold: Float,
)

/** Mirror of assets/threat_labels.json written by ml/export_onnx.py. */
data class ThreatMlConfig(
    val maxSeqLen: Int,
    val windowStride: Int,
    val maxWindows: Int,
    /** Scores inside (uncertainLow, uncertainHigh) are "unsure" and may escalate to Layer 3. */
    val uncertainLow: Float,
    val uncertainHigh: Float,
    val labels: List<LabelSpec>,
    val modelFile: String,
    val vocabFile: String,
    val modelSha256: String,
)
