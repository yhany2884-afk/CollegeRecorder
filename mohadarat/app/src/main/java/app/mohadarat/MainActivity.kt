package app.mohadarat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private lateinit var listView: RecyclerView
    private lateinit var empty: TextView
    private lateinit var resultCount: TextView
    private lateinit var recordPanel: View
    private lateinit var recordStatus: TextView
    private lateinit var recordTimer: TextView
    private lateinit var liveText: TextView
    private lateinit var pauseBtn: Button
    private lateinit var fab: FloatingActionButton
    private lateinit var search: EditText
    private val adapter = LectureAdapter { openDetail(it.id) }
    private var query = ""
    private var openedSaved: String? = null

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) showNewDialog()
        else Toast.makeText(this, "السماح للميكروفون مطلوب", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        listView = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        resultCount = findViewById(R.id.resultCount)
        recordPanel = findViewById(R.id.recordPanel)
        recordStatus = findViewById(R.id.recordStatus)
        recordTimer = findViewById(R.id.recordTimer)
        liveText = findViewById(R.id.liveText)
        pauseBtn = findViewById(R.id.pauseBtn)
        fab = findViewById(R.id.fab)
        search = findViewById(R.id.search)

        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                reload()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        fab.setOnClickListener { askRecord() }
        pauseBtn.setOnClickListener {
            val action = if (RecordingService.live.paused) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE
            startService(Intent(this, RecordingService::class.java).setAction(action))
        }
        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            startService(Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
        }
    }

    override fun onResume() {
        super.onResume()
        RecordingService.listener = { runOnUiThread { renderLive(it) } }
        renderLive(RecordingService.live)
        reload()
    }

    override fun onPause() {
        if (RecordingService.listener != null) RecordingService.listener = null
        super.onPause()
    }

    private fun askRecord() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) showNewDialog() else permissions.launch(missing.toTypedArray())
    }

    private fun showNewDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_new, null)
        val subject = view.findViewById<EditText>(R.id.subject)
        val language = view.findViewById<RadioGroup>(R.id.language)
        AlertDialog.Builder(this)
            .setTitle("محاضرة جديدة")
            .setView(view)
            .setPositiveButton("بدء التسجيل") { _, _ ->
                val lang = if (language.checkedRadioButtonId == R.id.langEn) "en-US" else "ar-EG"
                val intent = Intent(this, RecordingService::class.java)
                    .setAction(RecordingService.ACTION_START)
                    .putExtra(RecordingService.EXTRA_SUBJECT, subject.text.toString().trim())
                    .putExtra(RecordingService.EXTRA_LANGUAGE, lang)
                ContextCompat.startForegroundService(this, intent)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun renderLive(state: LiveState) {
        recordPanel.visibility = if (state.active) View.VISIBLE else View.GONE
        fab.visibility = if (state.active) View.GONE else View.VISIBLE
        if (!state.active) {
            if (state.savedId != null && state.savedId != openedSaved) {
                openedSaved = state.savedId
                reload()
                openDetail(state.savedId)
            }
            return
        }
        recordStatus.text = if (state.paused) "متوقف مؤقتًا · ${state.subject}" else "جاري التسجيل · ${state.subject}"
        recordTimer.text = formatClock(state.elapsedMs)
        pauseBtn.text = if (state.paused) "استئناف" else "إيقاف مؤقت"
        val live = buildString {
            append(state.transcript)
            if (state.partial.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(state.partial)
            }
        }
        liveText.text = live
        if (state.error != null) Toast.makeText(this, state.error, Toast.LENGTH_LONG).show()
    }

    private fun reload() {
        val q = fold(query.trim())
        val all = LectureStore.list(this)
        val rows = if (q.isEmpty()) all else all.filter {
            fold(listOf(it.title, it.subject, it.transcript, it.notes, it.summary).joinToString("\n")).contains(q)
        }
        adapter.submit(rows, q)
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if (all.isEmpty()) "لسه مفيش محاضرات" else "مفيش نتيجة"
        resultCount.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
        resultCount.text = "${rows.size} نتيجة"
    }

    private fun openDetail(id: String) {
        startActivity(Intent(this, DetailActivity::class.java).putExtra("id", id))
    }
}

private class LectureAdapter(
    val onClick: (Lecture) -> Unit,
) : RecyclerView.Adapter<LectureAdapter.Holder>() {
    private var items = emptyList<Lecture>()
    private var query = ""

    fun submit(list: List<Lecture>, query: String) {
        items = list
        this.query = query
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lecture, parent, false)
        return Holder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.meta.text = "${item.subject} · ${formatClock(item.durationMs)}" +
            if (item.transcript.isNotBlank()) " · نص جاهز" else ""
        val snippet = snippet(item, query)
        holder.snippet.visibility = if (snippet == null) View.GONE else View.VISIBLE
        holder.snippet.text = snippet
        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun snippet(lecture: Lecture, raw: String): String? {
        val q = fold(raw.trim())
        if (q.isEmpty()) return null
        return listOf(lecture.transcript, lecture.summary, lecture.notes)
            .flatMap { it.split('\n') }
            .firstOrNull { fold(it).contains(q) }
            ?.trim()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val meta: TextView = view.findViewById(R.id.meta)
        val snippet: TextView = view.findViewById(R.id.snippet)
    }
}
