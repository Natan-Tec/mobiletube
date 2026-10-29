package com.natantec.mobiletube.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Histórico de vídeos assistidos. Cada vídeo pode ser marcado ou desmarcado
 * à mão, e o histórico inteiro pode ser pausado (nada novo entra).
 */
object HistoryStore {
    private const val MAX = 500
    private val sp get() = appContext.getSharedPreferences("mobiletube_history", Context.MODE_PRIVATE)
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    var paused: Boolean
        get() = sp.getBoolean("paused", false)
        set(v) = sp.edit().putBoolean("paused", v).apply()

    fun all(): List<VideoItem> {
        val arr = JSONArray(sp.getString("items", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VideoItem(
                id = o.getString("id"),
                url = o.getString("url"),
                title = o.optString("title"),
                channel = o.optString("channel"),
                views = o.optLong("views", -1),
                durationSec = o.optLong("dur", 0),
                thumb = o.optString("thumb").ifEmpty { null },
                uploaded = o.optString("up").ifEmpty { null }
            )
        }
    }

    fun contains(id: String) = all().any { it.id == id }

    /** Chamado ao abrir um vídeo: só salva se o histórico não estiver pausado. */
    fun recordIfEnabled(item: VideoItem) {
        if (!paused) mark(item)
    }

    /** Marca como assistido (vai para o topo). */
    fun mark(item: VideoItem) {
        val list = all().filter { it.id != item.id }.toMutableList()
        list.add(0, item)
        save(list.take(MAX))
    }

    fun unmark(id: String) = save(all().filter { it.id != id })

    fun clear() = save(emptyList())

    private fun save(list: List<VideoItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("url", it.url).put("title", it.title)
                    .put("channel", it.channel).put("views", it.views)
                    .put("dur", it.durationSec).put("thumb", it.thumb ?: "")
                    .put("up", it.uploaded ?: "")
            )
        }
        sp.edit().putString("items", arr.toString()).apply()
    }
}
