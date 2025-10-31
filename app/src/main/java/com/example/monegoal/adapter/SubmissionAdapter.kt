package com.example.monegoal.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.monegoal.R
import com.example.monegoal.model.Submission
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class SubmissionAdapter(
    private var list: List<Submission>,
    private val onItemClick: (Submission) -> Unit
) : RecyclerView.Adapter<SubmissionAdapter.SubmissionViewHolder>() {

    inner class SubmissionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvSubmissionTitle)
        val tvChildName: TextView = itemView.findViewById(R.id.tvChildName)
        val tvTime: TextView = itemView.findViewById(R.id.tvSubmissionTime)
        val tvAmount: TextView = itemView.findViewById(R.id.tvSubmissionAmount)
        val card: CardView = itemView.findViewById(R.id.cardView) ?: itemView as CardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubmissionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_submission, parent, false)
        return SubmissionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubmissionViewHolder, position: Int) {
        val item = list[position]
        holder.tvTitle.text = item.title
        holder.tvChildName.text = "Dari: ${item.childName}"
        holder.tvAmount.text = "Rp %,d".format(item.amount)

        // Format waktu
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
        holder.tvTime.text = sdf.format(Date(item.createdAt))

        holder.card.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = list.size

    fun updateData(newList: List<Submission>) {
        list = newList
        notifyDataSetChanged()
    }
}