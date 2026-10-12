package com.example.ai.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.LongBuffer
import java.security.MessageDigest

/**
 * Runs the fine-tuned MobileBERT multi-label threat classifier on-device with ONNX Runtime.
 * Nothing leaves the phone: tokenisation, inference and thresholding are all local.
 *
 * Assets (produced by ml/export_onnx.py): threat_mobilebert.onnx, threat_vocab.txt, threat_labels.json.
 * If any are missing, [tryCreate] returns null and the engine uses its keyword fallback.
 */
class OnnxMobileBertClassifier private constructor(
    override val config: ThreatMlConfig,
    private val tokenizer: WordPieceTokenizer,
    private val env: OrtEnvironment,
    private val session: OrtSession,
) : ThreatMlClassifier, AutoCloseable {

    override fun classify(text: String): MlInference {
        val windows = tokenizer.encodeWindows(text, config.maxSeqLen, config.windowStride, config.maxWindows)
        val t0 = System.nanoTime()
        val perWindow = windows.map { runWindow(it) }
        val latencyMs = (System.nanoTime() - t0) / 1_000_000.0
        return MlInference(ThreatMlPolicy.aggregateMax(perWindow), windows.size, latencyMs)
    }

    private fun runWindow(w: EncodedWindow): FloatArray {
        val shape = longArrayOf(1, w.inputIds.size.toLong())
        val tensors = LinkedHashMap<String, OnnxTensor>()
        try {
            for (name in session.inputNames) {
                val data = when (name) {
                    "input_ids" -> w.inputIds
                    "attention_mask" -> w.attentionMask
                    "token_type_ids" -> w.tokenTypeIds
                    else -> throw IllegalStateException("Unexpected model input '$name'")
                }
                tensors[name] = OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape)
            }
            session.run(tensors).use { result ->
                @Suppress("UNCHECKED_CAST")
                val logits = (result[0].value as Array<FloatArray>)[0]
                check(logits.size == config.labels.size) {
                    "model has ${logits.size} outputs but config has ${config.labels.size} labels"
                }
                return FloatArray(logits.size) { ThreatMlPolicy.sigmoid(logits[it]) }
            }
        } finally {
            tensors.values.forEach { it.close() }
        }
    }

    override fun close() {
        session.close()
    }

    companion object {
        private const val TAG = "OnnxMobileBert"
        const val CONFIG_ASSET = "threat_labels.json"

        /** Returns null (never throws) when assets are absent or the runtime cannot start. */
        fun tryCreate(context: Context): OnnxMobileBertClassifier? = try {
            create(context)
        } catch (t: Throwable) {
            Log.w(TAG, "MobileBERT unavailable, using keyword fallback: ${t.message}", t)
            null
        }

        private fun create(context: Context): OnnxMobileBertClassifier? {
            val assets = context.assets
            val present = assets.list("")?.toSet() ?: emptySet()
            if (CONFIG_ASSET !in present) {
                Log.i(TAG, "No $CONFIG_ASSET in assets; model not installed")
                return null
            }
            val config = assets.open(CONFIG_ASSET).bufferedReader().use { ThreatMlConfigParser.parse(it.readText()) }
            if (config.modelFile !in present || config.vocabFile !in present) {
                Log.w(TAG, "Model or vocab asset missing (${config.modelFile}, ${config.vocabFile})")
                return null
            }
            val tokenizer = assets.open(config.vocabFile).bufferedReader().use {
                WordPieceTokenizer.fromVocabLines(it.readLines())
            }
            val modelPath = materializeModel(context, config)
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val session = env.createSession(modelPath.absolutePath, options)
            Log.i(TAG, "Loaded ${config.modelFile} with ${config.labels.size} labels, inputs=${session.inputNames}")
            return OnnxMobileBertClassifier(config, tokenizer, env, session)
        }

        /**
         * ONNX Runtime wants a file path (a 25-100 MB model would otherwise sit on the Java heap).
         * Copy the asset once per model version into app-private storage and verify its SHA-256.
         */
        private fun materializeModel(context: Context, config: ThreatMlConfig): File {
            val dir = File(context.filesDir, "ml").apply { mkdirs() }
            val dest = File(dir, "threat_mobilebert.onnx")
            val marker = File(dir, "threat_mobilebert.version")
            val version = config.modelSha256.ifBlank {
                context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
            }
            if (dest.exists() && marker.exists() && marker.readText() == version) return dest

            val tmp = File(dir, "threat_mobilebert.onnx.tmp")
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open(config.modelFile).use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        output.write(buf, 0, n)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (config.modelSha256.isNotBlank() && !actual.equals(config.modelSha256, ignoreCase = true)) {
                tmp.delete()
                throw IllegalStateException("Model checksum mismatch (expected ${config.modelSha256}, got $actual)")
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            marker.writeText(version)
            return dest
        }
    }
}
