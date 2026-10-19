package app.mohadarat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class LiveState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val elapsedMs: Long = 0,
    val subject: String = "",
    val transcript: String = "",
    val partial: String = "",
    val savedId: String? = null,
    val error: String? = null,
)

class RecordingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var speech: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFile: File? = null
    private var startedAt = 0L
    private var pausedAccum = 0L
    private var pauseMark = 0L
    private var paused = false
    private var listening = false
    private var subject = "عام"
    private var language = "ar-EG"
    private val finals = StringBuilder()
    private var partial = ""

    private val tick = object : Runnable {
        override fun run() {
            if (recorder != null && !paused) {
                publish(active = true, paused = false)
            }
            handler.postDelayed(this, 400)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(
                intent.getStringExtra(EXTRA_SUBJECT) ?: "عام",
                intent.getStringExtra(EXTRA_LANGUAGE) ?: "ar-EG",
            )
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> finish(save = true)
            else -> if (recorder == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(subject: String, language: String) {
        if (recorder != null) return
        this.subject = subject.ifBlank { "عام" }
        this.language = language
        finals.clear()
        partial = ""
        paused = false
        pausedAccum = 0
        ensureChannel()
        val notification = notification("جاري التسجيل")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTE_ID, notification)
        }
        try {
            val file = File(LectureStore.audioDir(this), "${UUID.randomUUID()}.m4a")
            audioFile = file
            val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(128000)
            rec.setAudioSamplingRate(44100)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            startedAt = System.currentTimeMillis()
            acquireWake()
            listening = true
            startSpeech()
            handler.post(tick)
            publish(active = true, paused = false)
        } catch (e: Exception) {
            publish(active = false, paused = false, error = "تعذر بدء التسجيل")
            finish(save = false)
        }
    }

    private fun pause() {
        val rec = recorder ?: return
        if (paused) return
        try {
            rec.pause()
        } catch (_: Exception) {
            return
        }
        paused = true
        pauseMark = System.currentTimeMillis()
        listening = false
        try {
            speech?.stopListening()
        } catch (_: Exception) {
        }
        updateNotification("متوقف مؤقتًا")
        publish(active = true, paused = true)
    }

    private fun resume() {
        val rec = recorder ?: return
        if (!paused) return
        try {
            rec.resume()
        } catch (_: Exception) {
            return
        }
        pausedAccum += System.currentTimeMillis() - pauseMark
        paused = false
        listening = true
        startSpeech()
        updateNotification("جاري التسجيل")
        publish(active = true, paused = false)
    }

    private fun finish(save: Boolean) {
        listening = false
        handler.removeCallbacks(tick)
        try {
            speech?.destroy()
        } catch (_: Exception) {
        }
        speech = null
        val rec = recorder
        recorder = null
        var duration = 0L
        if (rec != null) {
            duration = (System.currentTimeMillis() - startedAt - pausedAccum).coerceAtLeast(0)
            try {
                rec.stop()
            } catch (_: Exception) {
            }
            rec.release()
        }
        releaseWake()
        var savedId: String? = null
        val file = audioFile
        if (save && file != null && file.exists() && file.length() > 0) {
            val transcript = finals.toString().trim()
            val whenDate = Date()
            val title = "$subject — ${
                SimpleDateFormat("d MMM HH:mm", Locale("ar")).format(whenDate)
            }"
            val lecture = Lecture(
                id = UUID.randomUUID().toString(),
                title = title,
                subject = subject,
                language = language,
                createdAt = whenDate.time,
                durationMs = duration,
                transcript = transcript,
                summary = summarize(transcript),
                notes = "",
                audioName = file.name,
            )
            LectureStore.save(this, lecture)
            savedId = lecture.id
        } else if (!save) {
            file?.delete()
        }
        live = LiveState(savedId = savedId)
        listener?.invoke(live)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        try {
            speech?.destroy()
        } catch (_: Exception) {
        }
        val rec = SpeechRecognizer.createSpeechRecognizer(this)
        speech = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (listening && !paused) {
                    handler.postDelayed({ if (listening && !paused) listen() }, 350)
                }
            }

            override fun onResults(results: Bundle?) {
                takeText(results, final = true)
                if (listening && !paused) listen()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                takeText(partialResults, final = false)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        listen()
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speech?.startListening(intent)
        } catch (_: Exception) {
        }
    }

    private fun takeText(bundle: Bundle?, final: Boolean) {
        val text = bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) return
        if (final) {
            val stamp = formatClock(System.currentTimeMillis() - startedAt - pausedAccum)
            if (finals.isNotEmpty()) finals.append('\n')
            finals.append("[$stamp] ").append(text)
            partial = ""
        } else {
            partial = text
        }
        publish(active = true, paused = paused)
    }

    private fun publish(active: Boolean, paused: Boolean, error: String? = null) {
        val elapsed = if (startedAt == 0L) 0 else System.currentTimeMillis() - startedAt - pausedAccum
        live = LiveState(
            active = active,
            paused = paused,
            elapsedMs = elapsed.coerceAtLeast(0),
            subject = subject,
            transcript = finals.toString(),
            partial = partial,
            error = error,
        )
        listener?.invoke(live)
    }

    private fun acquireWake() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mohadarat:rec").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWake() {
        try {
            wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL, "التسجيل", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTE_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("محاضراتي · $subject")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "إنهاء", stop)
            .build()
    }

    override fun onDestroy() {
        if (recorder != null) finish(save = true)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.mohadarat.START"
        const val ACTION_PAUSE = "app.mohadarat.PAUSE"
        const val ACTION_RESUME = "app.mohadarat.RESUME"
        const val ACTION_STOP = "app.mohadarat.STOP"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_LANGUAGE = "language"
        private const val CHANNEL = "recording"
        private const val NOTE_ID = 7

        @Volatile
        var live = LiveState()

        var listener: ((LiveState) -> Unit)? = null
    }
}
