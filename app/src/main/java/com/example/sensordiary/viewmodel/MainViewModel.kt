package com.example.sensordiary.viewmodel

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import com.example.sensordiary.data.AppDatabase
import com.example.sensordiary.model.MoodOption
import com.example.sensordiary.model.MoodRecord
import com.example.sensordiary.util.SensorHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorHelper = SensorHelper(application)
    private val db = AppDatabase.getDatabase(application)
    private val moodDao = db.moodDao()

    // Baidu Voice Recognition API
    private val baiduApiKey = "iPc1iOwMOyRTZtnFiSbvWuRd"
    private val baiduSecretKey = "dl1BlZ1luK81IFF4Lk2q0NnLK0QHXfc5"
    private var cachedBaiduToken: String? = null

    // Permission Trigger
    private val _requestPermissionEvent = MutableSharedFlow<Unit>()
    val requestPermissionEvent = _requestPermissionEvent.asSharedFlow()

    var hasAudioPermission by mutableStateOf(false)
        private set

    var isLightSensorSupported by mutableStateOf(true)
        private set
    var isGyroSensorSupported by mutableStateOf(true)
        private set
    var isAccelSensorSupported by mutableStateOf(true)
        private set

    // App Share Link
    private val appShareLink = "https://share.feijipan.com/s/7n3kQLEg"

    // UI Navigation State
    var currentTab by mutableStateOf("home")
        private set

    // Sensor Data (Real)
    var lightIntensity by mutableIntStateOf(0)
        private set
    var ambientDecibels by mutableIntStateOf(0)
        private set
    var shakeFrequency by mutableFloatStateOf(0f)
        private set
    var realTimeScanFrequency by mutableIntStateOf(100)
        private set
    var activityState by mutableStateOf("静止")
        private set
    var isScreenOn by mutableStateOf(true)
        private set
    var voicePitch by mutableFloatStateOf(0f)
        private set
    var voiceTone by mutableStateOf("未知")
        private set
    var currentEnergyScore by mutableFloatStateOf(0.5f)
        private set

    // Voice content analysis
    var detectedKeywords by mutableStateOf("")
        private set

    // Records (Now from DB)
    val moodRecords = mutableStateListOf<MoodRecord>()

    // Analysis Page Data (Derived from DB)
    var energyTrendData by mutableStateOf(listOf<Int>(0, 0, 0, 0, 0, 0, 0))
        private set
    var monthEmojis by mutableStateOf(List(31) { " " })
        private set

    // Speech results storage (text from cloud voice recognition)
    private var speechResults = mutableListOf<String>()

    fun collectSpeechResult(text: String) {
        if (text.isNotEmpty()) {
            speechResults.clear()
            speechResults.add(text)
            analyzeSpeechContent(listOf(text))
        }
    }

    init {
        checkPermissions()
        isLightSensorSupported = sensorHelper.isLightSensorSupported
        isGyroSensorSupported = sensorHelper.isGyroSensorSupported
        isAccelSensorSupported = sensorHelper.isAccelSensorSupported

        viewModelScope.launch {
            sensorHelper.lightIntensity.collectLatest {
                lightIntensity = it.toInt()
                delay(5000)
            }
        }
        viewModelScope.launch {
            sensorHelper.ambientDecibels.collectLatest {
                ambientDecibels = it
                delay(5000)
            }
        }
        viewModelScope.launch {
            sensorHelper.shakeFrequency.collectLatest {
                shakeFrequency = it
            }
        }
        viewModelScope.launch {
            sensorHelper.realTimeMagnitude.collectLatest {
                realTimeScanFrequency = it.toInt()
            }
        }
        viewModelScope.launch {
            sensorHelper.activityState.collectLatest {
                activityState = it
            }
        }
        viewModelScope.launch {
            sensorHelper.isScreenOn.collectLatest {
                isScreenOn = it
            }
        }
        viewModelScope.launch {
            sensorHelper.voicePitch.collectLatest {
                voicePitch = it
            }
        }
        viewModelScope.launch {
            sensorHelper.voiceTone.collectLatest {
                voiceTone = it
            }
        }
        viewModelScope.launch {
            moodDao.getAllRecords().collectLatest {
                moodRecords.clear()
                moodRecords.addAll(it)
                updateAnalysisData(it)
            }
        }
    }

    fun checkPermissions() {
        hasAudioPermission = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestAudioPermission() {
        viewModelScope.launch {
            _requestPermissionEvent.emit(Unit)
        }
    }

    private fun updateAnalysisData(records: List<MoodRecord>) {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        val latestRecordPerDay = records.groupBy { record ->
            calendar.timeInMillis = record.timestamp
            "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}-${calendar.get(Calendar.DAY_OF_MONTH)}"
        }.mapValues { it.value.maxByOrNull { r -> r.timestamp }!! }

        val weeklyTrend = mutableListOf<Int>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
            val record = latestRecordPerDay[dayKey]
            val score = record?.energyScore ?: 0.5f
            weeklyTrend.add((score * 100).toInt())
        }
        energyTrendData = weeklyTrend

        val monthlyList = mutableListOf<String>()
        for (i in 1..31) {
            val dayKey = "$currentYear-$currentMonth-$i"
            monthlyList.add(latestRecordPerDay[dayKey]?.emoji ?: " ")
        }
        monthEmojis = monthlyList
    }

    fun startMonitoring() {
        sensorHelper.startMonitoring()
    }

    fun stopMonitoring() {
        sensorHelper.stopMonitoring()
    }

    val dateLabel: String
        get() {
            val sdf = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE)
            return sdf.format(Date())
        }

    // Scanning State
    var isScanning by mutableStateOf(false)
        private set
    var scanCountdown by mutableIntStateOf(3)
        private set
    private var scanJob: Job? = null

    // Result Modal State
    var showResultModal by mutableStateOf(false)
        private set
    var detectedMood by mutableStateOf<MoodOption?>(null)
        private set

    // Export Modal State
    var showExportModal by mutableStateOf(false)
        private set

    // Speech text storage (used by cloud recognition callback)
    var pendingSpeechText by mutableStateOf("")
    fun updatePendingText(text: String) {
        pendingSpeechText = text
    }

    // Clear Cache Confirm Dialog State
    var showClearConfirmDialog by mutableStateOf(false)
        private set

    // Delete Single Record Confirm Dialog State
    var showDeleteConfirmDialog by mutableStateOf(false)
        private set
    var recordToDelete by mutableStateOf<MoodRecord?>(null)
        private set

    fun switchTab(tab: String) {
        currentTab = tab
    }

    fun startScanning() {
        if (isScanning) return
        isScanning = true
        scanCountdown = 3
        speechResults.clear()
        pendingSpeechText = ""
        detectedKeywords = ""
        sensorHelper.startSampling()
        Log.d("EmotionTest", "开始录音和传感器收集...")

        scanJob = viewModelScope.launch {
            // Start 16000Hz recording + sensor collection in parallel
            val recordingJob = launch(Dispatchers.IO) {
                val pcmData = recordForBaidu()
                if (pcmData.isNotEmpty()) {
                    Log.d("EmotionTest", "录音完成，PCM 大小: ${pcmData.size} bytes")
                    val text = recognizeVoiceBaidu(pcmData)
                    withContext(Dispatchers.Main) {
                        updatePendingText(text)
                        collectSpeechResult(text)
                    }
                } else {
                    Log.w("EmotionTest", "录音失败，使用纯传感器算分")
                }
            }

            // Countdown 3 seconds
            while (scanCountdown > 0) {
                delay(1000)
                scanCountdown--
            }
            Log.d("EmotionTest", "3秒倒计时结束，等待云端识别结果...")

            // Wait for Baidu API response (with timeout)
            try {
                withTimeout(30000) {
                    recordingJob.join()
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("EmotionTest", "识别超时，使用纯传感器算分")
                recordingJob.cancel()
            } catch (e: Exception) {
                Log.w("EmotionTest", "识别异常: ${e.message}")
                recordingJob.cancel()
            }

            finishScanningWithText(pendingSpeechText)
        }
    }

    fun cancelScanning() {
        if (!isScanning) return
        scanJob?.cancel()
        isScanning = false
        scanCountdown = 3
        pendingSpeechText = ""
    }

    // ========================================================================
    // 百度语音识别 API
    // ========================================================================

    /**
     * 录制 3 秒 16000Hz 单声道 16bit PCM 音频
     */
    private suspend fun recordForBaidu(): ByteArray {
        return withContext(Dispatchers.IO) {
            // 动态权限检查
            if (ContextCompat.checkSelfPermission(
                    getApplication(),
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("EmotionTest", "录音权限未授予")
                return@withContext ByteArray(0)
            }

            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufferSize, 4096)

            val audioRecord = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            } catch (e: Exception) {
                Log.e("EmotionTest", "AudioRecord 初始化失败: ${e.message}")
                return@withContext ByteArray(0)
            }

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("EmotionTest", "AudioRecord 状态未就绪")
                return@withContext ByteArray(0)
            }

            audioRecord.startRecording()
            val output = ByteArrayOutputStream()
            val buffer = ShortArray(bufferSize)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < 3200) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (i in 0 until read) {
                        val sample = buffer[i]
                        output.write(sample.toInt() and 0xFF)
                        output.write(sample.toInt() shr 8)
                    }
                }
            }

            audioRecord.stop()
            audioRecord.release()
            output.toByteArray()
        }
    }

    /**
     * 获取百度 API Access Token（缓存 29 天）
     */
    private suspend fun getBaiduAccessToken(): String? {
        return withContext(Dispatchers.IO) {
            cachedBaiduToken?.let { return@withContext it }

            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            // 使用 GET 请求，参数拼在 URL 中
            val fullUrl = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials" +
                    "&client_id=$baiduApiKey" +
                    "&client_secret=$baiduSecretKey"

            // 输出调试信息（隐藏密钥关键部分）
            Log.d("EmotionTest", "完整URL: $fullUrl")
            Log.d("EmotionTest", "client_id 前6位: ${baiduApiKey.take(6)}")
            Log.d("EmotionTest", "client_secret 前6位: ${baiduSecretKey.take(6)}")

            val request = okhttp3.Request.Builder()
                .url(fullUrl)
                .get()
                .build()

            var response: okhttp3.Response? = null
            try {
                response = client.newCall(request).execute()
                val code = response?.code
                Log.d("EmotionTest", "HTTP响应码: $code")
                val body = response?.body?.string() ?: ""
                Log.d("EmotionTest", "Token响应: $body")
                val json = JSONObject(body)
                val token = json.optString("access_token", "")
                if (token.isNotEmpty()) {
                    cachedBaiduToken = token
                    Log.d("EmotionTest", "获取百度 Access Token 成功")
                    return@withContext token
                } else {
                    Log.e("EmotionTest", "获取 Access Token 失败: $body")
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e("EmotionTest", "获取 Access Token 异常: ${e.message}")
                return@withContext null
            } finally {
                response?.close()
            }
        }
    }

    /**
     * 调用百度一句话识别 API，将 PCM 音频转为文字
     */
    private suspend fun recognizeVoiceBaidu(pcmData: ByteArray): String {
        return withContext(Dispatchers.IO) {
            val token = getBaiduAccessToken()
            if (token == null) {
                Log.w("EmotionTest", "无法获取 Access Token，跳过语音识别")
                return@withContext ""
            }

            val speechBase64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
            val cuid = java.util.UUID.randomUUID().toString()

            val jsonBody = JSONObject().apply {
                put("format", "pcm")
                put("rate", 16000)
                put("channel", 1)
                put("cuid", cuid)
                put("token", token)
                put("len", pcmData.size)
                put("speech", speechBase64)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = okhttp3.Request.Builder()
                .url("http://vop.baidu.com/server_api")
                .post(requestBody)
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            var response: okhttp3.Response? = null
            try {
                response = client.newCall(request).execute()
                val body = response?.body?.string() ?: ""
                val json = JSONObject(body)
                val errNo = json.optInt("err_no", -1)
                if (errNo == 0) {
                    val result = json.optJSONArray("result")
                    if (result != null && result.length() > 0) {
                        val text = result.getString(0)
                        Log.d("EmotionTest", "百度识别结果: [$text]")
                        return@withContext text
                    }
                } else {
                    val errMsg = json.optString("err_msg", "unknown")
                    Log.e("EmotionTest", "百度识别失败: err_no=$errNo, err_msg=$errMsg")
                }
                return@withContext ""
            } catch (e: Exception) {
                Log.e("EmotionTest", "百度识别异常: ${e.message}")
                return@withContext ""
            } finally {
                response?.close()
            }
        }
    }

    private val positiveKeywords = listOf(
        "好", "开心", "快乐", "高兴", "棒", "喜欢", "谢谢", "幸福", "舒服",
        "不错", "爽", "轻松", "阳光", "美好", "惊喜", "满意", "成功", "爱",
        "太棒了", "好玩", "有趣", "优秀", "满意", "给力", "完美", "开心",
        "哈哈", "嘻嘻", "欢乐", "愉快", "温暖", "满足", "顺利", "顺利"
    )
    private val negativeKeywords = listOf(
        "烦", "累", "讨厌", "难过", "不好", "生气", "烦死了", "糟糕",
        "压力", "焦虑", "烦人", "烦躁", "痛苦", "无语", "崩溃", "失望",
        "绝望", "恨", "倒霉", "恶心", "受不了", "孤独", "寂寞", "伤心",
        "苦", "愁", "压抑", "心累", "疲惫", "不顺", "失败", "难过"
    )

    /**
     * Simple keyword-based sentiment analysis.
     */
    private fun analyzeSpeechContent(texts: List<String>) {
        var posCount = 0
        var negCount = 0
        for (text in texts) {
            if (positiveKeywords.any { text.contains(it) }) posCount++
            if (negativeKeywords.any { text.contains(it) }) negCount++
        }

        if (posCount > 0 || negCount > 0) {
            val keywords = mutableListOf<String>()
            if (posCount > 0) keywords.add("正面词汇($posCount)")
            if (negCount > 0) keywords.add("负面词汇($negCount)")
            detectedKeywords = keywords.joinToString("，")
        }
    }

    // ========================================================================
    // 评分算法 v3 — 文本一票否决 + 阶梯保底 + 加减法修饰
    // ========================================================================

    /**
     * 检测识别到的文本是否含有正面/负面关键词
     * 返回: 1=强正面, -1=强负面, 0=中性/未检测到
     */
    private fun detectTextEmotion(): Int {
        val allSpeech = speechResults.joinToString("")
        val hasPositive = positiveKeywords.any { allSpeech.contains(it) }
        val hasNegative = negativeKeywords.any { allSpeech.contains(it) }

        return when {
            hasPositive && !hasNegative -> 1
            hasNegative && !hasPositive -> -1
            else -> 0
        }
    }

    /**
     * 传感器微调项 — 带封顶的加减法（Additive Modifiers）
     * 最大总加成为 +0.15，最大总扣减也为 -0.15
     * 绝不使用乘法，避免一个低分把整体拉垮。
     */
    private fun computeSensorModifiers(
        lightLux: Float,
        shakeFreq: Float,
        soundDb: Int,
        activity: String,
        pitchHz: Float,
        tone: String
    ): Float {
        var modifier = 0f

        // 光照调节 ±0.03
        if (lightLux > 500) modifier += 0.03f       // 明亮加分
        else if (lightLux < 10) modifier -= 0.03f   // 黑暗扣分

        // 抖动调节 ±0.03
        if (shakeFreq < 0.5f) modifier += 0.03f     // 稳定加分
        else if (shakeFreq > 2f) modifier -= 0.03f  // 剧烈扣分

        // 噪音调节 ±0.03
        if (soundDb < 40) modifier += 0.03f         // 安静加分
        else if (soundDb > 80) modifier -= 0.03f    // 吵闹扣分

        // 活动状态调节 ±0.02
        if (activity == "静止") modifier += 0.02f
        else if (activity == "活跃") modifier -= 0.02f

        // 音调调节 ±0.02
        if (pitchHz in 120f..280f) modifier += 0.02f  // 自然音域加分
        else if (pitchHz > 0f && (pitchHz < 80f || pitchHz > 380f)) modifier -= 0.02f

        // 语调调节 ±0.02
        if (tone == "平稳") modifier += 0.02f
        else if (tone == "紧张") modifier -= 0.02f

        // 封顶：总调节范围 [-0.15, +0.15]
        return modifier.coerceIn(-0.15f, 0.15f)
    }

    /**
     * 核心评分 — 文本一票否决制
     *
     * 强正面 → Base=0.80，传感器只能 ±0.15 → 最终范围 [0.65, 0.95] → 大概率 >70%
     * 强负面 → Base=0.20，传感器只能 ±0.15 → 最终范围 [0.05, 0.35] → 大概率 <30%
     * 中性   → 环境线性加权 → 最终范围 [0.35, 0.65]
     */
    private fun computeScore(
        textEmotion: Int,       // 1=正面, -1=负面, 0=中性
        lightLux: Float,
        shakeFreq: Float,
        soundDb: Int,
        activity: String,
        pitchHz: Float,
        tone: String
    ): Float {
        return when (textEmotion) {
            1 -> {
                // 【强正面】基础分 80%，传感器最多微调 ±15%
                val sensorMod = computeSensorModifiers(lightLux, shakeFreq, soundDb, activity, pitchHz, tone)
                (0.80f + sensorMod).coerceIn(0.65f, 0.95f)
            }
            -1 -> {
                // 【强负面】基础分 20%，传感器最多微调 ±15%
                val sensorMod = computeSensorModifiers(lightLux, shakeFreq, soundDb, activity, pitchHz, tone)
                (0.20f + sensorMod).coerceIn(0.05f, 0.35f)
            }
            else -> {
                // 【中性/未检测到语音】启用完整环境线性加权
                val lightNorm = normalizeLight(lightLux)
                val shakeNorm = normalizeShake(shakeFreq)
                val soundNorm = normalizeSound(soundDb)
                val actScore = when (activity) {
                    "静止" -> 0.7f
                    "微动" -> 0.5f
                    else -> 0.4f
                }

                // 线性加权：光照30% + 稳定性30% + 安静25% + 活动15%
                val envScore = (
                    lightNorm * 0.30f +
                    (1f - shakeNorm) * 0.30f +
                    (1f - soundNorm) * 0.25f +
                    actScore * 0.15f
                ).coerceIn(0f, 1f)

                // 中性模式下映射到 0.35-0.65 区间
                // envScore=1.0 → 0.65, envScore=0.0 → 0.35
                (0.35f + envScore * 0.30f).coerceIn(0.35f, 0.65f)
            }
        }
    }

    private fun normalizeLight(lux: Float): Float {
        return when {
            lux <= 5f -> 0.1f
            lux < 50f -> 0.1f + ((lux - 5f) / 45f) * 0.3f
            lux < 500f -> 0.4f + ((lux - 50f) / 450f) * 0.3f
            lux < 2000f -> 0.7f + ((lux - 500f) / 1500f) * 0.15f
            lux < 10000f -> 0.85f + ((lux - 2000f) / 8000f) * 0.1f
            else -> 0.95f
        }
    }

    private fun normalizeShake(shakeFreq: Float): Float {
        return when {
            shakeFreq <= 0.1f -> 0.1f
            shakeFreq < 0.5f -> 0.1f + (shakeFreq - 0.1f) * 0.5f
            shakeFreq < 1.5f -> 0.3f + (shakeFreq - 0.5f) * 0.3f
            shakeFreq < 3f -> 0.6f + (shakeFreq - 1.5f) * 0.2f
            else -> 0.9f.coerceAtMost(0.9f + (shakeFreq - 3f) * 0.05f)
        }.coerceIn(0f, 1f)
    }

    private fun normalizeSound(db: Int): Float {
        return when {
            db <= 30 -> 0.1f
            db < 50 -> 0.1f + ((db - 30f) / 20f) * 0.3f
            db < 70 -> 0.4f + ((db - 50f) / 20f) * 0.3f
            db < 90 -> 0.7f + ((db - 70f) / 20f) * 0.2f
            else -> 0.9f.coerceAtMost(0.9f + ((db - 90f) / 40f) * 0.1f)
        }.coerceIn(0f, 1f)
    }

    private fun finishScanningWithText(recognizedText: String) {
        isScanning = false

        val samples = sensorHelper.getSampleSnapshot()

        val avgLight: Float
        val avgDb: Float
        val avgShake: Float
        val currentActivityState: String
        val avgVoicePitch: Float
        val currentVoiceTone: String

        if (samples.isNotEmpty()) {
            avgLight = samples.map { it.light }.average().toFloat()
            avgDb = samples.map { it.db }.average().toFloat()
            avgShake = samples.map { it.shakeFreq }.average().toFloat()
            currentActivityState = samples.groupingBy { it.activityState }.eachCount()
                .maxByOrNull { it.value }?.key ?: "静止"
            avgVoicePitch = samples.filter { it.voicePitch > 0 }.map { it.voicePitch }.average().toFloat()
                .coerceAtLeast(voicePitch)
            currentVoiceTone = samples.groupingBy { it.voiceTone }.eachCount()
                .maxByOrNull { it.value }?.key ?: "未知"
        } else {
            avgLight = lightIntensity.toFloat()
            avgDb = ambientDecibels.toFloat()
            avgShake = shakeFrequency
            currentActivityState = activityState
            avgVoicePitch = voicePitch
            currentVoiceTone = voiceTone
        }

        // ============================================================
        // 评分 v3: 文本一票否决 + 加减法修饰
        // ============================================================

        // Step 1: 检测文本情感（最高优先级）
        val textEmotion = detectTextEmotion()
        Log.d("EmotionTest", "识别文本: [$recognizedText]")
        Log.d("EmotionTest", "文本情感: ${when(textEmotion) { 1 -> "正面"; -1 -> "负面"; else -> "中性" }}")
        Log.d("EmotionTest", "关键词: [$detectedKeywords]")

        // Step 2: 根据文本情感计算分数
        currentEnergyScore = computeScore(
            textEmotion = textEmotion,
            lightLux = avgLight,
            shakeFreq = avgShake,
            soundDb = avgDb.toInt(),
            activity = currentActivityState,
            pitchHz = avgVoicePitch,
            tone = currentVoiceTone
        )

        Log.d("EmotionTest", "最终文本情感基准分: ${(currentEnergyScore * 100).toInt()}%")

        // Mood descriptions now reference voice analysis
        val moods = listOf(
            MoodOption(
                "😊", "心情愉悦",
                "语音平稳积极，语调自然，状态良好。",
                buildDetail(avgVoicePitch, currentVoiceTone, currentActivityState, avgLight, avgDb)
            ),
            MoodOption(
                "😌", "平和冷静",
                "语调平稳，各项指标均衡。",
                buildDetail(avgVoicePitch, currentVoiceTone, currentActivityState, avgLight, avgDb)
            ),
            MoodOption(
                "😐", "情绪一般",
                "语调有起伏，可能存在轻微波动。",
                buildDetail(avgVoicePitch, currentVoiceTone, currentActivityState, avgLight, avgDb)
            ),
            MoodOption(
                "😔", "感到压力",
                "语音紧张或内容偏负面，建议放松。",
                buildDetail(avgVoicePitch, currentVoiceTone, currentActivityState, avgLight, avgDb)
            )
        )

        detectedMood = when {
            currentEnergyScore > 0.7f -> moods[0]
            currentEnergyScore > 0.45f -> moods[1]
            currentEnergyScore > 0.3f -> moods[2]
            else -> moods[3]
        }
        showResultModal = true
    }

    private fun buildDetail(pitch: Float, tone: String, activity: String, light: Float, db: Float): String {
        val parts = mutableListOf<String>()
        if (pitch > 0) parts.add("基频 ${pitch.toInt()}Hz")
        if (tone != "未知") parts.add("语调$tone")
        parts.add(activity)
        if (detectedKeywords.isNotEmpty()) parts.add(detectedKeywords)
        return parts.joinToString(" · ")
    }

    fun handleResult(save: Boolean) {
        if (save && detectedMood != null) {
            val record = MoodRecord(
                emoji = detectedMood!!.emoji,
                title = detectedMood!!.title,
                description = detectedMood!!.detail,
                timestamp = System.currentTimeMillis(),
                energyScore = currentEnergyScore,
                activityState = activityState,
                confidenceScore = 1.0f,
                lightValue = lightIntensity,
                dbValue = ambientDecibels,
                shakeValue = shakeFrequency,
                voicePitch = voicePitch,
                voiceTone = voiceTone,
                voiceContent = detectedKeywords
            )
            viewModelScope.launch {
                moodDao.insertRecord(record)
            }
        }
        showResultModal = false
        detectedMood = null
    }

    fun toggleExportModal(show: Boolean) {
        showExportModal = show
    }

    fun toggleClearConfirmDialog(show: Boolean) {
        showClearConfirmDialog = show
    }

    fun toggleDeleteConfirmDialog(show: Boolean, record: MoodRecord? = null) {
        recordToDelete = record
        showDeleteConfirmDialog = show
    }

    fun confirmDeleteRecord() {
        recordToDelete?.let {
            viewModelScope.launch {
                moodDao.deleteRecord(it)
                showDeleteConfirmDialog = false
                recordToDelete = null
            }
        }
    }

    fun deleteRecord(record: MoodRecord) {
        viewModelScope.launch {
            moodDao.deleteRecord(record)
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            moodDao.deleteAllRecords()
            showClearConfirmDialog = false
        }
    }

    fun copyResultToClipboard() {
        detectedMood?.let { mood ->
            val voiceInfo = if (voicePitch > 0) "基频${voicePitch.toInt()}Hz" else "未检测到语音"
            val content = "【情绪检测结果】\n状态：${mood.emoji} ${mood.title}\n描述：${mood.description}\n${voiceInfo} · 语调${voiceTone}\n活动状态：${activityState}\n能量值：${(currentEnergyScore * 100).toInt()}%\n检测时间：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n\n下载传感器日记 App：$appShareLink"
            copyToClipboard(content, "结果已复制到剪贴板")
        }
    }

    fun copyAnalysisToClipboard() {
        val avgEnergy = if (energyTrendData.isNotEmpty()) energyTrendData.average().toInt() else 0
        val content = "【情绪周报】\n本周平均能量：$avgEnergy%\n检测记录数：${moodRecords.size}\n\n【不写日记】\n一起记录情绪变化，快速下载App最新版本：$appShareLink"
        copyToClipboard(content, "分析报告已复制到剪贴板")
    }

    private fun copyToClipboard(text: String, toastMsg: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SensorDiary Share", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(getApplication(), toastMsg, Toast.LENGTH_SHORT).show()
    }
}
