package ru.cmpas.voice.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Ведущий слой — голос практики. Позиция/длительность плеера следуют за реальным
 * воспроизведением голоса. Файлы — Opus в assets/voice (играются нативно через
 * ExoPlayer/Ogg). Все вызовы — с main-потока.
 */
interface VoiceEngine {
    fun load(assetPath: String)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun seekBy(deltaMs: Long)
    val positionMs: Long
    val durationMs: Long   // 0, пока неизвестна
    val ended: Boolean
    fun stop()
    fun release()
}

/** Заглушка (нет голоса) — плеер работает по номинальной длительности. */
object NoopVoiceEngine : VoiceEngine {
    override fun load(assetPath: String) {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(ms: Long) {}
    override fun seekBy(deltaMs: Long) {}
    override val positionMs: Long = 0L
    override val durationMs: Long = 0L
    override val ended: Boolean = false
    override fun stop() {}
    override fun release() {}
}

class ExoVoiceEngine(private val context: Context) : VoiceEngine {
    private var player: ExoPlayer? = null

    private fun ensure(): ExoPlayer =
        player ?: ExoPlayer.Builder(context).build().also { player = it }

    override fun load(assetPath: String) {
        val p = ensure()
        p.setMediaItem(MediaItem.fromUri("asset:///$assetPath"))
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.prepare()
    }

    override fun play() { player?.playWhenReady = true }
    override fun pause() { player?.playWhenReady = false }
    override fun seekTo(ms: Long) { player?.seekTo(ms.coerceAtLeast(0L)) }
    override fun seekBy(deltaMs: Long) {
        val p = player ?: return
        p.seekTo((p.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    override val positionMs: Long get() = player?.currentPosition ?: 0L
    override val durationMs: Long get() = player?.duration?.takeIf { it > 0L } ?: 0L
    override val ended: Boolean get() = player?.playbackState == Player.STATE_ENDED

    override fun stop() {
        player?.stop()
        player?.clearMediaItems()
    }

    override fun release() {
        player?.release()
        player = null
    }
}
