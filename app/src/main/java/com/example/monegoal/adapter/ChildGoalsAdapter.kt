package com.example.monegoal.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.monegoal.R
import com.example.monegoal.models.Goal
import java.text.NumberFormat
import java.util.*

class ChildGoalsAdapter(
    private var goals: List<Goal>,
    private val onClick: (Goal) -> Unit,
    private val onComplete: (Goal) -> Unit
) : RecyclerView.Adapter<ChildGoalsAdapter.GoalViewHolder>() {

    private var currentBalance: Long = 0L

    inner class GoalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvGoalTitle)
        val tvDescription: TextView = itemView.findViewById(R.id.tvGoalDescription)
        val tvCurrent: TextView = itemView.findViewById(R.id.tvCurrentAmount)
        val tvTargetLabel: TextView = itemView.findViewById(R.id.tvTargetLabel)
        val tvPoints: TextView = itemView.findViewById(R.id.tvPoints)
        val progressBar: ProgressBar = itemView.findViewById(R.id.pbGoalProgress)
        val btnStatus: Button? = itemView.findViewById(R.id.btnGoalStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GoalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_child_goal, parent, false)
        return GoalViewHolder(view)
    }

    override fun onBindViewHolder(holder: GoalViewHolder, position: Int) {
        val goal = goals[position]
        holder.tvTitle.text = goal.title
        holder.tvDescription.text = goal.description

        // collected: gunakan currentBalance (saldo user) atau goal.currentAmount sesuai kebutuhan.
        val collected = currentBalance.coerceAtMost(goal.targetAmount)
        holder.tvCurrent.text = formatCurrency(collected)
        holder.tvTargetLabel.text = "Target: ${formatCurrency(goal.targetAmount)}"
        holder.tvPoints.text = "${goal.points} Poin"

        val progressPercent = if (goal.targetAmount > 0L) {
            ((collected.toDouble() / goal.targetAmount.toDouble()) * 100).toInt()
        } else 0
        holder.progressBar.progress = progressPercent.coerceIn(0, 100)

        // Tampilkan tombol "Selesai" hanya jika progress >= 100
        if (progressPercent >= 100 && holder.btnStatus != null) {
            holder.btnStatus.visibility = View.VISIBLE
            holder.btnStatus.text = "Selesai"
            holder.btnStatus.isEnabled = true
            holder.btnStatus.setOnClickListener {
                onComplete(goal)
            }
        } else {
            holder.btnStatus?.visibility = View.GONE
            holder.btnStatus?.setOnClickListener(null)
        }

        // klik item -> buka detail misalnya
        holder.itemView.setOnClickListener { onClick(goal) }
    }

    override fun getItemCount(): Int = goals.size

    fun updateGoals(newGoals: List<Goal>) {
        this.goals = newGoals
        notifyDataSetChanged()
    }

    fun updateBalance(newBalance: Long) {
        this.currentBalance = newBalance
        notifyDataSetChanged()
    }

    private fun formatCurrency(amount: Long): String {
        val nf = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        return nf.format(amount).replace(",00", "")
    }
}