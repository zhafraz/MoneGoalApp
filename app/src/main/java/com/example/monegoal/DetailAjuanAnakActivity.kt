package com.example.monegoal

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class DetailAjuanAnakActivity : AppCompatActivity() {

    private lateinit var tvChildName: TextView
    private lateinit var tvChildInfo: TextView
    private lateinit var tvNominal: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvReason: TextView
    private lateinit var tvBalanceChild: TextView
    private lateinit var tvSpentThisMonth: TextView
    private lateinit var tvCountNew: TextView
    private lateinit var tvCountApproved: TextView
    private lateinit var tvCountRejected: TextView
    private lateinit var tvCountPending: TextView
    private lateinit var btnSetujuPenuh: CardView
    private lateinit var btnTolak: CardView
    private lateinit var btnTunda: CardView
    private lateinit var headerTitle: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var childId: String? = null
    private var submissionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_ajuan_anak)

        childId = intent.getStringExtra("childId")
        submissionId = intent.getStringExtra("submissionId")

        initViews()
        loadSubmissionData()
        setupButtonListeners()
    }

    private fun initViews() {
        tvChildName = findViewById(R.id.tvChildName)
        tvChildInfo = findViewById(R.id.tvChildInfo)
        tvNominal = findViewById(R.id.tvNominal)
        tvCategory = findViewById(R.id.tvCategory)
        tvReason = findViewById(R.id.tvReason)
        tvBalanceChild = findViewById(R.id.tvBalanceChild)
        tvSpentThisMonth = findViewById(R.id.tvSpentThisMonth)
        tvCountNew = findViewById(R.id.tvCountNew)
        tvCountApproved = findViewById(R.id.tvCountApproved)
        tvCountRejected = findViewById(R.id.tvCountRejected)
        tvCountPending = findViewById(R.id.tvCountPending)
        btnSetujuPenuh = findViewById(R.id.btnSetujuPenuh)
        btnTolak = findViewById(R.id.btnTolak)
        btnTunda = findViewById(R.id.btnTunda)
        headerTitle = findViewById(R.id.tvHeaderTitle)
    }

    private fun loadSubmissionData() {
        if (submissionId == null) return

        db.collection("submissions").document(submissionId!!)
            .addSnapshotListener { doc, e ->
                if (e != null || doc == null || !doc.exists()) return@addSnapshotListener

                val amount = doc.getLong("amount") ?: 0
                val category = doc.getString("category") ?: "-"
                val reason = doc.getString("reason") ?: "-"
                val status = doc.getString("status") ?: "pending"
                val createdAt = (doc.getTimestamp("createdAt") ?: Timestamp.now()).toDate()
                val childName = doc.getString("childName") ?: "Anak"
                childId = doc.getString("childId") ?: ""

                val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(createdAt)

                tvChildName.text = childName
                tvChildInfo.text = "Diajukan pada $formattedDate"
                tvNominal.text = formatCurrency(amount)
                tvCategory.text = category
                tvReason.text = reason

                updateStatusHeader(status)
                if (!childId.isNullOrEmpty()) loadChildFinancialData(childId!!)
            }
    }

    private fun loadChildFinancialData(childId: String) {
        // ambil saldo dari users
        db.collection("users").document(childId)
            .addSnapshotListener { doc, e ->
                if (e != null || doc == null || !doc.exists()) return@addSnapshotListener

                val balance = doc.getLong("saldo") ?: doc.getLong("balance") ?: 0
                tvBalanceChild.text = formatCurrency(balance)

                // Ambil transaksi anak
                db.collection("users").document(childId)
                    .collection("transactions")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        var pemasukan = 0L
                        var pengeluaran = 0L

                        for (d in snapshot.documents) {
                            val type = d.getString("type")?.lowercase() ?: ""
                            val category = d.getString("category")?.lowercase() ?: ""
                            val amount = d.getLong("amount") ?: 0L

                            if (type in listOf("pemasukan", "income") || category in listOf("topup", "deposit")) {
                                pemasukan += amount
                            } else if (type in listOf("pengeluaran", "expense") || category in listOf("belanja", "pembelian")) {
                                pengeluaran += amount
                            }
                        }

                        tvSpentThisMonth.text = formatCurrency(pengeluaran)
                    }

                // Hitung statistik pengajuan anak
                db.collection("submissions")
                    .whereEqualTo("childId", childId)
                    .get()
                    .addOnSuccessListener { q ->
                        var approved = 0
                        var rejected = 0
                        var pending = 0
                        var newCount = 0

                        for (d in q) {
                            when (d.getString("status")?.lowercase()) {
                                "approved" -> approved++
                                "rejected" -> rejected++
                                "pending" -> pending++
                                "new" -> newCount++
                            }
                        }

                        tvCountApproved.text = approved.toString()
                        tvCountRejected.text = rejected.toString()
                        tvCountPending.text = pending.toString()
                        tvCountNew.text = newCount.toString()
                    }
            }
    }

    private fun updateStatusHeader(status: String) {
        when (status.lowercase()) {
            "approved" -> {
                headerTitle.text = "✅ Disetujui Orang Tua"
                headerTitle.setTextColor(getColor(R.color.green))
            }
            "rejected" -> {
                headerTitle.text = "❌ Ditolak Orang Tua"
                headerTitle.setTextColor(getColor(R.color.red))
            }
            else -> {
                headerTitle.text = "⏰ Menunggu Persetujuan"
                headerTitle.setTextColor(getColor(R.color.orange))
            }
        }
    }

    private fun setupButtonListeners() {
        btnSetujuPenuh.setOnClickListener { updateSubmissionStatus("approved") }
        btnTolak.setOnClickListener { updateSubmissionStatus("rejected") }
        btnTunda.setOnClickListener { updateSubmissionStatus("pending") }
    }

    private fun updateSubmissionStatus(status: String) {
        if (childId.isNullOrEmpty() || submissionId.isNullOrEmpty()) return

        val submissionRef = db.collection("submissions").document(submissionId!!)
        val userRef = db.collection("users").document(childId!!)

        db.runTransaction { tr ->
            val doc = tr.get(submissionRef)
            val currentStatus = doc.getString("status") ?: ""
            val amount = doc.getLong("amount") ?: 0L

            tr.update(submissionRef, "status", status)

            if (status == "approved" && currentStatus != "approved") {
                val curBal = tr.get(userRef).getLong("saldo") ?: tr.get(userRef).getLong("balance") ?: 0L
                tr.update(userRef, "saldo", curBal + amount)
            }
        }.addOnSuccessListener {
            Toast.makeText(this, "Status diperbarui: $status", Toast.LENGTH_SHORT).show()
            updateStatusHeader(status)
        }.addOnFailureListener {
            Toast.makeText(this, "Gagal memperbarui status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatCurrency(amount: Long): String {
        val formatted = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(amount)
        return formatted.replace("Rp", "Rp ").trim()
    }
}