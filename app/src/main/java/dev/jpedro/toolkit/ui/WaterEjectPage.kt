package dev.jpedro.toolkit.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.jpedro.toolkit.ToolkitViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun WaterEjectPage(vm: ToolkitViewModel) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var frequency by remember { mutableFloatStateOf(165f) }

    val ejector = remember { SpeakerWaterEjector(context) }

    DisposableEffect(Unit) {
        onDispose {
            ejector.stop()
        }
    }

    PageColumn("Water Ejector", "Eject water from speakers using 165 Hz sound waves & haptics") {
        GlassCard(title = "Ejection Instructions") {
            Muted("1. Turn phone volume to 100%\n2. Point speaker grilles facing downwards\n3. Tap Start to begin the 15-second 165 Hz audio & vibration sweep")
        }

        GlassCard(title = "Ejection Frequency") {
            SliderSetting(
                title = "Sound Frequency",
                valueText = "${frequency.roundToInt()} Hz",
                value = frequency,
                range = 140f..220f
            ) { frequency = it }
            Muted("165 Hz is the optimal frequency for ejecting trapped water from mobile speakers.")
        }

        GlassCard(title = "Water Ejection Status") {
            StatusBadge(
                text = if (isRunning) "Ejecting water... ${(progress * 100).roundToInt()}% (${frequency.roundToInt()} Hz)" else "Ready to eject water",
                tone = if (isRunning) Tone.WARN else Tone.GOOD
            )

            SiteButton(
                text = if (isRunning) "Stop Water Ejection" else "Start Water Ejection (15s)",
                onClick = {
                    if (isRunning) {
                        ejector.stop()
                        isRunning = false
                        progress = 0f
                    } else {
                        isRunning = true
                        progress = 0f
                        ejector.start(
                            frequency = frequency.toDouble(),
                            onProgress = { p -> progress = p },
                            onFinished = {
                                isRunning = false
                                progress = 0f
                            }
                        )
                    }
                },
                danger = isRunning,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

class SpeakerWaterEjector(private val context: Context) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    fun start(frequency: Double, onProgress: (Float) -> Unit, onFinished: () -> Unit) {
        if (isPlaying) return
        isPlaying = true
        CoroutineScope(Dispatchers.IO).launch {
            var vibrator: Vibrator? = null
            try {
                val sampleRate = 44100
                val minSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = if (minSize > 0) minSize else 4096

                audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        android.media.AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                }

                if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                    audioTrack?.play()
                } else {
                    throw IllegalStateException("AudioTrack failed to initialize")
                }

                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), 0))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(longArrayOf(0, 400, 200, 400), 0)
                    }
                }

                val totalMs = 15000
                val chunkSizeMs = 100
                val steps = totalMs / chunkSizeMs

                for (step in 0..steps) {
                    if (!isPlaying) break
                    val currentFreq = frequency + (sin(step.toDouble() / 5.0) * 12.0)
                    val numSamples = (chunkSizeMs * sampleRate) / 1000
                    val buffer = ShortArray(numSamples)
                    for (i in 0 until numSamples) {
                        val angle = 2.0 * Math.PI * i * currentFreq / sampleRate
                        buffer[i] = (sin(angle) * 32767).toInt().toShort()
                    }
                    audioTrack?.write(buffer, 0, numSamples)
                    withContext(Dispatchers.Main) {
                        onProgress(step.toFloat() / steps)
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { vibrator?.cancel() }
                stop()
                withContext(Dispatchers.Main) {
                    onFinished()
                }
            }
        }
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
