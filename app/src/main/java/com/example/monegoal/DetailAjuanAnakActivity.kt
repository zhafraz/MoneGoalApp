package com.example.monegoal

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*


class DetailAjuanAnakActivity : AppCompatActivity() {

    // UI refs (sesuaikan dengan id di layout)
    private lateinit var btnBack: ImageButton
    private lateinit var tvHeaderTitle: TextView

    // statistik (PASTIKAN kamu menambahkan id ini ke XML)
    private lateinit var tvCountNew: TextView
    private lateinit var tvCountApproved: TextView
    private lateinit var tvCountRejected: TextView
    private lateinit var tvCountPending: TextView

    // detail pengajuan
    private lateinit var tvChildName: TextView
    private lateinit var tvChildInfo: TextView
    private lateinit var tvNominal: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvReason: TextView

    // history
    private lateinit var tvBalanceChild: TextView
    private lateinit var tvSpentThisMonth: TextView

    // pesan + opsi
    private lateinit var inputPesanBalasan: EditText
    private lateinit var cbReward: CheckBox
    private lateinit var cbJadwal: CheckBox
    private lateinit var cbCatatan: CheckBox

    // tombol aksi (CardView clickable)
    private lateinit var btnSetujuPenuh: CardView
    private lateinit var btnTolak: CardView
    private lateinit var btnTunda: CardView

    // firebase
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // data dokumen
    private var submissionId: String? = null
    private var submissionDoc: DocumentSnapshot? = null

    // konfigurasi reward default (bisa ubah)
    private val rewardAmount = 10000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detail_ajuan_anak)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ambil submissionId dari intent
        submissionId = intent.getStringExtra("submissionId")
        if (submissionId == null) {
            Toast.makeText(this, "Submission ID tidak ditemukan", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        bindViews()
        setListeners()
        loadSubmissionAndStats()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)

        tvCountNew = findViewById(R.id.tvCountNew)
        tvCountApproved = findViewById(R.id.tvCountApproved)
        tvCountRejected = findViewById(R.id.tvCountRejected)
        tvCountPending = findViewById(R.id.tvCountPending)

        tvChildName = findViewById(R.id.tvChildName)
        tvChildInfo = findViewById(R.id.tvChildInfo)
        tvNominal = findViewById(R.id.tvNominal)
        tvCategory = findViewById(R.id.tvCategory)
        tvReason = findViewById(R.id.tvReason)

        tvBalanceChild = findViewById(R.id.tvBalanceChild)
        tvSpentThisMonth = findViewById(R.id.tvSpentThisMonth)

        inputPesanBalasan = findViewById(R.id.inputPesanBalasan)
        cbReward = findViewById(R.id.cbReward)
        cbJadwal = findViewById(R.id.cbJadwal)
        cbCatatan = findViewById(R.id.cbCatatan)

        btnSetujuPenuh = findViewById(R.id.btnSetujuPenuh)
        btnTolak = findViewById(R.id.btnTolak)
        btnTunda = findViewById(R.id.btnTunda)
    }

    private fun setListeners() {
        btnBack.setOnClickListener { finish() }

        btnSetujuPenuh.setOnClickListener {
            askForLimitThenApprove()
        }

        btnTolak.setOnClickListener {
            showRejectConfirmation()
        }

        btnTunda.setOnClickListener {
            showDelayDialog()
        }
    }

    private fun loadSubmissionAndStats() {
        val subRef = firestore.collection("submissions").document(submissionId!!)
        subRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                Toast.makeText(this, "Pengajuan tidak ditemukan", Toast.LENGTH_SHORT).show()
                finish()
                return@addOnSuccessListener
            }

            submissionDoc = doc
            bindSubmissionToUI(doc)

            val childId = doc.getString("childId")
            if (!childId.isNullOrBlank()) {
                loadChildSummary(childId)
            }

            loadSubmissionCounts()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Gagal memuat pengajuan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun bindSubmissionToUI(doc: DocumentSnapshot) {
        val childName = doc.getString("childName") ?: doc.getString("childId") ?: "Anak"
        val createdAt = doc.getTimestamp("createdAt")
        val ageAndDate = if (createdAt != null) {
            "${createdAt.toDate()}"
        } else ""
        val amount = doc.getLong("amount") ?: 0L
        val category = doc.getString("category") ?: "-"
        val reason = doc.getString("reason") ?: "-"
        val status = doc.getString("status") ?: "pending"

        tvChildName.text = childName
        tvChildInfo.text = ageAndDate
        tvNominal.text = "Rp %,d".format(amount)
        tvCategory.text = category
        tvReason.text = reason

        // history placeholder (jika dokumen berisi data history, tampilkan)
        tvBalanceChild.text = "Rp ${doc.getLong("childBalance") ?: "..." }"
        tvSpentThisMonth.text = "Rp ${doc.getLong("childSpentThisMonth") ?: "0"}"
    }

    private fun loadChildSummary(childId: String) {
        val userRef = firestore.collection("users").document(childId)
        userRef.get().addOnSuccessListener { udoc ->
            if (udoc.exists()) {
                val balance = udoc.getLong("balance") ?: 0L
                val name = udoc.getString("name") ?: "Anak"
                tvBalanceChild.text = "Rp %,d".format(balance)
                if (tvChildName.text.isNullOrBlank()) tvChildName.text = name
            }
        }

        firestore.collection("submissions")
            .whereEqualTo("childId", childId)
            .whereIn("status", listOf("approved", "rejected"))
            .orderBy("createdAt")
            .get()
            .addOnSuccessListener { query ->
                if (query.isEmpty) {
                    tvSpentThisMonth.text = "Belum ada riwayat"
                    return@addOnSuccessListener
                }

                val totalApproved = query.filter { it.getString("status") == "approved" }
                    .sumOf { it.getLong("amount") ?: 0L }

                val totalRejected = query.filter { it.getString("status") == "rejected" }.size

                tvSpentThisMonth.text =
                    "Disetujui: Rp %,d | Ditolak: %d".format(totalApproved, totalRejected)
            }
            .addOnFailureListener { e ->
                tvSpentThisMonth.text = "Gagal memuat riwayat: ${e.message}"
            }
    }

    private fun loadSubmissionCounts() {
        firestore.collection("submissions")
            .get()
            .addOnSuccessListener { query ->
                var countNew = 0
                var countApproved = 0
                var countRejected = 0
                var countPending = 0

                for (doc in query) {
                    when (doc.getString("status")) {
                        "new" -> countNew++
                        "approved" -> countApproved++
                        "rejected" -> countRejected++
                        "pending" -> countPending++
                    }
                }

                tvCountNew.text = countNew.toString()
                tvCountApproved.text = countApproved.toString()
                tvCountRejected.text = countRejected.toString()
                tvCountPending.text = countPending.toString()
            }
    }

    private fun askForLimitThenApprove() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Aturan tambahan (opsional)")
        builder.setMessage("Masukkan limit pemakaian (contoh: 50000) atau kosongkan untuk tanpa limit")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Limit pemakaian (Rp)"
        builder.setView(input)

        builder.setPositiveButton("Setujui & Simpan") { dialog, _ ->
            val limitStr = input.text.toString().trim()
            val limitValue = if (limitStr.isNotEmpty()) limitStr.toLongOrNull() else null
            dialog.dismiss()
            performApprove(limitValue)
        }
        builder.setNegativeButton("Batal") { d, _ -> d.dismiss() }
        builder.show()
    }

    private fun performApprove(limitPemakaian: Long?) {
        val doc = submissionDoc ?: run {
            Toast.makeText(this, "Data pengajuan belum dimuat", Toast.LENGTH_SHORT).show()
            return
        }
        val childId = doc.getString("childId") ?: run {
            Toast.makeText(this, "childId tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }
        val amount = doc.getLong("amount") ?: 0L
        val submissionRef = firestore.collection("submissions").document(submissionId!!)

        // prepare update map
        val updates = hashMapOf<String, Any>(
            "status" to "approved",
            "parentResponse" to (inputPesanBalasan.text.toString().trim()),
            "parentId" to (auth.currentUser?.uid ?: "unknown"),
            "respondedAt" to FieldValue.serverTimestamp()
        )
        if (limitPemakaian != null) {
            updates["usageLimit"] = limitPemakaian
        }
        if (cbReward.isChecked) {
            updates["rewardGiven"] = true
            updates["rewardAmount"] = rewardAmount
        }

        // Update submission first, then update child's balance in a transaction / atomic increment
        submissionRef.update(updates as Map<String, Any>).addOnSuccessListener {
            // increment child's balance by amount + reward (if any)
            val incrementBy = amount + if (cbReward.isChecked) rewardAmount else 0L
            val childRef = firestore.collection("users").document(childId)
            childRef.update("balance", FieldValue.increment(incrementBy))
                .addOnSuccessListener {
                    // optionally record transaction history: add doc in child transactions collection
                    val tx = hashMapOf(
                        "type" to "credit",
                        "amount" to amount,
                        "note" to "Persetujuan pengajuan oleh orang tua",
                        "timestamp" to FieldValue.serverTimestamp(),
                        "fromParentId" to (auth.currentUser?.uid ?: "parent")
                    )
                    firestore.collection("users").document(childId)
                        .collection("transactions")
                        .add(tx)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Pengajuan disetujui — saldo anak terupdate.", Toast.LENGTH_LONG).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            // walaupun transaksi history gagal, saldo sudah ditambah
                            Toast.makeText(this, "Disetujui, namun gagal menyimpan riwayat: ${e.message}", Toast.LENGTH_LONG).show()
                            finish()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal mengupdate saldo anak: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Gagal update pengajuan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showRejectConfirmation() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Tolak pengajuan?")
        builder.setMessage("Apakah Anda yakin ingin menolak pengajuan ini?")
        builder.setPositiveButton("Tolak") { d, _ ->
            d.dismiss()
            performReject()
        }
        builder.setNegativeButton("Batal") { d, _ -> d.dismiss() }
        builder.show()
    }

    private fun performReject() {
        val submissionRef = firestore.collection("submissions").document(submissionId!!)
        val updates = hashMapOf<String, Any>(
            "status" to "rejected",
            "parentResponse" to inputPesanBalasan.text.toString().trim(),
            "parentId" to (auth.currentUser?.uid ?: "unknown"),
            "respondedAt" to FieldValue.serverTimestamp()
        )
        submissionRef.update(updates as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(this, "Pengajuan ditolak.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menolak: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showDelayDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Tunda pengajuan")
        builder.setMessage("Pilih berapa hari ingin menunda pengingat:")

        // input number of days
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Jumlah hari (contoh: 3)"
        builder.setView(input)

        builder.setPositiveButton("Tunda") { d, _ ->
            val days = input.text.toString().toIntOrNull() ?: 0
            d.dismiss()
            performDelay(days)
        }

        builder.setNegativeButton("Batal") { d, _ -> d.dismiss() }
        builder.show()
    }

    private fun performDelay(days: Int) {
        val subRef = firestore.collection("submissions").document(submissionId!!)
        val remindAt = Timestamp(Date(System.currentTimeMillis() + (days.toLong() * 24L * 3600L * 1000L)))
        val updates = hashMapOf<String, Any>(
            "status" to "pending",
            "parentResponse" to inputPesanBalasan.text.toString().trim(),
            "remindAt" to remindAt,
            "parentId" to (auth.currentUser?.uid ?: "unknown"),
            "respondedAt" to FieldValue.serverTimestamp()
        )
        subRef.update(updates as Map<String, Any>).addOnSuccessListener {
            Toast.makeText(this, "Pengajuan ditunda selama $days hari.", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Gagal menunda pengajuan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}