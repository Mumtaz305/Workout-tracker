package com.sadtaz.workout

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val repo = WorkoutRepository(WorkoutDatabase.getInstance(this).workoutDao())
        val rv   = findViewById<RecyclerView>(R.id.rvHistory)
        rv.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            repo.getAllSets().collectLatest { allSets ->
                val items = mutableListOf<HistoryItem>()

                if (allSets.isEmpty()) {
                    items.add(HistoryItem.Header("Koi workout log nahi hua abhi tak", "Aaj se shuru karo! 💪"))
                    rv.adapter = HistoryAdapter(items); return@collectLatest
                }

                allSets.groupBy { it.loggedDate }.entries.sortedByDescending { it.key }
                    .forEach { (date, daySets) ->
                        // Calculate total volume for the day
                        val volume = daySets.filter { it.weightKg > 0 }
                            .sumOf { (it.weightKg * it.reps).toDouble() }.toInt()
                        val totalSets = daySets.size
                        val dayOfWeek = try {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            val d = sdf.parse(date)
                            java.text.SimpleDateFormat("EEEE, dd MMM", java.util.Locale.getDefault()).format(d!!)
                        } catch (e: Exception) { date }

                        val sub = buildString {
                            append("$totalSets sets")
                            if (volume > 0) append(" • ${volume} kg total")
                        }
                        items.add(HistoryItem.Header(dayOfWeek, sub))

                        daySets.groupBy { it.exerciseName }.forEach { (ex, exSets) ->
                            val type = getExerciseType(ex)
                            val detail = when (type) {
                                ExerciseType.PLANK  -> "${exSets.size} sets • Max ${exSets.maxOf { it.minutes }} min"
                                ExerciseType.CARDIO -> "${exSets.size} sets • ${exSets.maxOf { it.minutes }} min • ${exSets.maxOf { it.speedKmh }} km/h"
                                else -> {
                                    val setsStr = exSets.joinToString("  ") { "${it.weightKg}kg×${it.reps}" }
                                    setsStr
                                }
                            }
                            items.add(HistoryItem.Entry(ex, exSets.first().muscleGroup, detail))
                        }
                    }
                rv.adapter = HistoryAdapter(items)
            }
        }
    }
}

sealed class HistoryItem {
    data class Header(val title: String, val sub: String = "") : HistoryItem()
    data class Entry(val exercise: String, val muscle: String, val detail: String) : HistoryItem()
}

class HistoryAdapter(private val items: List<HistoryItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(pos: Int) = if (items[pos] is HistoryItem.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == 0) R.layout.item_history_date else R.layout.item_history_entry
        return object : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false)) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        when (val item = items[pos]) {
            is HistoryItem.Header -> {
                holder.itemView.findViewById<TextView>(R.id.tvDate).text = item.title
                holder.itemView.findViewById<TextView>(R.id.tvDateSub).text = item.sub
            }
            is HistoryItem.Entry -> {
                holder.itemView.findViewById<TextView>(R.id.tvExName).text   = item.exercise
                holder.itemView.findViewById<TextView>(R.id.tvDetail).text   = item.detail
                holder.itemView.findViewById<TextView>(R.id.tvMuscleTag).text = item.muscle
            }
        }
    }

    override fun getItemCount() = items.size
}
