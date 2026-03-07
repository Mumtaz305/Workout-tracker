package com.sadtaz.workout

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val repo by lazy { WorkoutRepository(WorkoutDatabase.getInstance(this).workoutDao()) }
    private var selectedDayIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tabLayout  = findViewById<TabLayout>(R.id.tabLayout)
        val tvTitle    = findViewById<TextView>(R.id.tvDayTitle)
        val tvMuscle   = findViewById<TextView>(R.id.tvMuscle)
        val tvDate     = findViewById<TextView>(R.id.tvDate)
        val tvVolume   = findViewById<TextView>(R.id.tvTodayVolume)
        val recycler   = findViewById<RecyclerView>(R.id.recyclerView)

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        tvDate.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        // Today's total volume
        lifecycleScope.launch {
            repo.getSetsForDate(today).collectLatest { sets ->
                val volume = sets.filter { it.weightKg > 0 }
                    .sumOf { (it.weightKg * it.reps).toDouble() }.toInt()
                val totalSets = sets.size
                tvVolume.text = if (volume > 0) "Aaj: ${totalSets} sets • ${volume} kg volume" else "Aaj koi workout log nahi hua"
            }
        }

        // Auto-select today's tab
        val todayCode = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "MON"; Calendar.TUESDAY -> "TUE"
            Calendar.WEDNESDAY -> "WED"; Calendar.THURSDAY -> "THU"
            Calendar.FRIDAY -> "FRI"; Calendar.SATURDAY -> "SAT"
            else -> "MON"
        }
        selectedDayIndex = WorkoutPlan.days.indexOfFirst { it.dayCode == todayCode }.coerceAtLeast(0)
        WorkoutPlan.days.forEach { tabLayout.addTab(tabLayout.newTab().setText(it.dayCode)) }
        tabLayout.getTabAt(selectedDayIndex)?.select()

        fun loadDay(index: Int) {
            val day = WorkoutPlan.days[index]
            tvTitle.text  = day.dayLabel
            tvMuscle.text = day.muscleGroup
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = ExerciseAdapter(day, today, repo, lifecycleScope) { ex ->
                startActivity(Intent(this, LogWorkoutActivity::class.java).apply {
                    putExtra("exercise", ex)
                    putExtra("muscle",   day.muscleGroup)
                    putExtra("dayCode",  day.dayCode)
                })
            }
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) { selectedDayIndex = t.position; loadDay(selectedDayIndex) }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
        loadDay(selectedDayIndex)

        findViewById<View>(R.id.btnHistory).setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        findViewById<View>(R.id.btnGraph).setOnClickListener   { startActivity(Intent(this, GraphActivity::class.java)) }
    }
}

class ExerciseAdapter(
    private val day: WorkoutDay,
    private val today: String,
    private val repo: WorkoutRepository,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<ExerciseAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName:   TextView = v.findViewById(R.id.tvExerciseName)
        val tvSub:    TextView = v.findViewById(R.id.tvTapToLog)
        val tvStatus: TextView = v.findViewById(R.id.tvSetStatus)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_exercise, p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val ex = day.exercises[pos]
        h.tvName.text = ex
        h.tvSub.text  = when (getExerciseType(ex)) {
            ExerciseType.PLANK  -> "Sets + Minutes"
            ExerciseType.CARDIO -> "Sets + Minutes + Speed"
            else                -> "Reps + Weight (kg)"
        }
        h.tvStatus.text = ""

        // Show today's logged sets count
        scope.launch {
            val sets = repo.getSetsForExerciseAndDate(ex, today)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (sets.isNotEmpty()) {
                    val type = getExerciseType(ex)
                    h.tvStatus.text = when (type) {
                        ExerciseType.PLANK  -> "✓ ${sets.size} sets • ${sets.maxOf{it.minutes}} min"
                        ExerciseType.CARDIO -> "✓ ${sets.size} sets • ${sets.maxOf{it.minutes}} min"
                        else                -> "✓ ${sets.size} sets • ${sets.maxOf{it.weightKg}} kg"
                    }
                    h.tvStatus.setTextColor(0xFF4CAF50.toInt())
                } else {
                    h.tvStatus.text = "Log nahi kiya"
                    h.tvStatus.setTextColor(0xFF555555.toInt())
                }
            }
        }
        h.itemView.setOnClickListener { onClick(ex) }
    }

    override fun getItemCount() = day.exercises.size
}
