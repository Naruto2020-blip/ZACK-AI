package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class RecordedAudioResult(
    val file: File,
    val base64: String,
    val mimeType: String,
    val durationSeconds: Int
)

class AudioRecordManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var startTimeMs: Long = 0L
    var isRecording: Boolean = false
        private set

    fun startRecording(): Boolean {
        return try {
            stopRecordingInternal()
            val dir = File(context.cacheDir, "voice_recordings")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "voice_query_${System.currentTimeMillis()}.m4a")
            currentOutputFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            startTimeMs = System.currentTimeMillis()
            isRecording = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecordingInternal()
            isRecording = false
            false
        }
    }

    suspend fun stopAndGetAudio(): RecordedAudioResult? = withContext(Dispatchers.IO) {
        if (!isRecording && currentOutputFile == null) return@withContext null
        try {
            val durationSeconds = ((System.currentTimeMillis() - startTimeMs) / 1000).coerceAtLeast(1).toInt()
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    // Ignore if stopped too quickly
                }
                release()
            }
            mediaRecorder = null
            isRecording = false

            val file = currentOutputFile ?: return@withContext null
            if (!file.exists() || file.length() == 0L) return@withContext null

            val bytes = file.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            RecordedAudioResult(
                file = file,
                base64 = base64,
                mimeType = "audio/mp4",
                durationSeconds = durationSeconds
            )
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            null
        }
    }

    fun cancelRecording() {
        stopRecordingInternal()
        try {
            currentOutputFile?.delete()
        } catch (e: Exception) {}
        currentOutputFile = null
        isRecording = false
    }

    private fun stopRecordingInternal() {
        try {
            mediaRecorder?.apply {
                try { stop() } catch (e: Exception) {}
                release()
            }
        } catch (e: Exception) {}
        mediaRecorder = null
    }
}
