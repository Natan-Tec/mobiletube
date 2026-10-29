package com.natantec.mobiletube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.math.abs
import org.schabi.newpipe.extractor.Page as NpPage

/** Uma página de resultados + o necessário para pedir a próxima. */
class FeedPage(
    val items: List<VideoItem>,
    val next: NpPage?,
    val handler: SearchQueryHandler
)

object YoutubeRepository {
    private val service get() = ServiceList.YouTube

    private val idRegex =
        Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})""")

    fun videoId(url: String): String? = idRegex.find(url)?.groupValues?.get(1)

    suspend fun search(query: String): FeedPage = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
            ""
        )
        val info = SearchInfo.getInfo(service, handler)
        FeedPage(map(info.relatedItems), info.nextPage, handler)
    }

    suspend fun more(handler: SearchQueryHandler, page: NpPage): FeedPage =
        withContext(Dispatchers.IO) {
            val result = SearchInfo.getMoreItems(service, handler, page)
            FeedPage(map(result.items), result.nextPage, handler)
        }

    suspend fun streamInfo(url: String): StreamInfo = withContext(Dispatchers.IO) {
        StreamInfo.getInfo(service, url)
    }

    private fun map(items: List<InfoItem>): List<VideoItem> =
        items.filterIsInstance<StreamInfoItem>().mapNotNull { s ->
            val url = s.url ?: return@mapNotNull null
            val id = videoId(url) ?: return@mapNotNull null
            VideoItem(
                id = id,
                url = url,
                title = s.name ?: "",
                channel = s.uploaderName ?: "",
                views = s.viewCount,
                durationSec = s.duration,
                thumb = pickThumb(s.thumbnails),
                uploaded = s.textualUploadDate
            )
        }

    /** Pega a miniatura mais próxima de 360px de altura (leve para celular fraco). */
    private fun pickThumb(list: List<Image>?): String? =
        list?.minByOrNull { abs(it.height - 360) }?.url
}

/** Tela inicial: "Em alta" do YouTube; se não houver, cai para uma busca. */
suspend fun YoutubeRepository.home(): FeedPage = withContext(Dispatchers.IO) {
    try {
        val service = ServiceList.YouTube
        val url = service.kioskList.defaultKioskExtractor.url
        val info = org.schabi.newpipe.extractor.kiosk.KioskInfo.getInfo(service, url)
        val items = info.relatedItems.filterIsInstance<StreamInfoItem>()
        if (items.isEmpty()) error("vazio")
        FeedPage(
            items = items.mapNotNull { toVideoItem(it) },
            next = null,
            handler = service.searchQHFactory.fromQuery("em alta", emptyList(), "")
        )
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        search("em alta hoje")
    }
}

fun toVideoItem(s: StreamInfoItem): VideoItem? {
    val url = s.url ?: return null
    val id = YoutubeRepository.videoId(url) ?: return null
    return VideoItem(
        id = id,
        url = url,
        title = s.name ?: "",
        channel = s.uploaderName ?: "",
        views = s.viewCount,
        durationSec = s.duration,
        thumb = s.thumbnails.minByOrNull { abs(it.height - 360) }?.url,
        uploaded = s.textualUploadDate
    )
}
