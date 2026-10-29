package com.natantec.mobiletube.data

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

data class SkipSegment(val startMs: Long, val endMs: Long, val category: String) {
    val label: String
        get() = when (category) {
            "sponsor" -> "Patrocínio"
            "selfpromo" -> "Autopromoção"
            "interaction" -> "Pedido de interação"
            else -> "Trecho"
        }
}

/** Busca trechos de patrocínio na API pública do SponsorBlock (sponsor.ajay.app). */
object SponsorBlock {
    suspend fun segments(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        try {
            val categories = Uri.encode("[\"sponsor\",\"selfpromo\",\"interaction\"]")
            val actions = Uri.encode("[\"skip\"]")
            val url = "https://sponsor.ajay.app/api/skipSegments" +
                "?videoID=$videoId&categories=$categories&actionTypes=$actions"
            val request = Request.Builder().url(url).header("User-Agent", "Mobiletube/0.1").build()
            HttpClients.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val arr = JSONArray(resp.body?.string() ?: "[]")
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.getJSONObject(i)
                    val seg = obj.optJSONArray("segment") ?: return@mapNotNull null
                    SkipSegment(
                        startMs = (seg.getDouble(0) * 1000).toLong(),
                        endMs = (seg.getDouble(1) * 1000).toLong(),
                        category = obj.optString("category")
                    )
                }.sortedBy { it.startMs }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
}
