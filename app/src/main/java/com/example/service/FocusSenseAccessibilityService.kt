package com.example.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.FocusSenseApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FocusSenseAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastExtractedText = ""
    private var lastAnalyzedTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceRunning.value = true
        Log.i(TAG, "FocusSense Accessibility Sentinel Connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: return
        // Ignore system UI and own app
        if (pkgName == packageName || pkgName.contains("launcher") || pkgName.contains("systemui")) {
            return
        }

        val app = application as? FocusSenseApplication ?: return
        val currentChildId = app.repository.selectedChildId.value

        // 1. Check if package is restricted by active schedule rule right now
        scope.launch {
            val restriction = app.repository.checkAppRestriction(currentChildId, pkgName)
            if (restriction.isBlocked) {
                _lastBlockedApp.value = restriction.restrictedAppName
            }
        }

        // 2. Real-time contextual text extraction
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedTime < 2500) {
            return // Debounce rapid stream
        }

        val rootNode = rootInActiveWindow ?: return
        val extractedStrings = mutableListOf<String>()
        traverseNodes(rootNode, extractedStrings)

        val fullText = extractedStrings.joinToString(" ")
        if (fullText.length > 15 && fullText != lastExtractedText) {
            lastExtractedText = fullText
            lastAnalyzedTime = now
            val truncatedSample = if (fullText.length > 250) fullText.take(250) + "..." else fullText

            _lastScrapedContent.value = ScrapedContentSample(
                packageName = pkgName,
                title = event.className?.toString() ?: "Active Window",
                text = truncatedSample,
                timestamp = now
            )

            scope.launch {
                val appLabel = getAppLabel(pkgName)
                app.repository.processExtractedContent(
                    childId = currentChildId,
                    packageName = pkgName,
                    appName = appLabel,
                    contentTitle = "Extracted from $appLabel",
                    extractedText = fullText
                )
            }
        }
    }

    private fun traverseNodes(node: AccessibilityNodeInfo?, output: MutableList<String>) {
        if (node == null) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()

        if (!text.isNullOrBlank() && text.length > 2) {
            output.add(text)
        } else if (!desc.isNullOrBlank() && desc.length > 2) {
            output.add(desc)
        }

        for (i in 0 until node.childCount) {
            traverseNodes(node.getChild(i), output)
        }
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            when {
                pkg.contains("youtube") -> "YouTube"
                pkg.contains("chrome") -> "Chrome"
                pkg.contains("discord") -> "Discord"
                pkg.contains("instagram") -> "Instagram"
                pkg.contains("tiktok") || pkg.contains("musically") -> "TikTok"
                pkg.contains("roblox") -> "Roblox"
                else -> pkg.substringAfterLast('.')
            }
        }
    }

    override fun onInterrupt() {
        _isServiceRunning.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
    }

    companion object {
        private const val TAG = "FocusSenseSentinel"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _lastScrapedContent = MutableStateFlow<ScrapedContentSample?>(null)
        val lastScrapedContent = _lastScrapedContent.asStateFlow()

        private val _lastBlockedApp = MutableStateFlow<String?>(null)
        val lastBlockedApp = _lastBlockedApp.asStateFlow()
    }
}

data class ScrapedContentSample(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)
