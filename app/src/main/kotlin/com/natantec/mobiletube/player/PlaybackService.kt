package com.natantec.mobiletube.player

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.natantec.mobiletube.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Mantém a reprodução em segundo plano (com notificação e controles na
 * tela de bloqueio) e pula os trechos de patrocínio.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var skipJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerHolder.get(this)
        session = MediaSession.Builder(this, player).build()
        skipJob = scope.launch {
            while (isActive) {
                if (Prefs.sponsorBlock && player.isPlaying) {
                    val pos = player.currentPosition
                    val seg = PlayerHolder.segments.firstOrNull { pos >= it.startMs && pos < it.endMs - 300 }
                    if (seg != null) player.seekTo(seg.endMs)
                }
                delay(400)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        skipJob?.cancel()
        scope.cancel()
        session?.release()
        session = null
        PlayerHolder.release()
        super.onDestroy()
    }
}
