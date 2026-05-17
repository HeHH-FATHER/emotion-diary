package com.example.sensordiary.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

class SensorHelper(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isLightSensorSupported = lightSensor != null
    val isGyroSensorSupported = gyroSensor != null
    val isAccelSensorSupported = accelSensor != null

    // Light
    private val _lightIntensity = MutableStateFlow(0f)
    val lightIntensity = _lightIntensity.asStateFlow()

    // Audio
    private val _ambientDecibels = MutableStateFlow(0)
    val ambientDecibels = _ambientDecibels.asStateFlow()

    // Voice analysis
    private val _voicePitch = MutableStateFlow(0f)
    val voicePitch = _voicePitch.asStateFlow()
    private val _voiceTone = MutableStateFlow("未知")
    val voiceTone = _voiceTone.asStateFlow()

    // Gyro
    private val _shakeFrequency = MutableStateFlow(0f)
    val shakeFrequency = _shakeFrequency.asStateFlow()

    private val _realTimeMagnitude = MutableStateFlow(0f)
    val realTimeMagnitude = _realTimeMagnitude.asStateFlow()

    // Accelerometer activity state
    private val _activityState = MutableStateFlow("静止")
    val activityState = _activityState.asStateFlow()

    // Screen state (on/off)
    private val _isScreenOn = MutableStateFlow(true)
    val isScreenOn = _isScreenOn.asStateFlow()

    // Sampling buffer for averaging during scan
    private val sampleBuffer = mutableListOf<SampleSnapshot>()
    private val pitchBuffer = mutableListOf<Float>()

    data class SampleSnapshot(
        val light: Float,
        val db: Float,
        val shakeFreq: Float,
        val activityState: String,
        val voicePitch: Float,
        val voiceTone: String
    )

    fun startSampling() {
        sampleBuffer.clear()
        pitchBuffer.clear()
    }

    fun getSampleSnapshot(): List<SampleSnapshot> {
        return sampleBuffer.toList()
    }

    fun getPitchSamples(): List<Float> {
        return pitchBuffer.toList()
    }

    private var gyroLastTimestamp = 0L
    private var gyroPeakCount = 0
    private var gyroStartTime = 0L

    private var gyroMagnitudeHistory = mutableListOf<Float>()

    private val accelWindow = mutableListOf<Float>()
    private val ACCEL_WINDOW_SIZE = 30

    // Voice pitch tracking
    private val voicePitchHistory = mutableListOf<Float>()

    private var audioRecord: AudioRecord? = null
    private var isMonitoringAudio = false
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val pitchFrameSize = 1024

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> _isScreenOn.value = true
                Intent.ACTION_SCREEN_OFF -> _isScreenOn.value = false
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun startMonitoring() {
        try {
            context.registerReceiver(screenReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }

        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        gyroSensor?.let {
            gyroStartTime = System.currentTimeMillis()
            gyroPeakCount = 0
            gyroMagnitudeHistory.clear()
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startAudioMonitoring()
        }
    }

    fun stopMonitoring() {
        sensorManager.unregisterListener(this)
        stopAudioMonitoring()
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioMonitoring() {
        if (isMonitoringAudio) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                isMonitoringAudio = true

                Thread {
                    val buffer = ShortArray(bufferSize)
                    val pitchFrame = DoubleArray(pitchFrameSize)
                    var frameIndex = 0
                    while (isMonitoringAudio) {
                        val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                        if (readSize > 0) {
                            // RMS amplitude + zero crossings
                            var sum = 0.0
                            var crossings = 0
                            for (i in 0 until readSize) {
                                sum += buffer[i] * buffer[i]
                                if (i > 0 && ((buffer[i] >= 0) != (buffer[i - 1] >= 0))) {
                                    crossings++
                                }
                            }
                            val amplitude = sum / readSize
                            val dbRaw = if (amplitude > 0) (10 * log10(amplitude)).toInt() else 0

                            val zeroCrossingRate = crossings.toFloat() / readSize
                            val estimatedFreq = zeroCrossingRate * sampleRate / 2f
                            val aWeightingDb = aWeightingCorrection(estimatedFreq)
                            val dbA = (dbRaw.toFloat() + aWeightingDb).coerceAtLeast(0f)
                            _ambientDecibels.value = dbA.toInt()

                            // Pitch detection via autocorrelation
                            for (i in 0 until readSize) {
                                pitchFrame[frameIndex % pitchFrameSize] = buffer[i].toDouble() / 32768.0
                                frameIndex++
                                if (frameIndex % pitchFrameSize == 0) {
                                    val pitch = detectPitch(pitchFrame, pitchFrameSize)
                                    if (pitch > 0f) {
                                        _voicePitch.value = pitch
                                        voicePitchHistory.add(pitch)
                                        pitchBuffer.add(pitch)
                                        if (voicePitchHistory.size > 30) {
                                            voicePitchHistory.removeAt(0)
                                        }
                                        updateVoiceTone()
                                    }
                                }
                            }

                            recordSample(dbA)
                        }
                    }
                }.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Autocorrelation-based pitch detection.
     * Returns fundamental frequency in Hz, or 0f if no clear pitch detected.
     */
    private fun detectPitch(buffer: DoubleArray, size: Int): Float {
        // Skip if amplitude too low (silence)
        var maxAmp = 0.0
        for (i in 0 until size) {
            val a = kotlin.math.abs(buffer[i])
            if (a > maxAmp) maxAmp = a
        }
        if (maxAmp < 0.01) return 0f // Too quiet

        // Autocorrelation
        val minLag = (sampleRate / 400.0).roundToInt() // Max 400 Hz
        val maxLag = (sampleRate / 60.0).roundToInt()  // Min 60 Hz
        var bestCorr = -1.0
        var bestLag = -1

        for (lag in minLag..maxLag) {
            var corr = 0.0
            for (i in 0 until size - lag) {
                corr += buffer[i] * buffer[i + lag]
            }
            corr /= (size - lag)
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }

        return if (bestLag > 0 && bestCorr > 0.01) {
            (sampleRate.toFloat() / bestLag).coerceIn(60f, 500f)
        } else {
            0f
        }
    }

    /**
     * Analyze voice tone from pitch history.
     * Stability: low variance = calm, high variance = emotional
     * Pitch level: low = deep, mid = natural, high = excited
     */
    private fun updateVoiceTone() {
        if (voicePitchHistory.size < 3) {
            _voiceTone.value = "未知"
            return
        }

        val mean = voicePitchHistory.average()
        val variance = voicePitchHistory.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)

        // Stability based on coefficient of variation
        val cv = if (mean > 0) stdDev / mean else 1.0

        val tone = when {
            cv < 0.15 -> "平稳"
            cv < 0.35 -> "起伏"
            else -> "紧张"
        }
        _voiceTone.value = tone
    }

    private fun aWeightingCorrection(frequency: Float): Float {
        if (frequency <= 0f) return -50f
        val f = frequency.toDouble()
        val f2 = f * f
        val f4 = f2 * f2
        val fSq12200 = f2 + 12200.0 * 12200.0
        val fSq20 = f2 + 20.0 * 20.0
        val fSq1077 = f2 + 1077.0 * 1077.0

        val a = 12200.0 * 12200.0 * f4 /
                ((fSq12200) * sqrt(fSq20) * sqrt(fSq1077) * sqrt(fSq12200))

        val dbAdjustment = 2.0 + 20.0 * log10(a)
        return dbAdjustment.toFloat().coerceIn(-50f, 2f)
    }

    private fun stopAudioMonitoring() {
        isMonitoringAudio = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            _lightIntensity.value = event.values[0]
        } else if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)

            _realTimeMagnitude.value = 100f + (magnitude * 20f).coerceIn(0f, 100f)

            gyroMagnitudeHistory.add(magnitude)
            if (gyroMagnitudeHistory.size > 60) {
                gyroMagnitudeHistory.removeAt(0)
            }

            if (magnitude > 1.0f) {
                val now = System.currentTimeMillis()
                if (now - gyroLastTimestamp > 100) {
                    gyroPeakCount++
                    gyroLastTimestamp = now
                    val elapsedSeconds = (now - gyroStartTime) / 1000f
                    if (elapsedSeconds > 0) {
                        _shakeFrequency.value = gyroPeakCount / elapsedSeconds
                    }
                }
            }
        } else if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)

            accelWindow.add(magnitude)
            if (accelWindow.size > ACCEL_WINDOW_SIZE) {
                accelWindow.removeAt(0)
            }

            if (accelWindow.size >= 10) {
                val mean = accelWindow.average().toFloat()
                val variance = accelWindow.map { (it - mean) * (it - mean) }.average().toFloat()
                val normalizedVar = (variance / 5f).coerceIn(0f, 1f)
                val state = when {
                    normalizedVar < 0.05f -> "静止"
                    normalizedVar < 0.2f -> "微动"
                    else -> "走动"
                }
                _activityState.value = state
            }
        }
    }

    private fun recordSample(dbValue: Float) {
        synchronized(sampleBuffer) {
            sampleBuffer.add(
                SampleSnapshot(
                    light = _lightIntensity.value,
                    db = dbValue,
                    shakeFreq = _shakeFrequency.value,
                    activityState = _activityState.value,
                    voicePitch = _voicePitch.value,
                    voiceTone = _voiceTone.value
                )
            )
            if (sampleBuffer.size > 60) {
                sampleBuffer.removeAt(0)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
