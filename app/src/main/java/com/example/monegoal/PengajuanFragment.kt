package com.example.monegoal

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.monegoal.ui.anak.AjukanDanaFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale

class PengajuanFragment : Fragment() {

    private lateinit var tvTotalSaldo: TextView
    private lateinit var tvPemasukan: TextView
    private lateinit var tvPengeluaran: TextView

    private lateinit var cardAjukanDana: FrameLayout
    private lateinit var cardPembayaran: FrameLayout

    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusSubtitle: TextView
    private lateinit var tvStatusNominal: TextView
    private lateinit var tvStatusKeperluan: TextView
    private lateinit var pbStatus: ProgressBar

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pengajuan, container, false)

        // find views
        tvTotalSaldo = view.findViewById(R.id.tvTotalSaldo)
        tvPemasukan = view.findViewById(R.id.tvPemasukan)
        tvPengeluaran = view.findViewById(R.id.tvPengeluaran)

        cardAjukanDana = view.findViewById(R.id.cardAjukanDana)
        cardPembayaran = view.findViewById(R.id.cardPembayaran)

        tvStatusTitle = view.findViewById(R.id.tvStatusTitle)
        tvStatusSubtitle = view.findViewById(R.id.tvStatusSubtitle)
        tvStatusNominal = view.findViewById(R.id.tvStatusNominal)
        tvStatusKeperluan = view.findViewById(R.id.tvStatusKeperluan)
        pbStatus = view.findViewById(R.id.pbStatus)

        setupClicks()
        loadAllData()

        return view
    }

    private fun setupClicks() {
        cardAjukanDana.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(AjukanDanaFragment())
        }

        // buka scanner / pembayaran (activity)
        cardPembayaran.setOnClickListener {
            val ctx = requireContext()
            val intent = Intent(ctx, ScannerActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadAllData() {
        val user = auth.currentUser ?: return
        val userId = user.uid
        // pertama, ambil data user (saldo)
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val balance = doc.getLong("balance") ?: doc.getLong("saldo") ?: 0L
                tvTotalSaldo.text = formatCurrency(balance)
                // setelah dapat saldo, load ringkasan transaksi
                loadTransactionsSummary(userId)
                // dan load pengajuan terbaru
                loadLatestSubmission(userId, user.displayName)
            }
            .addOnFailureListener {
                tvTotalSaldo.text = formatCurrency(0L)
                loadTransactionsSummary(userId)
                loadLatestSubmission(userId, user.displayName)
            }
    }

    private fun loadTransactionsSummary(userId: String) {
        // default 0
        var pemasukanSum = 0L
        var pengeluaranSum = 0L

        db.collection("users").document(userId)
            .collection("transactions")
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val type = doc.getString("type") ?: ""
                    val amount = (doc.getLong("amount") ?: doc.getLong("amount") ?: 0L)
                    if (type.equals("pemasukan", ignoreCase = true) || type.equals("income", ignoreCase = true)) {
                        pemasukanSum += amount
                    } else if (type.equals("pengeluaran", ignoreCase = true) || type.equals("expense", ignoreCase = true)) {
                        pengeluaranSum += amount
                    }
                }

                tvPemasukan.text = formatCurrency(pemasukanSum)
                tvPengeluaran.text = formatCurrency(pengeluaranSum)
            }
            .addOnFailureListener {
                tvPemasukan.text = formatCurrency(0L)
                tvPengeluaran.text = formatCurrency(0L)
            }
    }

    /**
     * Cari pengajuan terbaru.
     * Mencoba 2 cara:
     *  1) where "userId" == userId
     *  2) fallback where "anak" == displayName (jika ada)
     */
    private fun loadLatestSubmission(userId: String, displayName: String?) {
        // 1) coba query berdasar field userId
        db.collection("pengajuan_dana")
            .whereEqualTo("userId", userId)
            .orderBy("tanggal", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    val doc = snap.documents.first()
                    showSubmissionInUi(doc.data)
                } else {
                    // fallback: cari berdasarkan nama anak (displayName)
                    if (!displayName.isNullOrBlank()) {
                        db.collection("pengajuan_dana")
                            .whereEqualTo("anak", displayName)
                            .orderBy("tanggal", com.google.firebase.firestore.Query.Direction.DESCENDING)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { snap2 ->
                                if (!snap2.isEmpty) {
                                    val doc2 = snap2.documents.first()
                                    showSubmissionInUi(doc2.data)
                                } else {
                                    // tidak ada pengajuan -> tampilkan state kosong/default
                                    showNoSubmission()
                                }
                            }
                            .addOnFailureListener {
                                showNoSubmission()
                            }
                    } else {
                        showNoSubmission()
                    }
                }
            }
            .addOnFailureListener {
                showNoSubmission()
            }
    }

    private fun showSubmissionInUi(data: Map<String, Any>?) {
        if (data == null) {
            showNoSubmission()
            return
        }

        val status = data["status"] as? String ?: "Menunggu"
        val nominal = when {
            data["nominal"] is Number -> (data["nominal"] as Number).toLong()
            data["nominal"] is String -> (data["nominal"] as String).toLongOrNull() ?: 0L
            else -> 0L
        }
        val alasan = data["alasan"] as? String ?: "-"
        val tanggal = data["tanggal"] // unused here

        // tampilkan di UI
        tvStatusTitle.text = when {
            status.contains("setuju", true) || status.contains("disetujui", true) -> "Disetujui Orang Tua"
            status.contains("ditolak", true) -> "Ditolak"
            status.contains("menunggu", true) -> "Menunggu Persetujuan"
            else -> status
        }
        tvStatusSubtitle.text = status
        tvStatusNominal.text = formatCurrency(nominal)
        tvStatusKeperluan.text = alasan

        // progress bar: jika status == "Disetujui" -> 100; kalau Menunggu -> 50; if custom status -> map accordingly.
        val progress = when {
            status.contains("setuju", true) || status.contains("disetujui", true) -> 100
            status.contains("ditolak", true) -> 0
            status.contains("menunggu", true) -> 50
            else -> 30
        }
        pbStatus.progress = progress
    }

    private fun showNoSubmission() {
        tvStatusTitle.text = "Belum Ada Pengajuan"
        tvStatusSubtitle.text = "Kamu belum mengajukan dana"
        tvStatusNominal.text = "Rp 0"
        tvStatusKeperluan.text = "-"
        pbStatus.progress = 0
    }

    private fun formatCurrency(amount: Long): String {
        val nf = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        return nf.format(amount).replace("Rp", "Rp ").trim()
    }
}