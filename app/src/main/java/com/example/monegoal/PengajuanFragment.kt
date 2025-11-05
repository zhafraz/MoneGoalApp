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
import com.google.firebase.firestore.Query
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

    private var resolvedChildId: String? = null
    private var resolvedChildName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pengajuan, container, false)

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

        val argChildId = arguments?.getString("childId")
        if (!argChildId.isNullOrBlank()) {
            resolvedChildId = argChildId
            loadChildOverview(argChildId)
        } else {
            resolveChildThenLoad()
        }

        return view
    }

    private fun setupClicks() {
        cardAjukanDana.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(AjukanDanaFragment())
        }
        cardPembayaran.setOnClickListener {
            val ctx = requireContext()
            val intent = Intent(ctx, ScannerActivity::class.java)
            startActivity(intent)
        }
    }

    private fun resolveChildThenLoad() {
        val user = auth.currentUser ?: run {
            showNoDataState()
            return
        }

        val email = user.email
        val parentId = if (!email.isNullOrBlank()) encodeEmailToId(email) else null

        if (!parentId.isNullOrBlank()) {
            db.collection("users")
                .whereArrayContains("parents", parentId)
                .limit(1)
                .get()
                .addOnSuccessListener { snap ->
                    if (!snap.isEmpty) {
                        val doc = snap.documents.first()
                        resolvedChildId = doc.id
                        resolvedChildName = doc.getString("name")
                        loadChildOverview(resolvedChildId!!)
                    } else {
                        if (!email.isNullOrBlank()) {
                            db.collection("users")
                                .whereArrayContains("parents", email)
                                .limit(1)
                                .get()
                                .addOnSuccessListener { snap2 ->
                                    if (!snap2.isEmpty) {
                                        val doc2 = snap2.documents.first()
                                        resolvedChildId = doc2.id
                                        resolvedChildName = doc2.getString("name")
                                        loadChildOverview(resolvedChildId!!)
                                    } else {
                                        loadChildOverview(user.uid)
                                    }
                                }
                                .addOnFailureListener {
                                    loadChildOverview(user.uid)
                                }
                        } else {
                            loadChildOverview(user.uid)
                        }
                    }
                }
                .addOnFailureListener {
                    loadChildOverview(user.uid)
                }
        } else {
            loadChildOverview(user.uid)
        }
    }

    private fun loadChildOverview(childId: String) {
        resolvedChildId = childId
        db.collection("users").document(childId)
            .get()
            .addOnSuccessListener { doc ->
                val saldo = doc?.getLong("saldoAnak")
                    ?: doc?.getLong("saldo")
                    ?: doc?.getLong("balance")
                    ?: 0L
                val name = doc?.getString("name") ?: ""
                resolvedChildName = name
                tvTotalSaldo.text = formatCurrency(saldo)
                loadTransactionsSummary(childId)
                loadLatestSubmission(childId, name)
            }
            .addOnFailureListener {
                tvTotalSaldo.text = formatCurrency(0L)
                loadTransactionsSummary(childId)
                loadLatestSubmission(childId, null)
            }
    }

    private fun loadTransactionsSummary(childId: String) {
        var pemasukanSum = 0L
        var pengeluaranSum = 0L

        db.collection("users").document(childId)
            .collection("transactions")
            .get()
            .addOnSuccessListener { snap ->
                for (d in snap.documents) {
                    val type = (d.getString("type") ?: "").lowercase(Locale.getDefault())
                    val category = (d.getString("category") ?: "").lowercase(Locale.getDefault())
                    val amount = when (val a = d.get("amount")) {
                        is Number -> a.toLong()
                        is String -> a.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    val isIncome = type in listOf("pemasukan", "income", "topup", "deposit") ||
                            category in listOf("topup", "deposit")
                    val isExpense = type in listOf("pengeluaran", "expense", "pembelian", "purchase", "belanja") ||
                            category in listOf("pembelian", "belanja", "purchase")

                    when {
                        isIncome -> pemasukanSum += amount
                        isExpense -> pengeluaranSum += amount
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

    private fun loadLatestSubmission(childId: String, childName: String?) {
        val collectionsToCheck = listOf(
            Pair("pengajuan_dana", "userId"),
            Pair("pengajuan_dana", "anakId"),
            Pair("submissions", "childId")
        )

        fun checkCollection(index: Int) {
            if (index >= collectionsToCheck.size) {
                // fallback by childName if ID not found
                if (!childName.isNullOrBlank()) {
                    db.collection("pengajuan_dana")
                        .whereEqualTo("anak", childName)
                        .orderBy("tanggal", Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { snap ->
                            if (!snap.isEmpty) showSubmissionInUi(snap.documents.first().data ?: emptyMap())
                            else db.collection("submissions")
                                .whereEqualTo("childName", childName)
                                .orderBy("createdAt", Query.Direction.DESCENDING)
                                .limit(1)
                                .get()
                                .addOnSuccessListener { snap2 ->
                                    if (!snap2.isEmpty) showSubmissionInUi(snap2.documents.first().data ?: emptyMap())
                                    else showNoSubmission()
                                }
                                .addOnFailureListener { showNoSubmission() }
                        }
                        .addOnFailureListener { showNoSubmission() }
                } else {
                    showNoSubmission()
                }
                return
            }

            val (col, field) = collectionsToCheck[index]
            db.collection(col)
                .whereEqualTo(field, childId)
                .orderBy("tanggal", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { snap ->
                    if (!snap.isEmpty) showSubmissionInUi(snap.documents.first().data ?: emptyMap())
                    else checkCollection(index + 1)
                }
                .addOnFailureListener { checkCollection(index + 1) }
        }

        checkCollection(0)
    }

    private fun showSubmissionInUi(data: Map<String, Any>?) {
        if (data == null) { showNoSubmission(); return }

        val rawStatus = (data["status"] ?: data["state"] ?: "menunggu").toString()
        val status = rawStatus.lowercase(Locale.getDefault())
        val nominal = when (val n = data["nominal"] ?: data["amount"] ?: data["amountRp"] ?: data["nominalRp"]) {
            is Number -> n.toLong()
            is String -> n.toLongOrNull() ?: 0L
            else -> 0L
        }
        val alasan = (data["keperluan"] ?: data["alasan"] ?: data["purpose"] ?: data["note"] ?: "-").toString()

        tvStatusNominal.text = formatCurrency(nominal)
        tvStatusKeperluan.text = alasan
        tvStatusSubtitle.text = rawStatus

        when {
            status.contains("setuju") || status.contains("disetujui") || status.contains("approved") -> {
                tvStatusTitle.text = "Disetujui Orang Tua"
                pbStatus.progress = 100
            }
            status.contains("tolak") || status.contains("ditolak") || status.contains("rejected") -> {
                tvStatusTitle.text = "Ditolak Orang Tua"
                pbStatus.progress = 0
            }
            status.contains("menunggu") || status.contains("pending") -> {
                tvStatusTitle.text = "Menunggu Persetujuan"
                pbStatus.progress = 50
            }
            else -> {
                tvStatusTitle.text = rawStatus
                pbStatus.progress = 30
            }
        }
    }

    private fun showNoSubmission() {
        tvStatusTitle.text = "Belum Ada Pengajuan"
        tvStatusSubtitle.text = "Kamu belum mengajukan dana"
        tvStatusNominal.text = "Rp 0"
        tvStatusKeperluan.text = "-"
        pbStatus.progress = 0
    }

    private fun showNoDataState() {
        tvTotalSaldo.text = formatCurrency(0L)
        tvPemasukan.text = formatCurrency(0L)
        tvPengeluaran.text = formatCurrency(0L)
        showNoSubmission()
    }

    private fun formatCurrency(amount: Long): String {
        val nf = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        return nf.format(amount).replace("Rp", "Rp ").trim()
    }

    private fun encodeEmailToId(email: String): String {
        return email.replace("@", "_at_").replace(".", "_dot_")
    }

}