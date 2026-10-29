package com.natantec.mobiletube

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.natantec.mobiletube.data.FeedPage
import com.natantec.mobiletube.data.HistoryStore
import com.natantec.mobiletube.data.Prefs
import com.natantec.mobiletube.data.SponsorBlock
import com.natantec.mobiletube.data.VideoItem
import com.natantec.mobiletube.data.YoutubeRepository
import com.natantec.mobiletube.data.home
import com.natantec.mobiletube.player.PlaybackService
import com.natantec.mobiletube.player.PlayerHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Estado de uma lista de vídeos (tela inicial ou busca). */
data class FeedState(
    val items: List<VideoItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val page: FeedPage? = null,
    val canLoadMore: Boolean = false
)

class AppVm(app: Application) : AndroidViewModel(app) {

    // ---- navegação ----
    var tab by mutableStateOf(0) // 0 início, 1 buscar, 2 histórico, 3 ajustes

    // ---- listas ----
    var home by mutableStateOf(FeedState())
        private set
    var search by mutableStateOf(FeedState())
        private set
    var query by mutableStateOf("")
    var history by mutableStateOf(HistoryStore.all())
        private set

    // ---- ajustes (gravam direto nas preferências) ----
    var blockShorts by mutableStateOf(Prefs.blockShorts)
        private set
    var sponsorBlock by mutableStateOf(Prefs.sponsorBlock)
        private set
    var historyPaused by mutableStateOf(HistoryStore.paused)
        private set
    var maxHeight by mutableStateOf(Prefs.maxHeight)
        private set

    // ---- player ----
    var playing by mutableStateOf<VideoItem?>(null)
        private set
    var playerOpen by mutableStateOf(false)
    var loadingVideo by mutableStateOf(false)
        private set
    var playError by mutableStateOf<String?>(null)
        private set
    var skipped by mutableStateOf(0)
        private set

    private var homeJob: Job? = null
    private var searchJob: Job? = null
    private var playJob: Job? = null

    init {
        loadHome()
    }

    // ================= listas =================

    fun loadHome() {
        homeJob?.cancel()
        home = FeedState(loading = true)
        homeJob = viewModelScope.launch {
            home = try {
                val p = YoutubeRepository.home()
                FeedState(items = p.items, page = p, canLoadMore = p.next != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FeedState(error = e.message ?: "Não foi possível carregar")
            }
        }
    }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        search = FeedState(loading = true)
        searchJob = viewModelScope.launch {
            search = try {
                val p = YoutubeRepository.search(q)
                FeedState(items = p.items, page = p, canLoadMore = p.next != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FeedState(error = e.message ?: "Erro na busca")
            }
        }
    }

    fun loadMore(forSearch: Boolean) {
        val cur = if (forSearch) search else home
        val page = cur.page ?: return
        val next = page.next ?: return
        if (cur.loading || !cur.canLoadMore) return
        set(forSearch, cur.copy(loading = true))
        val job = viewModelScope.launch {
            try {
                val more = YoutubeRepository.more(page.handler, next)
                val old = (if (forSearch) search else home)
                set(
                    forSearch,
                    old.copy(
                        items = old.items + more.items,
                        page = more,
                        loading = false,
                        canLoadMore = more.next != null
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val old = (if (forSearch) search else home)
                set(forSearch, old.copy(loading = false, canLoadMore = false))
            }
        }
        if (forSearch) searchJob = job else homeJob = job
    }

    private fun set(forSearch: Boolean, s: FeedState) {
        if (forSearch) search = s else home = s
    }

    /** Aplica o bloqueio de Shorts à lista. */
    fun visible(list: List<VideoItem>): List<VideoItem> =
        if (blockShorts) list.filter { !it.isShort } else list

    // ================= histórico =================

    fun isWatched(id: String): Boolean = history.any { it.id == id }

    fun toggleWatched(item: VideoItem) {
        if (isWatched(item.id)) HistoryStore.unmark(item.id) else HistoryStore.mark(item)
        history = HistoryStore.all()
    }

    fun clearHistory() {
        HistoryStore.clear()
        history = emptyList()
    }

    // ================= ajustes =================

    fun setBlockShorts(v: Boolean) {
        Prefs.blockShorts = v; blockShorts = v
    }

    fun setSponsorBlock(v: Boolean) {
        Prefs.sponsorBlock = v; sponsorBlock = v
    }

    fun setHistoryPaused(v: Boolean) {
        HistoryStore.paused = v; historyPaused = v
    }

    fun setMaxHeight(v: Int) {
        Prefs.maxHeight = v; maxHeight = v
    }

    // ================= player =================

    fun openUrl(url: String) {
        val id = YoutubeRepository.videoId(url) ?: return
        open(
            VideoItem(
                id = id, url = url, title = "Carregando…", channel = "",
                views = -1, durationSec = 0, thumb = null, uploaded = null
            )
        )
    }

    fun open(item: VideoItem) {
        playing = item
        playerOpen = true
        loadingVideo = true
        playError = null
        skipped = 0
        HistoryStore.recordIfEnabled(item)
        history = HistoryStore.all()

        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val info = YoutubeRepository.streamInfo(item.url)
                val (uri, isHls) = pickStream(info)
                    ?: error("Nenhum formato de reprodução encontrado para este vídeo")

                // Se veio de link externo, atualiza título e canal.
                val real = item.copy(
                    title = info.name ?: item.title,
                    channel = info.uploaderName ?: item.channel,
                    durationSec = if (info.duration > 0) info.duration else item.durationSec
                )
                playing = real
                if (item.title == "Carregando…") {
                    HistoryStore.unmark(item.id)
                    HistoryStore.recordIfEnabled(real)
                    history = HistoryStore.all()
                }

                val app = getApplication<Application>()
                val player = PlayerHolder.get(app)
                PlayerHolder.segments = emptyList()
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .apply { if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(real.title)
                            .setArtist(real.channel)
                            .setArtworkUri(real.thumb?.let { Uri.parse(it) })
                            .build()
                    )
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
                app.startService(Intent(app, PlaybackService::class.java))
                loadingVideo = false

                val segs = SponsorBlock.segments(item.id)
                if (playing?.id == item.id) PlayerHolder.segments = segs
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                playError = e.message ?: "Erro ao abrir o vídeo"
                loadingVideo = false
            }
        }
    }

    fun closePlayerScreen() {
        playerOpen = false
    }

    fun stopPlayback() {
        playJob?.cancel()
        PlayerHolder.release()
        getApplication<Application>().stopService(
            Intent(getApplication(), PlaybackService::class.java)
        )
        playing = null
        playerOpen = false
        loadingVideo = false
    }

    /** Escolhe o stream: vídeo+áudio juntos até a altura máxima; senão HLS; senão só áudio. */
    private fun pickStream(info: StreamInfo): Pair<String, Boolean>? {
        val maxH = Prefs.maxHeight
        val muxed = info.videoStreams.filter { !it.isVideoOnly }.sortedByDescending { it.height }
        val chosen = muxed.firstOrNull { it.height <= maxH } ?: muxed.lastOrNull()
        chosen?.content?.takeIf { it.isNotEmpty() }?.let { return it to false }
        info.hlsUrl?.takeIf { it.isNotEmpty() }?.let { return it to true }
        info.audioStreams.maxByOrNull { it.averageBitrate }
            ?.content?.takeIf { it.isNotEmpty() }?.let { return it to false }
        return null
    }
}
