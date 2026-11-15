package com.example.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import com.example.game.model.WeaponType
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural SoundPool SFX synthesizer + low-CPU ambient tactical music loop via AudioTrack.
 */
class AudioManager(context: Context? = null) {

    enum class SfxType {
        PISTOL,
        ASSAULT_RIFLE,
        SHOTGUN,
        SNIPER,
        MACHINE_GUN,
        ROCKET,
        EXPLOSION,
        GRENADE_THROW,
        RELOAD,
        EMPTY_CLICK,
        HIT_MARKER,
        PLAYER_HURT,
        KILL_CONFIRM,
        JUMP,
        PICKUP,
        FLAG_EVENT
    }

    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<SfxType, Int>()
    private var sfxVolume: Float = 0.85f
    private var musicVolume: Float = 0.55f
    private var initialized = false

    private var musicTrack: AudioTrack? = null
    private var musicThread: Thread? = null
    @Volatile private var musicRunning = false

    init {
        if (context != null) {
            initSoundPool(context)
            startMusicLoop()
        }
    }

    fun setSfxVolume(vol: Float) {
        sfxVolume = vol.coerceIn(0f, 1f)
    }

    fun setMusicVolume(vol: Float) {
        musicVolume = vol.coerceIn(0f, 1f)
        musicTrack?.setVolume(musicVolume * 0.38f)
    }

    fun playWeaponShot(weapon: WeaponType, distanceAttenuation: Float = 1f) {
        val type = when (weapon) {
            WeaponType.PISTOL -> SfxType.PISTOL
            WeaponType.ASSAULT_RIFLE -> SfxType.ASSAULT_RIFLE
            WeaponType.SHOTGUN -> SfxType.SHOTGUN
            WeaponType.SNIPER_RIFLE -> SfxType.SNIPER
            WeaponType.MACHINE_GUN -> SfxType.MACHINE_GUN
            WeaponType.ROCKET_LAUNCHER -> SfxType.ROCKET
        }
        play(type, distanceAttenuation)
    }

    fun play(type: SfxType, gain: Float = 1f) {
        if (!initialized || sfxVolume <= 0.01f) return
        val id = soundIds[type] ?: return
        val finalGain = (sfxVolume * gain).coerceIn(0f, 1f)
        if (finalGain <= 0.01f) return
        soundPool?.play(id, finalGain, finalGain, 1, 0, 1.0f)
    }

    fun pauseMusic() {
        musicRunning = false
        runCatching { musicTrack?.pause() }
    }

    fun resumeMusic() {
        if (initialized && !musicRunning) {
            startMusicLoop()
        }
    }

    fun release() {
        musicRunning = false
        runCatching {
            musicTrack?.stop()
            musicTrack?.release()
        }
        musicTrack = null
        soundPool?.release()
        soundPool = null
        soundIds.clear()
        initialized = false
    }

    private fun initSoundPool(context: Context) {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pool = SoundPool.Builder()
                .setMaxStreams(12)
                .setAudioAttributes(attrs)
                .build()
            soundPool = pool

            val cacheDir = File(context.cacheDir, "sfx_synth_v2").apply { mkdirs() }
            for (type in SfxType.entries) {
                val wavFile = File(cacheDir, "${type.name.lowercase()}.wav")
                if (!wavFile.exists()) {
                    writeWavFile(wavFile, synthesizeSamples(type), SAMPLE_RATE)
                }
                soundIds[type] = pool.load(wavFile.absolutePath, 1)
            }
            initialized = true
        } catch (_: Throwable) {
            initialized = false
        }
    }

    private fun startMusicLoop() {
        if (musicRunning) return
        try {
            val loopSamples = buildAmbientLoopSamples()
            val minBuf = AudioTrack.getMinBufferSize(
                MUSIC_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(MUSIC_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.setVolume(musicVolume * 0.38f)
            track.play()
            musicTrack = track
            musicRunning = true

            musicThread = Thread {
                var offset = 0
                val chunk = 1024
                while (musicRunning) {
                    val end = (offset + chunk).coerceAtMost(loopSamples.size)
                    track.write(loopSamples, offset, end - offset)
                    offset = if (end >= loopSamples.size) 0 else end
                }
            }.apply {
                isDaemon = true
                name = "ArenaMusicThread"
                start()
            }
        } catch (_: Throwable) {
            musicRunning = false
        }
    }

    private fun buildAmbientLoopSamples(): ShortArray {
        val durSec = 4.0f
        val count = (MUSIC_RATE * durSec).toInt()
        val out = ShortArray(count)
        // Tactical minor arpeggio bass notes: D2, F2, A2, C3
        val notes = floatArrayOf(73.42f, 87.31f, 110.0f, 130.81f, 98.0f, 87.31f, 73.42f, 65.41f)
        val stepSamples = count / notes.size
        var phase = 0.0
        var padPhase = 0.0
        for (i in 0 until count) {
            val idx = (i / stepSamples).coerceAtMost(notes.lastIndex)
            val localT = (i % stepSamples).toFloat() / MUSIC_RATE
            val freq = notes[idx]
            phase += 2.0 * PI * freq / MUSIC_RATE
            padPhase += 2.0 * PI * (freq * 2.0) / MUSIC_RATE
            val env = exp(-4.5f * localT) * 0.7f + 0.3f
            val bass = (sin(phase) * 0.6 + sin(phase * 2.0) * 0.2).toFloat() * env
            val pad = sin(padPhase).toFloat() * 0.25f
            out[i] = ((bass + pad).coerceIn(-1f, 1f) * 14000f).toInt().toShort()
        }
        return out
    }

    private fun synthesizeSamples(type: SfxType): ShortArray {
        val rng = Random(type.ordinal * 997 + 17)
        return when (type) {
            SfxType.PISTOL -> synthGunshot(0.14f, 520f, 90f, 0.65f, 24f, rng)
            SfxType.ASSAULT_RIFLE -> synthGunshot(0.11f, 640f, 115f, 0.72f, 30f, rng)
            SfxType.SHOTGUN -> synthGunshot(0.28f, 280f, 52f, 0.84f, 11f, rng)
            SfxType.SNIPER -> synthGunshot(0.34f, 980f, 70f, 0.78f, 9f, rng)
            SfxType.MACHINE_GUN -> synthGunshot(0.09f, 580f, 120f, 0.76f, 34f, rng)
            SfxType.ROCKET -> synthGunshot(0.38f, 320f, 95f, 0.90f, 7f, rng)
            SfxType.EXPLOSION -> synthGunshot(0.55f, 160f, 32f, 0.88f, 5.5f, rng)
            SfxType.GRENADE_THROW -> synthTone(0.12f, 340f, 620f, 18f)
            SfxType.RELOAD -> synthClickClack(rng)
            SfxType.EMPTY_CLICK -> synthTone(0.05f, 1200f, 900f, 55f)
            SfxType.HIT_MARKER -> synthTone(0.06f, 1750f, 1250f, 48f)
            SfxType.PLAYER_HURT -> synthGunshot(0.16f, 220f, 85f, 0.35f, 18f, rng)
            SfxType.KILL_CONFIRM -> synthKillChime()
            SfxType.JUMP -> synthTone(0.11f, 220f, 540f, 16f)
            SfxType.PICKUP -> synthTone(0.16f, 520f, 1040f, 12f)
            SfxType.FLAG_EVENT -> synthTone(0.24f, 660f, 990f, 10f)
        }
    }

    private fun synthGunshot(durationSec: Float, pitchStart: Float, pitchEnd: Float, noiseMix: Float, decay: Float, rng: Random): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-decay * t)
            val freq = pitchEnd + (pitchStart - pitchEnd) * exp(-28f * t)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val body = sin(phase).toFloat()
            val noise = rng.nextFloat() * 2f - 1f
            val sample = ((body * (1f - noiseMix) + noise * noiseMix) * env).coerceIn(-1f, 1f)
            out[i] = (sample * 29000f).toInt().toShort()
        }
        return out
    }

    private fun synthTone(durationSec: Float, fStart: Float, fEnd: Float, decay: Float): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val freq = fStart + (fEnd - fStart) * (i.toFloat() / count)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = exp(-decay * t)
            out[i] = (sin(phase).toFloat() * env * 22000f).toInt().toShort()
        }
        return out
    }

    private fun synthClickClack(rng: Random): ShortArray {
        val count = (SAMPLE_RATE * 0.24f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val pulse1 = if (t < 0.05f) exp(-70f * t) else 0f
            val pulse2 = if (t > 0.11f) exp(-55f * (t - 0.11f)) else 0f
            val v1 = (sin(2.0 * PI * 900 * t).toFloat() + (rng.nextFloat() * 2f - 1f) * 0.5f) * pulse1
            val v2 = (sin(2.0 * PI * 1450 * t).toFloat() + (rng.nextFloat() * 2f - 1f) * 0.4f) * pulse2
            out[i] = ((v1 + v2).coerceIn(-1f, 1f) * 20000f).toInt().toShort()
        }
        return out
    }

    private fun synthKillChime(): ShortArray {
        val count = (SAMPLE_RATE * 0.22f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val f = if (t < 0.09f) 784f else 1174f
            val env = exp(-14f * (if (t < 0.09f) t else t - 0.09f))
            out[i] = (sin(2.0 * PI * f * t).toFloat() * env * 20000f).toInt().toShort()
        }
        return out
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        val dataSize = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataSize)

        val pcmBuffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) pcmBuffer.putShort(s)

        FileOutputStream(file).use { fos ->
            fos.write(header.array())
            fos.write(pcmBuffer.array())
        }
    }

    companion object {
        private const val SAMPLE_RATE = 22050
        private const val MUSIC_RATE = 11025
    }
}
