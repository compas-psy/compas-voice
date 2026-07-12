package ru.cmpas.voice.ui.components

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Live-состояние «подключены ли наушники / BT-стерео» (ТЗ 1.1 §3.3):
 * обновляется при смене аудио-маршрута через AudioDeviceCallback. Используется
 * для переключения 7b↔7c и поведения «Объёмного» фона.
 */
@Composable
fun rememberHeadphonesConnected(): Boolean {
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    fun connected(): Boolean = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }

    var state by remember { mutableStateOf(connected()) }
    DisposableEffect(am) {
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { state = connected() }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { state = connected() }
        }
        am.registerAudioDeviceCallback(cb, null)
        onDispose { am.unregisterAudioDeviceCallback(cb) }
    }
    return state
}
