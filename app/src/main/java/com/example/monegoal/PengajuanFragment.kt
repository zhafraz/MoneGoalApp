package com.example.monegoal

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.monegoal.ui.anak.AjukanDanaFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

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
    private lateinit var tvTotalLimit: TextView

    // Tombol/Link untuk membuka daftar semua pengajuan
    private var tvSeeAll: TextView? = null

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var resolvedChildId: String? = null
    private var resolvedChildName: String? = null

    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))

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
        tvTotalLimit = view.findViewById(R.id.tvTotalLimit)

        setupClicks()

        loadApprovedLimit()

        // tambahkan TextView "Lihat Semua Pengajuan" di bawah status (programmatically)
        insertSeeAllLink(view as ViewGroup)

        val argChildId = arguments?.getString("childId")
        if (!argChildId.isNullOrBlank()) {
            resolvedChildId = argChildId
            loadChildOverview(argChildId)
        } else {
            resolveChildThenLoad()
        }

        return view
    }

    private fun loadApprovedLimit() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Baca approvedLimit (bisa number atau string)
                    val approvedLimit = when (val value = doc.get("approvedLimit")) {
                        is Number -> value.toLong()
                        is String -> value.toLongOrNull() ?: 0L
                        else -> 0L
                    }

                    // Format ke Rupiah (contoh: Rp 2.500)
                    val formattedLimit =
                        "Rp " + NumberFormat.getNumberInstance(Locale("in", "ID")).format(approvedLimit)

                    tvTotalLimit.text = formattedLimit
                } else {
                    tvTotalLimit.text = "Rp 0"
                }
            }
            .addOnFailureListener {
                tvTotalLimit.text = "Rp 0"
            }
    }

    private fun insertSeeAllLink(container: ViewGroup) {
        // jangan duplikat jika sudah dibuat
        if (tvSeeAll != null) return

        val link = TextView(requireContext()).apply {
            text = "Lihat Semua Pengajuan"
            textSize = 14f
            setPadding(24, 18, 24, 6)
            try {
                setTextColor(resources.getColor(R.color.light_blue, null))
            } catch (_: Exception) {
                setTextColor(resources.getColor(android.R.color.holo_blue_dark))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val cid = resolvedChildId
                if (cid.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Anak belum terdeteksi.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                fetchAllSubmissionsForChild(cid) { list ->
                    if (list.isEmpty()) {
                        Toast.makeText(requireContext(), "Belum ada pengajuan.", Toast.LENGTH_SHORT).show()
                    } else {
                        showSubmissionsDialog(list)
                    }
                }
            }
        }

        val statusParent = (pbStatus.parent as? ViewGroup) ?: (tvStatusTitle.parent as? ViewGroup)

        if (statusParent != null) {
            var insertIndex = -1
            for (i in 0 until statusParent.childCount) {
                if (statusParent.getChildAt(i) === pbStatus) {
                    insertIndex = i + 1
                    break
                }
                if (statusParent.getChildAt(i) === tvStatusTitle) {
                    insertIndex = i + 1
                }
            }

            if (insertIndex >= 0 && insertIndex <= statusParent.childCount) {
                statusParent.addView(link, insertIndex)
            } else {
                statusParent.addView(link)
            }
        } else {
            try {
                container.addView(link)
            } catch (e: Exception) {
                val root = requireActivity().findViewById<ViewGroup>(android.R.id.content)
                root.addView(link)
            }
        }

        tvSeeAll = link
    }

    private fun setupClicks() {
        cardAjukanDana.setOnClickListener {
            // sebelum membuka AjukanDanaFragment, cek saldo anak
            checkChildBeforeOpenAjukan()
        }
        cardPembayaran.setOnClickListener {
            val ctx = requireContext()
            val intent = Intent(ctx, ScannerActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Pastikan resolvedChildId ada dan saldo anak memadai sebelum membuka form AjukanDanaFragment.
     * Jika saldo <= 0 maka tolak pengajuan (sesuai permintaan).
     * Jika saldo > 0 -> buka AjukanDanaFragment dan kirim arg childId, childName, childBalance.
     */
    private fun checkChildBeforeOpenAjukan() {
        val cid = resolvedChildId
        if (cid.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Anak belum terdeteksi.", Toast.LENGTH_SHORT).show()
            return
        }

        // ambil saldo terbaru
        db.collection("users").document(cid)
            .get()
            .addOnSuccessListener { doc ->
                val balance = doc.getLong("saldoAnak") ?: doc.getLong("saldo") ?: doc.getLong("balance") ?: 0L
                val name = doc.getString("name") ?: resolvedChildName ?: "Anak"
                if (balance <= 0L) {
                    // Tampilkan dialog / toast menolak pengajuan
                    AlertDialog.Builder(requireContext())
                        .setTitle("Tidak Bisa Mengajukan")
                        .setMessage("Saldo ${name} saat ini ${formatCurrency(balance)}. Pengajuan tidak bisa dibuat karena saldo tidak mencukupi.")
                        .setPositiveButton("Tutup", null)
                        .show()
                    return@addOnSuccessListener
                }

                // buka AjukanDanaFragment, kirim argumen childId, childName, childBalance
                val frag = AjukanDanaFragment()
                frag.arguments = Bundle().apply {
                    putString("childId", cid)
                    putString("childName", name)
                    putLong("childBalance", balance)
                }

                // gunakan MainActivity.navigateTo bila tersedia agar bottom nav ikut update
                val main = activity as? MainActivity
                if (main != null) {
                    main.navigateTo(frag /*, optionally menu id if wanted */)
                } else {
                    // fallback: gunakan fragment manager
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, frag)
                        .addToBackStack(null)
                        .commit()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Gagal memeriksa saldo anak.", Toast.LENGTH_SHORT).show()
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

    /**
     * Ambil *satu* pengajuan terbaru (fallback ke beberapa koleksi).
     * Tetap menampilkan ringkasan seperti sebelumnya.
     */
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

    // -----------------------------------------------------------
    // Bagian baru: fetch semua pengajuan untuk child, gabungkan dari beberapa koleksi
    // -----------------------------------------------------------

    data class SimpleSubmission(
        val id: String,
        val title: String,
        val amount: Long,
        val status: String,
        val reason: String,
        val createdAtMillis: Long
    )

    private fun fetchAllSubmissionsForChild(childId: String, callback: (List<SimpleSubmission>) -> Unit) {
        val collected = mutableListOf<SimpleSubmission>()
        var pending = 0
        var finished = false

        fun checkDone() {
            pending--
            if (pending <= 0 && !finished) {
                finished = true
                val sorted = collected.sortedBy { it.createdAtMillis }
                callback(sorted)
            }
        }

        fun processDocs(docs: List<Map<String, Any?>>) {
            for (map in docs) {
                val id = (map["id"] as? String) ?: (map["docId"] as? String) ?: UUID.randomUUID().toString()
                val title = (map["title"] ?: map["category"] ?: map["keperluan"] ?: "Pengajuan").toString()
                val amount = when (val v = map["amount"] ?: map["nominal"] ?: map["amountRp"]) {
                    is Number -> v.toLong()
                    is String -> v.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val status = (map["status"] ?: map["state"] ?: "menunggu").toString()
                val reason = (map["reason"] ?: map["alasan"] ?: map["purpose"] ?: map["note"] ?: "-").toString()

                val tsObj = map["createdAt"] ?: map["tanggal"] ?: map["date"] ?: map["timestamp"]
                val millis = when (tsObj) {
                    is com.google.firebase.Timestamp -> tsObj.toDate().time
                    is com.google.firebase.Timestamp -> tsObj.toDate().time
                    is Number -> tsObj.toLong()
                    is Long -> tsObj
                    is Date -> tsObj.time
                    else -> 0L
                }

                collected.add(SimpleSubmission(id, title, amount, status, reason, millis))
            }
        }

        pending++
        db.collection("submissions")
            .whereEqualTo("childId", childId)
            .get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents.map { doc ->
                    val m = doc.data?.toMutableMap() ?: mutableMapOf()
                    m["id"] = doc.id
                    m["createdAt"] = doc.get("createdAt") ?: doc.get("tanggal") ?: doc.get("date")
                    m
                }
                processDocs(docs)
                checkDone()
            }
            .addOnFailureListener { checkDone() }

        val pengajuanChecks = listOf("childId", "anakId", "userId", "anak")
        for (field in pengajuanChecks) {
            pending++
            db.collection("pengajuan_dana")
                .whereEqualTo(field, childId)
                .get()
                .addOnSuccessListener { snap ->
                    val docs = snap.documents.map { doc ->
                        val m = doc.data?.toMutableMap() ?: mutableMapOf()
                        m["id"] = doc.id
                        m["createdAt"] = doc.get("createdAt") ?: doc.get("tanggal") ?: doc.get("date") ?: doc.get("tanggal")
                        m
                    }
                    processDocs(docs)
                    checkDone()
                }
                .addOnFailureListener { checkDone() }
        }

        pending++
        db.collection("users").document(childId)
            .collection("submissions")
            .get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents.map { doc ->
                    val m = doc.data?.toMutableMap() ?: mutableMapOf()
                    m["id"] = doc.id
                    m["createdAt"] = doc.get("createdAt") ?: doc.get("tanggal") ?: doc.get("date")
                    m
                }
                processDocs(docs)
                checkDone()
            }
            .addOnFailureListener { checkDone() }
    }

    private fun showSubmissionsDialog(list: List<SimpleSubmission>) {
        val lines = list.map { s ->
            val date = if (s.createdAtMillis > 0) dateFmt.format(Date(s.createdAtMillis)) else "-"
            val amt = formatCurrency(s.amount)
            val status = s.status
            "$date — $amt — ${status.replaceFirstChar { it.uppercase() }} — ${s.title}"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Semua Pengajuan (${list.size})")
            .setItems(lines.toTypedArray(), null)
            .setPositiveButton("Tutup", null)
            .show()
    }
}