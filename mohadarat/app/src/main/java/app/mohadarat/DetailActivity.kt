package app.mohadarat

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class DetailActivity : AppCompatActivity() {
    private var lecture: Lecture? = null
    private var player: MediaPlayer? = null
    private var tab = "text"
    private lateinit var body: TextView
    private lateinit var notes: EditText
    private lateinit var bodyScroll: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        val id = intent.getStringExtra("id")
        lecture = id?.let { LectureStore.get(this, it) }
        val current = lecture
        if (current == null) {
            finish()
            return
        }
        body = findViewById(R.id.body)
        notes = findViewById(R.id.notes)
        bodyScroll = findViewById(R.id.bodyScroll)
        findViewById<TextView>(R.id.title).text = current.title
        findViewById<TextView>(R.id.meta).text =
            "${current.subject} · ${formatClock(current.durationMs)} · ${if (current.language == "en-US") "English" else "عربي"}"
        notes.setText(current.notes)
        findViewById<Button>(R.id.back).setOnClickListener { finish() }
        findViewById<Button>(R.id.play).setOnClickListener { togglePlay() }
        findViewById<Button>(R.id.share).setOnClickListener { share() }
        findViewById<Button>(R.id.delete).setOnClickListener { confirmDelete() }
        findViewById<Button>(R.id.tabText).setOnClickListener { show("text") }
        findViewById<Button>(R.id.tabSummary).setOnClickListener { show("summary") }
        findViewById<Button>(R.id.tabNotes).setOnClickListener { show("notes") }
        show("text")
    }

    private fun show(which: String) {
        tab = which
        val current = lecture ?: return
        val notesMode = which == "notes"
        notes.visibility = if (notesMode) View.VISIBLE else View.GONE
        bodyScroll.visibility = if (notesMode) View.GONE else View.VISIBLE
        body.text = when (which) {
            "summary" -> current.summary.ifBlank { "مفيش كلام كفاية للتلخيص." }
            else -> current.transcript.ifBlank { "مفيش نص. الصوت محفوظ وتقدر تشغّله." }
        }
    }

    private fun togglePlay() {
        val current = lecture ?: return
        val file = LectureStore.audioFile(this, current.audioName)
        if (!file.exists()) return
        val playing = player
        if (playing != null && playing.isPlaying) {
            playing.pause()
            findViewById<Button>(R.id.play).text = "تشغيل"
            return
        }
        if (playing != null) {
            playing.start()
            findViewById<Button>(R.id.play).text = "إيقاف الصوت"
            return
        }
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { findViewById<Button>(R.id.play).text = "تشغيل" }
            prepare()
            start()
        }
        findViewById<Button>(R.id.play).text = "إيقاف الصوت"
    }

    private fun share() {
        val current = lecture ?: return
        val text = buildString {
            appendLine(current.title)
            appendLine("المادة: ${current.subject}")
            appendLine("المدة: ${formatClock(current.durationMs)}")
            appendLine()
            if (current.summary.isNotBlank()) appendLine(current.summary).appendLine()
            if (current.notes.isNotBlank()) appendLine(current.notes).appendLine()
            appendLine(current.transcript)
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "تصدير النص"))
    }

    private fun confirmDelete() {
        val current = lecture ?: return
        AlertDialog.Builder(this)
            .setMessage("حذف المحاضرة؟")
            .setPositiveButton("حذف") { _, _ ->
                LectureStore.delete(this, current)
                finish()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onPause() {
        val current = lecture
        if (current != null) {
            val next = current.copy(notes = notes.text.toString())
            lecture = next
            LectureStore.save(this, next)
        }
        super.onPause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
