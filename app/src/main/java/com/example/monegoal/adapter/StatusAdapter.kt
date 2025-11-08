package com.example.monegoal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.monegoal.models.Submission
import java.text.NumberFormat
import java.util.*

class StatusAdapter(
    submissions: List<Submission>,
    private val onClick: (Submission) -> Unit
) : RecyclerView.Adapter<StatusAdapter.Holder>() {

    private val items = submissions.sortedByDescending { it.createdAt }.take(5)

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvIcon: TextView = itemView.findViewById(R.id.tvStatusIcon)
        val tvTitle: TextView = itemView.findViewById(R.id.tvStatusTitle)
        val tvSub: TextView = itemView.findViewById(R.id.tvStatusSubtitle)
        val tvNominal: TextView = itemView.findViewById(R.id.tvNominal)
        val tvKeperluan: TextView = itemView.findViewById(R.id.tvKeperluan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_status_pengajuan, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val s = items[position]
        val st = s.status.lowercase(Locale.getDefault())

        when {
            st.contains("approved") || st.contains("setuju") || st.contains("disetujui") -> {
                holder.tvTitle.text = "Disetujui Orang Tua"
                holder.tvSub.text = "Pengajuan berhasil disetujui"
                holder.tvIcon.text = "✅"
            }
            st.contains("pending") || st.contains("menunggu") -> {
                holder.tvTitle.text = "Menunggu Persetujuan"
                holder.tvSub.text = "Pengajuan masih diproses"
                holder.tvIcon.text = "⏳"
            }
            st.contains("rejected") || st.contains("ditolak") || st.contains("tolak") -> {
                holder.tvTitle.text = "Ditolak"
                holder.tvSub.text = "Pengajuan tidak disetujui"
                holder.tvIcon.text = "❌"
            }
            else -> {
                holder.tvTitle.text = s.status
                holder.tvSub.text = ""
                holder.tvIcon.text = "ℹ️"
            }
        }

        holder.tvNominal.text = formatRupiah(s.amount)
        holder.tvKeperluan.text = s.title
    }

    override fun getItemCount(): Int = items.size

    private fun formatRupiah(amount: Long): String {
        val nf = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        return nf.format(amount).replace("Rp", "Rp ").trim()
    }
}