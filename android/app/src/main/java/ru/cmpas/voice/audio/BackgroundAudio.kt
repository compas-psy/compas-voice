package ru.cmpas.voice.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.cmpas.voice.data.Background
import ru.cmpas.voice.data.FeatureFlags
import ru.cmpas.voice.data.LocalStore
import ru.cmpas.voice.data.SoundFamily

/** Слой фонового звука (петля семьи + бинауральный слой за флагом). */
interface BackgroundAudio {
    fun start(family: SoundFamily, mode: Background)
    fun pause()
    fun resume()
    /** Сонное угасание: плавный fade-out фона в тишину за [windowMs]. */
    fun enterSleepFade(windowMs: Long)
    fun stop()
    fun release()
}

/** Заглушка (используется, когда softBackground выключен). */
object NoopBackgroundAudio : BackgroundAudio {
    override fun start(family: SoundFamily, mode: Background) {}
    override fun pause() {}
    override fun resume() {}
    override fun enterSleepFade(windowMs: Long) {}
    override fun stop() {}
    override fun release() {}
}

/**
 * Реализация на Media3/ExoPlayer.
 * - «Мягкий фон»: один ExoPlayer, gapless-луп (REPEAT_MODE_ONE), громкость 1.0
 *   (петли нормализованы к −30 LUFS на этапе сборки), плавные фейды.
 * - Бинауральный слой: второй ExoPlayer, стартует ТОЛЬКО при
 *   spatialAudio==ON И вывод в наушники/BT — в MVP флаг OFF, слой не создаётся.
 * Все вызовы — с main-потока (scope = Main.immediate).
 */
class ExoBackgroundAudio(
    private val context: Context,
    private val scope: CoroutineScope,
    private val store: LocalStore,
) : BackgroundAudio {

    init {
        // Строгий LRU: подхватываем сохранённые индексы петель с диска при старте.
        scope.launch {
            store.loopIndices.first().forEach { (family, idx) ->
                if (idx >= 0) lastLoopIndex[family] = idx
            }
        }
    }

    private companion object {
        const val BG_VOLUME = 1.0f       // петли уже на −30 LUFS
        const val FADE_IN_MS = 2_000L
        const val PAUSE_FADE_MS = 400L
        const val STOP_FADE_MS = 1_500L
        const val BIN_VOLUME = 0.5f      // бинаурал ещё тише (−10..−14 dB под фоном)
    }

    private var bgPlayer: ExoPlayer? = null
    private var binPlayer: ExoPlayer? = null
    private var fadeJob: Job? = null
    private var binFadeJob: Job? = null

    // LRU по семьям (round-robin): 4 запуска подряд → 4 разные петли.
    private val lastLoopIndex = HashMap<SoundFamily, Int>()

    private var currentFamily: SoundFamily? = null
    private var binauralDesired = false
    private var routeCallback: AudioDeviceCallback? = null

    private fun rawUri(resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    private fun nextLoop(family: SoundFamily): Int {
        val loops = SoundLibrary.loops(family)
        val prev = lastLoopIndex[family] ?: -1
        val idx = (prev + 1) % loops.size
        lastLoopIndex[family] = idx
        scope.launch { store.setLoopIndex(family, idx) } // persist на диск
        return loops[idx]
    }

    override fun start(family: SoundFamily, mode: Background) {
        if (!FeatureFlags.softBackground || mode == Background.VOICE) {
            stop(); return
        }
        val player = bgPlayer ?: ExoPlayer.Builder(context).build().also { bgPlayer = it }
        fadeJob?.cancel()
        player.setMediaItem(MediaItem.fromUri(rawUri(nextLoop(family))))
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.volume = 0f
        player.prepare()
        player.playWhenReady = true
        fadeTo(player, BG_VOLUME, FADE_IN_MS)

        // Бинауральный слой — за флагом, поверх фона, только в наушниках.
        currentFamily = family
        binauralDesired = FeatureFlags.spatialAudio && mode == Background.BINAURAL
        if (binauralDesired) {
            registerRouteCallback()
            if (isHeadphonesConnected()) startBinaural(family)
        }
    }

    override fun pause() {
        bgPlayer?.let { p -> fadeTo(p, 0f, PAUSE_FADE_MS) { p.playWhenReady = false } }
        binPlayer?.let { p -> fadeBin(p, 0f, 1_000L) { p.playWhenReady = false } }
    }

    override fun resume() {
        bgPlayer?.let { p -> p.playWhenReady = true; fadeTo(p, BG_VOLUME, PAUSE_FADE_MS) }
        binPlayer?.let { p -> p.playWhenReady = true; fadeBin(p, BIN_VOLUME, 8_000L) }
    }

    override fun enterSleepFade(windowMs: Long) {
        // Окно = длина практики («уснуть под фон»), до 30 мин.
        val w = windowMs.coerceIn(2_000L, 30 * 60_000L)
        bgPlayer?.let { p -> fadeTo(p, 0f, w) }
        binPlayer?.let { p -> fadeBin(p, 0f, w) }
    }

    override fun stop() {
        binauralDesired = false
        unregisterRouteCallback()
        bgPlayer?.let { p ->
            fadeTo(p, 0f, STOP_FADE_MS) {
                p.stop(); p.clearMediaItems()
            }
        }
        stopBinaural(2_000L)
    }

    override fun release() {
        binauralDesired = false
        unregisterRouteCallback()
        fadeJob?.cancel(); binFadeJob?.cancel()
        bgPlayer?.release(); bgPlayer = null
        binPlayer?.release(); binPlayer = null
    }

    private fun fadeTo(player: ExoPlayer, target: Float, durationMs: Long, then: (() -> Unit)? = null) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val startV = player.volume
            val steps = (durationMs / 30L).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                player.volume = startV + (target - startV) * (i.toFloat() / steps)
                delay(30L)
            }
            player.volume = target
            then?.invoke()
        }
    }

    // ── Бинауральный слой (за флагом OFF в MVP) ─────────────
    private fun startBinaural(family: SoundFamily) {
        val player = binPlayer ?: ExoPlayer.Builder(context).build().also { binPlayer = it }
        binFadeJob?.cancel()
        player.setMediaItem(MediaItem.fromUri(rawUri(SoundLibrary.binaural(family))))
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.volume = 0f
        player.prepare()
        player.playWhenReady = true
        fadeBin(player, BIN_VOLUME, 9_000L) // мягкий вход 8–10 с
    }

    private fun stopBinaural(durationMs: Long = 9_000L) {
        binPlayer?.let { p -> fadeBin(p, 0f, durationMs) { p.stop(); p.clearMediaItems() } }
    }

    /** Подписка на смену аудио-маршрута: наушники вкл/выкл во время практики. */
    private fun registerRouteCallback() {
        if (routeCallback != null) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                if (binauralDesired && isHeadphonesConnected() && binPlayer?.isPlaying != true) {
                    currentFamily?.let { startBinaural(it) } // fade-in при подключении
                }
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                if (!isHeadphonesConnected()) stopBinaural(1_000L) // быстрый уход из динамика
            }
        }
        am.registerAudioDeviceCallback(cb, null)
        routeCallback = cb
    }

    private fun unregisterRouteCallback() {
        val cb = routeCallback ?: return
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.unregisterAudioDeviceCallback(cb)
        routeCallback = null
    }

    private fun fadeBin(player: ExoPlayer, target: Float, durationMs: Long, then: (() -> Unit)? = null) {
        binFadeJob?.cancel()
        binFadeJob = scope.launch {
            val startV = player.volume
            val steps = (durationMs / 40L).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                player.volume = startV + (target - startV) * (i.toFloat() / steps)
                delay(40L)
            }
            player.volume = target
            then?.invoke()
        }
    }

    private fun isHeadphonesConnected(): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    }
}
