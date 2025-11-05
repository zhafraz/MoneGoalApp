package com.example.monegoal

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
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
    private lateinit var tvPemasukan: TextView
    private lateinit var tvPengeluaran: TextView
    private lateinit var tvCountNew: TextView
    private lateinit var tvCountApproved: TextView
    private lateinit var tvCountRejected: TextView
    private lateinit var tvCountPending: TextView
    private lateinit var btnSetujuPenuh: CardView
    private lateinit var btnTolak: CardView
    private lateinit var btnTunda: CardView
    private lateinit var headerTitle: TextView
    private lateinit var childUsername: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var childId: String? = null
    private var submissionId: String? = null
    private var submissionDocRef: DocumentReference? = null
    private var submissionCollection: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_ajuan_anak)

        childId = intent.getStringExtra("childId")
        submissionId = intent.getStringExtra("submissionId")
        submissionCollection = intent.getStringExtra("submissionCollection")

        initViews()
        setupButtonListeners()

        when {
            !submissionId.isNullOrEmpty() -> loadSubmissionById(submissionId!!)
            !childId.isNullOrEmpty() -> {
                findLatestSubmissionForChild(childId!!)
                loadChildFinancialData(childId!!)
            }
            else -> {
                val me = auth.currentUser
                if (me != null) {
                    childId = me.uid
                    findLatestSubmissionForChild(childId!!)
                    loadChildFinancialData(childId!!)
                } else {
                    Toast.makeText(this, "Tidak ada data pengajuan tersedia", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initViews() {
        tvChildName = findViewById(R.id.tvChildName)
        tvChildInfo = findViewById(R.id.tvChildInfo)
        tvNominal = findViewById(R.id.tvNominal)
        tvCategory = findViewById(R.id.tvCategory)
        tvReason = findViewById(R.id.tvReason)
        tvBalanceChild = findViewById(R.id.tvBalanceChild)
        tvPemasukan = findViewById(R.id.tvPemasukan)
        tvPengeluaran = findViewById(R.id.tvSpentThisMonth)
        tvCountNew = findViewById(R.id.tvCountNew)
        tvCountApproved = findViewById(R.id.tvCountApproved)
        tvCountRejected = findViewById(R.id.tvCountRejected)
        tvCountPending = findViewById(R.id.tvCountPending)
        btnSetujuPenuh = findViewById(R.id.btnSetujuPenuh)
        btnTolak = findViewById(R.id.btnTolak)
        btnTunda = findViewById(R.id.btnTunda)
        headerTitle = findViewById(R.id.tvHeaderTitle)

        childUsername = findViewById(R.id.childUsername)
    }


    private fun loadSubmissionById(subId: String) {
        // helper untuk fallback scanning users collection if needed
        fun scanUsersForSubmission() {
            db.collection("users")
                .get()
                .addOnSuccessListener { usersSnap ->
                    var found = false
                    val tasks = usersSnap.documents
                    for (u in tasks) {
                        db.collection("users").document(u.id)
                            .collection("submissions").document(subId)
                            .get()
                            .addOnSuccessListener { subDoc ->
                                if (!found && subDoc != null && subDoc.exists()) {
                                    found = true
                                    submissionDocRef = subDoc.reference
                                    submissionId = subDoc.id
                                    handleSubmissionDoc(subDoc)
                                }
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Pengajuan tidak ditemukan", Toast.LENGTH_SHORT).show()
                }
        }

// 1) coba top-level "submissions"
        db.collection("submissions").document(subId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    submissionDocRef = doc.reference
                    submissionId = doc.id
                    handleSubmissionDoc(doc)
                } else {
// 2) coba top-level "pengajuan_dana"
                    db.collection("pengajuan_dana").document(subId)
                        .get()
                        .addOnSuccessListener { pdoc ->
                            if (pdoc != null && pdoc.exists()) {
                                submissionDocRef = pdoc.reference
                                submissionId = pdoc.id
                                handleSubmissionDoc(pdoc)
                            } else {
// 3) scan nested users/*/submissions
                                scanUsersForSubmission()
                            }
                        }
                        .addOnFailureListener {
// jika gagal, coba scan users
                            scanUsersForSubmission()
                        }
                }
            }
            .addOnFailureListener {
// fallback ke pengajuan_dana or users scan
                db.collection("pengajuan_dana").document(subId)
                    .get()
                    .addOnSuccessListener { pdoc ->
                        if (pdoc != null && pdoc.exists()) {
                            submissionDocRef = pdoc.reference
                            submissionId = pdoc.id
                            handleSubmissionDoc(pdoc)
                        } else {
                            scanUsersForSubmission()
                        }
                    }
                    .addOnFailureListener { scanUsersForSubmission() }
            }
    }

    /**
     * Jika hanya punya childId, cari pengajuan terbaru untuk child tersebut.
     * Periksa top-level "submissions" -> "pengajuan_dana" -> users/{child}/submissions
     * Simpan reference dokumen yang ditemukan agar update langsung ke dokumen tersebut.
     */
    private fun findLatestSubmissionForChild(childId: String) {
        // helper fallback to nested
        fun checkUsersNested() {
            db.collection("users").document(childId)
                .collection("submissions")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { s2 ->
                    if (!s2.isEmpty) {
                        val d = s2.documents.first()
                        submissionDocRef = d.reference
                        submissionId = d.id
                        handleSubmissionDoc(d)
                    } else {
                        showNoSubmissionState()
                    }
                }
                .addOnFailureListener { showNoSubmissionState() }
        }

        // 1) top-level "submissions"
        db.collection("submissions")
            .whereEqualTo("childId", childId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    val doc = snap.documents.first()
                    submissionDocRef = doc.reference
                    submissionId = doc.id
                    handleSubmissionDoc(doc)
                } else {
                    // 2) top-level "pengajuan_dana" where childId or anakId or anak
                    db.collection("pengajuan_dana")
                        .whereEqualTo("childId", childId)
                        .orderBy(
                            "createdAt",
                            com.google.firebase.firestore.Query.Direction.DESCENDING
                        )
                        .limit(1)
                        .get()
                        .addOnSuccessListener { s1 ->
                            if (!s1.isEmpty) {
                                val d = s1.documents.first()
                                submissionDocRef = d.reference
                                submissionId = d.id
                                handleSubmissionDoc(d)
                            } else {
                                // coba field 'anakId'
                                db.collection("pengajuan_dana")
                                    .whereEqualTo("anakId", childId)
                                    .orderBy(
                                        "createdAt",
                                        com.google.firebase.firestore.Query.Direction.DESCENDING
                                    )
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener { s2 ->
                                        if (!s2.isEmpty) {
                                            val d = s2.documents.first()
                                            submissionDocRef = d.reference
                                            submissionId = d.id
                                            handleSubmissionDoc(d)
                                        } else {
                                            // fallback nested
                                            checkUsersNested()
                                        }
                                    }
                                    .addOnFailureListener { checkUsersNested() }
                            }
                        }
                        .addOnFailureListener { checkUsersNested() }
                }
            }
            .addOnFailureListener { checkUsersNested() }
    }

    /**
     * Gunakan doc (submission) untuk mengisi UI.
     * doc bisa dari top-level "submissions", "pengajuan_dana", atau nested.
     */
    private fun handleSubmissionDoc(doc: DocumentSnapshot) {
        if (submissionDocRef == null) submissionDocRef = doc.reference
        submissionId = doc.id

        val amount = when (val a = doc.get("amount") ?: doc.get("nominal") ?: doc.get("amountRp")) {
            is Number -> a.toLong()
            is String -> a.toLongOrNull() ?: 0L
            else -> 0L
        }
        val category = doc.getString("category") ?: doc.getString("keperluan") ?: "-"
        val reason =
            doc.getString("reason") ?: doc.getString("alasan") ?: doc.getString("purpose") ?: "-"
        val status = doc.getString("status") ?: doc.getString("state") ?: "pending"

        val ts =
            doc.get("createdAt") ?: doc.get("tanggal") ?: doc.get("date") ?: doc.get("created_at")
        val date = when (ts) {
            is Timestamp -> ts.toDate()
            is com.google.firebase.Timestamp -> ts.toDate()
            is Long -> Date(ts)
            is Number -> Date(ts.toLong())
            is Date -> ts
            else -> Date()
        }
        val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(date)

        val childNameFromDoc =
            doc.getString("childName") ?: doc.getString("anak") ?: doc.getString("child") ?: ""

        // set nama pada headline (jika ada)
        if (!childNameFromDoc.isNullOrBlank()) {
            tvChildName.text = childNameFromDoc
            // NEW: update juga childUsername (bagian "📊 Keuangan ...")
            childUsername.text = "📊 Keuangan $childNameFromDoc"
        } else {
            // kalau doc tidak berisi nama, biarkan tvChildName (atau isi dari user doc)
            // jangan menimpa dengan "Kevin" default
        }

        tvChildInfo.text = "Diajukan pada $formattedDate"
        tvNominal.text = formatCurrency(amount)
        tvCategory.text = category
        tvReason.text = reason

        val docChildId =
            doc.getString("childId") ?: doc.getString("anakId") ?: doc.getString("userId")
        if (!docChildId.isNullOrBlank()) {
            childId = docChildId
            loadChildFinancialData(childId!!)
        } else {
            childId?.let { loadChildFinancialData(it) }
        }

        updateStatusHeader(status)
    }

    /**
     * Ambil data keuangan & statistik pengajuan untuk childId
     */
    private fun loadChildFinancialData(childId: String) {
        db.collection("users").document(childId)
            .addSnapshotListener { doc, e ->
                if (e != null || doc == null || !doc.exists()) return@addSnapshotListener

                val name = doc.getString("name") ?: "Anak"
                // update kedua TextView: headline dan bagian history
                tvChildName.text = name
                childUsername.text = "📊 Keuangan $name"    // <-- make sure this updates

                val balance = doc.getLong("saldoAnak")
                    ?: doc.getLong("saldo")
                    ?: doc.getLong("balance")
                    ?: 0L
                tvBalanceChild.text = formatCurrency(balance)

                db.collection("users").document(childId)
                    .collection("transactions")
                    .get()
                    .addOnSuccessListener { snap ->
                        var pemasukan = 0L
                        var pengeluaran = 0L
                        for (d in snap.documents) {
                            val type = (d.getString("type") ?: "").lowercase(Locale.getDefault())
                            val category =
                                (d.getString("category") ?: "").lowercase(Locale.getDefault())

                            val amt = when (val a = d.get("amount")) {
                                is Number -> a.toLong()
                                is String -> a.toLongOrNull() ?: 0L
                                else -> 0L
                            }

                            val isIncome =
                                type in listOf("pemasukan", "income", "topup", "deposit") ||
                                        category in listOf("topup", "deposit")
                            val isExpense = type in listOf(
                                "pengeluaran",
                                "expense",
                                "pembelian",
                                "purchase",
                                "belanja"
                            ) ||
                                    category in listOf("pembelian", "belanja", "purchase")

                            when {
                                isIncome -> pemasukan += amt
                                isExpense -> pengeluaran += amt
                            }
                        }

                        try {
                            tvPemasukan.text = formatCurrency(pemasukan)
                        } catch (_: Throwable) {
                        }
                        try {
                            tvPengeluaran.text = formatCurrency(pengeluaran)
                        } catch (_: Throwable) {
                        }
                    }
                    .addOnFailureListener {
                        try {
                            tvPemasukan.text = formatCurrency(0L)
                        } catch (_: Throwable) {
                        }
                        try {
                            tvPengeluaran.text = formatCurrency(0L)
                        } catch (_: Throwable) {
                        }
                    }

                db.collection("submissions")
                    .whereEqualTo("childId", childId)
                    .get()
                    .addOnSuccessListener { q ->
                        var approved = 0
                        var rejected = 0
                        var pending = 0
                        var newCount = 0

                        for (d in q.documents) {
                            when (d.getString("status")?.lowercase(Locale.getDefault())) {
                                "approved", "disetujui" -> approved++
                                "rejected", "ditolak" -> rejected++
                                "pending", "menunggu" -> pending++
                                "new" -> newCount++
                            }
                        }

                        tvCountApproved.text = approved.toString()
                        tvCountRejected.text = rejected.toString()
                        tvCountPending.text = pending.toString()
                        tvCountNew.text = newCount.toString()
                    }
                    .addOnFailureListener {
                        tvCountApproved.text = "0"
                        tvCountRejected.text = "0"
                        tvCountPending.text = "0"
                        tvCountNew.text = "0"
                    }
            }
    }

    private fun updateStatusHeader(statusRaw: String) {
        val s = statusRaw.toLowerCase(Locale.getDefault())
        when {
            s.contains("approved") || s.contains("setuju") || s.contains("disetujui") -> {
                headerTitle.text = "✅ Disetujui Orang Tua"
                headerTitle.setTextColor(Color.parseColor("#34C759")) // green
            }

            s.contains("rejected") || s.contains("tolak") || s.contains("ditolak") -> {
                headerTitle.text = "❌ Ditolak Orang Tua"
                headerTitle.setTextColor(Color.parseColor("#FF4961")) // red
            }

            else -> {
                headerTitle.text = "⏰ Menunggu Persetujuan"
                headerTitle.setTextColor(Color.parseColor("#FFB800")) // orange
            }
        }
    }

    private fun setupButtonListeners() {
        btnSetujuPenuh.setOnClickListener { updateSubmissionStatus("approved") }
        btnTolak.setOnClickListener { updateSubmissionStatus("rejected") }
        btnTunda.setOnClickListener { updateSubmissionStatus("pending") }
    }

    /**
     * Update status pengajuan. Jika disetujui, tambahkan amount ke saldo anak.
     * Transaksi dilakukan atomically.
     * Menggunakan submissionDocRef untuk memastikan update diarahkan ke dokumen yg benar.
     */
    private fun updateSubmissionStatus(newStatus: String) {
        val docRef = submissionDocRef
        val cid = childId
        if (docRef == null || cid.isNullOrBlank()) {
            Toast.makeText(this, "Tidak ada pengajuan/anak untuk diperbarui", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val userRef = db.collection("users").document(cid)

        db.runTransaction { tr ->
            val subDoc = tr.get(docRef)
            if (!subDoc.exists()) throw Exception("Submission tidak ditemukan")
            val currentStatus = subDoc.getString("status") ?: ""
            val amount = when (val a =
                subDoc.get("amount") ?: subDoc.get("nominal") ?: subDoc.get("amountRp")) {
                is Number -> a.toLong()
                is String -> a.toLongOrNull() ?: 0L
                else -> 0L
            }

            // --- READ user doc within transaction (required) ---
            val userDoc = tr.get(userRef)

            tr.update(
                docRef,
                mapOf("status" to newStatus, "updatedAt" to FieldValue.serverTimestamp())
            )

            if ((newStatus.equals("approved", ignoreCase = true) || newStatus.contains(
                    "setuju",
                    true
                ))
                && !currentStatus.equals("approved", true)
            ) {
                val hasSaldoAnak = userDoc.contains("saldoAnak")
                val hasSaldo = userDoc.contains("saldo")
                val hasBalance = userDoc.contains("balance")

                when {
                    hasSaldoAnak -> {
                        val cur = userDoc.getLong("saldoAnak") ?: 0L
                        tr.update(userRef, "saldoAnak", cur + amount)
                    }

                    hasSaldo -> {
                        val cur = userDoc.getLong("saldo") ?: 0L
                        tr.update(userRef, "saldo", cur + amount)
                    }

                    hasBalance -> {
                        val cur = userDoc.getLong("balance") ?: 0L
                        tr.update(userRef, "balance", cur + amount)
                    }

                    else -> {
                        tr.update(userRef, "saldoAnak", amount)
                    }
                }

                val txCol = userRef.collection("transactions").document()
                val txData = mapOf(
                    "type" to "pemasukan",
                    "category" to "topup_from_parent_on_approve",
                    "amount" to amount,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "note" to "Auto top-up saat approval pengajuan"
                )
                tr.set(txCol, txData)
            }
        }.addOnSuccessListener {
            Toast.makeText(this, "Status diperbarui: $newStatus", Toast.LENGTH_SHORT).show()
            updateStatusHeader(newStatus)

            setResult(Activity.RESULT_OK)
            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(
                this,
                "Gagal memperbarui status: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showNoSubmissionState() {
        tvChildName.text = "Anak"
        tvChildInfo.text = "-"
        tvNominal.text = formatCurrency(0L)
        tvCategory.text = "-"
        tvReason.text = "-"
        headerTitle.text = "Belum Ada Pengajuan"
        headerTitle.setTextColor(Color.DKGRAY)
        childUsername.text = "📊 Keuangan Anak"
    }

    private fun formatCurrency(amount: Long): String {
        val nf = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        return nf.format(amount).replace("Rp", "Rp ").trim()
    }
}