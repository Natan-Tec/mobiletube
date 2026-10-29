package com.natantec.mobiletube.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.natantec.mobiletube.data.SkipSegment

/**
 * Um único ExoPlayer para o app todo. A tela e o serviço de segundo plano
 * usam o mesmo player, então o áudio continua com a tela apagada.
 */
object PlayerHolder {
    private var player: ExoPlayer? = null

    /** Trechos que serão pulados (SponsorBlock) no vídeo atual. */
    @Volatile
    var segments: List<SkipSegment> = emptyList()

    fun get(context: Context): ExoPlayer = player ?: ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .also { player = it }

    fun release() {
        player?.release()
        player = null
        segments = emptyList()
    }
}
