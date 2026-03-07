package com.sadtaz.workout

import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LogWorkoutActivity : AppCompatActivity() {

    private lateinit var repo: WorkoutRepository
    private lateinit var exercise: String
    private lateinit var muscle: String
    private lateinit var dayCode: String
    private lateinit var todayDate: String
    private lateinit var exType: ExerciseType
    private val setInputs = mutableListOf<SetInput>()
    private var prevSets = listOf<WorkoutSet>()
    private lateinit var adapter: SetsInputAdapter

    // Rest timer
    private var timerRunning = false
    private var timerStart = 0L
    private lateinit var tvTimer: TextView
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (timerRunning) {
                val elapsed = (SystemClock.elapsedRealtime() - timerStart) / 1000
                val m = elapsed / 60; val s = elapsed % 60
                tvTimer.text = "Rest: %d:%02d".format(m, s)
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_workout)

        exercise  = intent.getStringExtra("exercise") ?: run { finish(); return }
        muscle    = intent.getStringExtra("muscle") ?: ""
        dayCode   = intent.getStringExtra("dayCode") ?: ""
        exType    = getExerciseType(exercise)
        todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        repo      = WorkoutRepository(WorkoutDatabase.getInstance(this).workoutDao())

        tvTimer = findViewById(R.id.tvTimer)
        findViewById<TextView>(R.id.tvExerciseTitle).text = exercise
        findViewById<TextView>(R.id.tvMuscleLabel).text   = muscle
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // Rest timer toggle
        tvTimer.setOnClickListener {
            if (timerRunning) {
                timerRunning = false
                timerHandler.removeCallbacks(timerRunnable)
                tvTimer.text = "Rest Timer"
            } else {
                timerRunning = true
                timerStart = SystemClock.elapsedRealtime()
                timerHandler.post(timerRunnable)
            }
        }

        // Column headers
        val tvH1 = findViewById<TextView>(R.id.tvColSet)
        val tvH2 = findViewById<TextView>(R.id.tvColPrev)
        val tvH3 = findViewById<TextView>(R.id.tvColA)
        val tvH4 = findViewById<TextView>(R.id.tvColB)
        when (exType) {
            ExerciseType.PLANK  -> { tvH3.text = "MIN"; tvH4.visibility = View.GONE }
            ExerciseType.CARDIO -> { tvH3.text = "MIN"; tvH4.text = "KM/H" }
            else                -> { tvH3.text = "KG";  tvH4.text = "REPS" }
        }

        val rv = findViewById<RecyclerView>(R.id.rvSets)
        adapter = SetsInputAdapter(setInputs, prevSets, exType)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        lifecycleScope.launch {
            // Load previous session for reference
            prevSets = repo.getLastSessionSets(exercise, todayDate)

            // Load today's data if exists
            val existing = repo.getSetsForExerciseAndDate(exercise, todayDate)
            setInputs.clear()
            if (existing.isNotEmpty()) {
                existing.forEach { setInputs.add(SetInput(it.setNumber, it.reps, it.weightKg, it.minutes, it.speedKmh)) }
            } else {
                // Pre-fill with previous session values as hints
                if (prevSets.isNotEmpty()) {
                    prevSets.forEach { setInputs.add(SetInput(it.setNumber, it.reps, it.weightKg, it.minutes, it.speedKmh)) }
                } else {
                    setInputs.add(SetInput(1))
                }
            }
            adapter.updatePrev(prevSets)
            adapter.notifyDataSetChanged()
        }

        findViewById<Button>(R.id.btnAddSet).setOnClickListener {
            // Copy last set values as default
            val last = setInputs.lastOrNull()
            setInputs.add(SetInput(setInputs.size + 1, last?.reps ?: 0, last?.weightKg ?: 0f, last?.minutes ?: 0f, last?.speedKmh ?: 0f))
            adapter.notifyItemInserted(setInputs.size - 1)
            rv.smoothScrollToPosition(setInputs.size - 1)
            // Reset rest timer
            timerRunning = true
            timerStart = SystemClock.elapsedRealtime()
            timerHandler.post(timerRunnable)
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            lifecycleScope.launch {
                val toSave = setInputs.filterIndexed { _, s ->
                    when (exType) {
                        ExerciseType.PLANK, ExerciseType.CARDIO -> s.minutes > 0f
                        else -> s.reps > 0 || s.weightKg > 0f
                    }
                }.mapIndexed { i, s -> s.copy(setNumber = i + 1) }

                if (toSave.isEmpty()) {
                    Toast.makeText(this@LogWorkoutActivity, "Pehle kuch values enter karo!", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                repo.saveSets(exercise, muscle, dayCode, todayDate, toSave)
                Toast.makeText(this@LogWorkoutActivity, "✓ Save ho gaya!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
    }
}

class SetsInputAdapter(
    private val sets: MutableList<SetInput>,
    private var prevSets: List<WorkoutSet>,
    private val type: ExerciseType
) : RecyclerView.Adapter<SetsInputAdapter.VH>() {

    fun updatePrev(p: List<WorkoutSet>) { prevSets = p }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvNum:   TextView  = v.findViewById(R.id.tvSetNum)
        val tvPrev:  TextView  = v.findViewById(R.id.tvPrev)
        val etA:     EditText  = v.findViewById(R.id.etFieldA)
        val etB:     EditText  = v.findViewById(R.id.etFieldB)
        val cbDone:  CheckBox  = v.findViewById(R.id.cbDone)
        val btnDel:  ImageView = v.findViewById(R.id.btnDelSet)
        var wA: TextWatcher? = null
        var wB: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_set_row, parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val s = sets[pos]
        holder.tvNum.text = "${pos + 1}"

        // Previous session hint
        val prev = prevSets.getOrNull(pos)
        holder.tvPrev.text = if (prev != null) {
            when (type) {
                ExerciseType.PLANK  -> "${prev.minutes}m"
                ExerciseType.CARDIO -> "${prev.minutes}m"
                else                -> "${prev.weightKg}×${prev.reps}"
            }
        } else "-"

        holder.etA.removeTextChangedListener(holder.wA)
        holder.etB.removeTextChangedListener(holder.wB)

        when (type) {
            ExerciseType.PLANK -> {
                holder.etA.hint = "Min"
                holder.etA.setText(if (s.minutes > 0f) s.minutes.toString() else "")
                holder.etB.visibility = View.GONE
            }
            ExerciseType.CARDIO -> {
                holder.etA.hint = "Min"
                holder.etB.hint = "km/h"
                holder.etA.setText(if (s.minutes > 0f) s.minutes.toString() else "")
                holder.etB.setText(if (s.speedKmh > 0f) s.speedKmh.toString() else "")
                holder.etB.visibility = View.VISIBLE
            }
            else -> {
                holder.etA.hint = "kg"
                holder.etB.hint = "reps"
                holder.etA.setText(if (s.weightKg > 0f) s.weightKg.toString() else "")
                holder.etB.setText(if (s.reps > 0) s.reps.toString() else "")
                holder.etB.visibility = View.VISIBLE
            }
        }

        holder.cbDone.isChecked = s.isCompleted
        if (s.isCompleted) {
            holder.itemView.alpha = 0.6f
        } else {
            holder.itemView.alpha = 1f
        }

        holder.wA = tw { text ->
            val i = holder.adapterPosition; if (i < 0) return@tw
            when (type) {
                ExerciseType.PLANK, ExerciseType.CARDIO -> sets[i].minutes  = text.toFloatOrNull() ?: 0f
                else                                    -> sets[i].weightKg = text.toFloatOrNull() ?: 0f
            }
        }
        holder.wB = tw { text ->
            val i = holder.adapterPosition; if (i < 0) return@tw
            when (type) {
                ExerciseType.CARDIO -> sets[i].speedKmh = text.toFloatOrNull() ?: 0f
                else                -> sets[i].reps     = text.toIntOrNull()   ?: 0
            }
        }
        holder.etA.addTextChangedListener(holder.wA)
        holder.etB.addTextChangedListener(holder.wB)

        holder.cbDone.setOnCheckedChangeListener { _, checked ->
            val i = holder.adapterPosition; if (i < 0) return@setOnCheckedChangeListener
            sets[i].isCompleted = checked
            holder.itemView.alpha = if (checked) 0.6f else 1f
        }

        holder.btnDel.setOnClickListener {
            val i = holder.adapterPosition
            if (i >= 0 && sets.size > 1) { sets.removeAt(i); notifyItemRemoved(i); notifyItemRangeChanged(i, sets.size) }
        }
    }

    override fun getItemCount() = sets.size

    private fun tw(cb: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { cb(s?.toString().orEmpty()) }
    }
}
