package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class YouTubeChannelSearchResult(
    val channelId: String,
    val title: String,
    val handle: String,
    val avatarUrl: String,
    val subscriberCount: String,
    val description: String,
    val channelUrl: String
)

object YouTubeSearchService {
    private const val TAG = "YouTubeSearchService"
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()

    suspend fun searchChannels(query: String): List<YouTubeChannelSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
            val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery&sp=EgIQAg%253D%253D"
            val request = Request.Builder().url(searchUrl).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string().orEmpty()
            return@withContext parseChannelsFromHtml(html, query)
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
            return@withContext emptyList()
        }
    }

    private fun parseChannelsFromHtml(html: String, query: String): List<YouTubeChannelSearchResult> {
        // (Simplified parsing logic or implementation for demo)
        return emptyList() // You can keep the parsing logic from the previous file if needed
    }
}
