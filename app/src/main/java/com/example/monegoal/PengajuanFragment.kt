package com.example.monegoal

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.monegoal.models.Submission
import com.example.monegoal.ui.anak.AjukanDanaFragment
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class PengajuanFragment : Fragment() {

    // header / saldo
    private lateinit var tvTotalSaldo: TextView
    private lateinit var tvPemasukan: TextView
    private lateinit var tvPengeluaran: TextView
    private lateinit var tvTotalLimit: TextView

    // tombol
    private lateinit var cardAjukanDana: FrameLayout
    private lateinit var cardPembayaran: FrameLayout

    // RecyclerView status pengajuan
    private var rvStatusPengajuan: RecyclerView? = null
    private var statusAdapter: StatusAdapter? = null
    private val statusList = mutableListOf<Submission>()

    // "lihat semua" link (opsional)
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

        // find views (safe)
        tvTotalSaldo = view.findViewById(R.id.tvTotalSaldo)
        tvPemasukan = view.findViewById(R.id.tvPemasukan)
        tvPengeluaran = view.findViewById(R.id.tvPengeluaran)
        tvTotalLimit = view.findViewById(R.id.tvTotalLimit)

        cardAjukanDana = view.findViewById(R.id.cardAjukanDana)
        cardPembayaran = view.findViewById(R.id.cardPembayaran)

        // RecyclerView (pastikan id ada di layout fragment_pengajuan)
        rvStatusPengajuan = view.findViewByIdOrNull(R.id.rvStatusPengajuan)
        rvStatusPengajuan?.layoutManager = LinearLayoutManager(requireContext())
        // biarkan adapter diinisialisasi sekali
        statusAdapter = StatusAdapter(statusList) { submission ->
            val intent = Intent(requireContext(), DetailAjuanAnakActivity::class.java)
            intent.putExtra("submissionId", submission.id)
            startActivity(intent)
        }
        rvStatusPengajuan?.adapter = statusAdapter
        // penting jika RecyclerView ada di dalam ScrollView
        rvStatusPengajuan?.isNestedScrollingEnabled = false
        rvStatusPengajuan?.setHasFixedSize(false)

        setupClicks()
        loadApprovedLimit()

        // tambah link "Lihat Semua"
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

    // safe findViewById helper extension
    private fun <T : View> View.findViewByIdOrNull(id: Int): T? =
        try { findViewById(id) } catch (e: Exception) { null }

    private fun setupClicks() {
        cardAjukanDana.setOnClickListener {
            checkChildBeforeOpenAjukan()
        }
        cardPembayaran.setOnClickListener {
            val ctx = requireContext()
            val intent = Intent(ctx, ScannerActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadApprovedLimit() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val approved = when (val v = doc.get("approvedLimit")) {
                    is Number -> v.toLong()
                    is String -> v.toString().toLongOrNull() ?: 0L
                    else -> 0L
                }
                tvTotalLimit.text = formatCurrency(approved)
            }
            .addOnFailureListener {
                tvTotalLimit.text = formatCurrency(0L)
            }
    }

    private fun insertSeeAllLink(container: ViewGroup) {
        if (tvSeeAll != null) return

        val link = TextView(requireContext()).apply {
            text = "Lihat Semua Pengajuan"
            textSize = 14f
            setPadding(24, 18, 24, 6)
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

        try {
            container.addView(link)
        } catch (e: Exception) {
            requireActivity().findViewById<ViewGroup>(android.R.id.content).addView(link)
        }
        tvSeeAll = link
    }

    /**
     * sebelum buka form ajukan, pastikan saldo anak > 0
     */
    private fun checkChildBeforeOpenAjukan() {
        val cid = resolvedChildId
        if (cid.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Anak belum terdeteksi.", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").document(cid)
            .get()
            .addOnSuccessListener { doc ->
                val balance = doc.getLong("saldoAnak") ?: doc.getLong("saldo") ?: doc.getLong("balance") ?: 0L
                val name = doc.getString("name") ?: resolvedChildName ?: "Anak"
                if (balance <= 0L) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Tidak Bisa Mengajukan")
                        .setMessage("Saldo ${name} saat ini ${formatCurrency(balance)}. Pengajuan tidak bisa dibuat karena saldo tidak mencukupi.")
                        .setPositiveButton("Tutup", null)
                        .show()
                    return@addOnSuccessListener
                }

                val frag = AjukanDanaFragment()
                frag.arguments = Bundle().apply {
                    putString("childId", cid)
                    putString("childName", name)
                    putLong("childBalance", balance)
                }

                val main = activity as? MainActivity
                if (main != null) {
                    main.navigateTo(frag)
                } else {
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
                                .addOnFailureListener { loadChildOverview(user.uid) }
                        } else {
                            loadChildOverview(user.uid)
                        }
                    }
                }
                .addOnFailureListener { loadChildOverview(user.uid) }
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
                loadLatestSubmission(childId, name) // optional summary
                loadStatusList(childId) // load ke RecyclerView (maks 5)
            }
            .addOnFailureListener {
                tvTotalSaldo.text = formatCurrency(0L)
                loadTransactionsSummary(childId)
                loadLatestSubmission(childId, null)
                loadStatusList(childId)
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
     * Ambil pengajuan dari beberapa sumber, normalisasi, lalu callback dengan list
     * Kita ambil maksimal 5 per sumber lalu merge, dedup, ambil 5 terbaru.
     */
    private fun fetchAllSubmissionsForChild(childId: String, callback: (List<Submission>) -> Unit) {
        val collected = mutableListOf<Submission>()
        var pending = 0
        var finished = false

        fun checkDone() {
            pending--
            if (pending <= 0 && !finished) {
                finished = true
                // dedup by id, keep latest by createdAt
                val map = linkedMapOf<String, Submission>()
                collected.sortedByDescending { it.createdAt }.forEach { s ->
                    if (!map.containsKey(s.id)) map[s.id] = s
                }
                val merged = map.values.toList().sortedByDescending { it.createdAt }
                callback(merged)
            }
        }

        fun processDocs(docs: List<Map<String, Any?>>) {
            for (m in docs) {
                val id = (m["id"] as? String) ?: UUID.randomUUID().toString()
                val title = (m["title"] ?: m["purpose"] ?: m["keperluan"] ?: m["alasan"] ?: m["note"] ?: "").toString()
                val amount = when (val v = m["amount"] ?: m["nominal"] ?: m["amountRp"]) {
                    is Number -> v.toLong()
                    is String -> v.toString().toLongOrNull() ?: 0L
                    else -> 0L
                }
                val status = (m["status"] ?: m["state"] ?: "menunggu").toString()
                val childName = (m["childName"] ?: m["anak"] ?: "").toString()
                val childIdFromDoc = (m["childId"] ?: m["anakId"] ?: m["userId"])?.toString() ?: ""

                val tsObj = m["createdAt"] ?: m["tanggal"] ?: m["date"] ?: m["timestamp"]
                val createdAtMillis = when (tsObj) {
                    is Timestamp -> tsObj.toDate().time
                    is Number -> tsObj.toLong()
                    is Long -> tsObj
                    is Date -> tsObj.time
                    else -> 0L
                }

                collected.add(
                    Submission(
                        id = id,
                        childId = if (childIdFromDoc.isNotBlank()) childIdFromDoc else childId,
                        childName = childName,
                        title = if (title.isBlank()) (m["reason"] ?: m["alasan"] ?: "").toString() else title,
                        amount = amount,
                        status = status,
                        createdAt = createdAtMillis
                    )
                )
            }
        }

        // Queries: ambil max 5 tiap sumber
        val queries = listOf(
            Triple("submissions", "childId", 5L),
            Triple("pengajuan_dana", "childId", 5L),
            Triple("pengajuan_dana", "anakId", 5L),
            Triple("pengajuan_dana", "userId", 5L)
        )

        pending = queries.size + 1

        for ((col, field, limit) in queries) {
            db.collection(col)
                .whereEqualTo(field, childId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener { snap ->
                    val docs = snap.documents.mapNotNull { it.data?.toMutableMap()?.also { m -> m["id"] = it.id } }
                    processDocs(docs)
                    checkDone()
                }
                .addOnFailureListener {
                    // fallback without orderBy
                    db.collection(col)
                        .whereEqualTo(field, childId)
                        .limit(limit)
                        .get()
                        .addOnSuccessListener { snap2 ->
                            val docs2 = snap2.documents.mapNotNull { it.data?.toMutableMap()?.also { m -> m["id"] = it.id } }
                            processDocs(docs2)
                            checkDone()
                        }
                        .addOnFailureListener { checkDone() }
                }
        }

        // nested users/{child}/submissions
        db.collection("users").document(childId)
            .collection("submissions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents.mapNotNull { it.data?.toMutableMap()?.also { m -> m["id"] = it.id } }
                processDocs(docs)
                checkDone()
            }
            .addOnFailureListener { checkDone() }
    }

    private fun showSubmissionsDialog(list: List<Submission>) {
        val lines = list.map { s ->
            val date = if (s.createdAt > 0) dateFmt.format(Date(s.createdAt)) else "-"
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

    /**
     * Load last 5 submissions (merged) and show in RecyclerView
     */
    private fun loadStatusList(childId: String) {
        fetchAllSubmissionsForChild(childId) { list ->
            // ambil maksimal 5 terbaru
            val last5 = list.sortedByDescending { it.createdAt }.take(5)
            statusList.clear()
            statusList.addAll(last5)
            requireActivity().runOnUiThread {
                statusAdapter?.notifyDataSetChanged()
            }
        }
    }

    /**
     * Ambil 1 pengajuan terbaru untuk ringkasan (dipanggil dari loadChildOverview).
     * Kosongkan (atau implement jika ingin).
     */
    private fun loadLatestSubmission(childId: String, childName: String?) {
        // tidak wajib; biarkan kosong agar tidak mengganggu RecyclerView
    }

    private fun showNoDataState() {
        tvTotalSaldo.text = formatCurrency(0L)
        tvPemasukan.text = formatCurrency(0L)
        tvPengeluaran.text = formatCurrency(0L)
    }

    private fun formatCurrency(amount: Long): String {
        val nf = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        return nf.format(amount).replace("Rp", "Rp ").trim()
    }

    private fun encodeEmailToId(email: String): String {
        return email.replace("@", "_at_").replace(".", "_dot_")
    }

    // ---------------- Adapter ----------------
    class StatusAdapter(
        private val items: List<Submission>,
        private val onClick: (Submission) -> Unit
    ) : RecyclerView.Adapter<StatusAdapter.Holder>() {

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvIcon: TextView = itemView.findViewById(R.id.tvStatusIcon)
            val tvTitle: TextView = itemView.findViewById(R.id.tvStatusTitle)
            val tvSub: TextView = itemView.findViewById(R.id.tvStatusSubtitle)
            val tvNominal: TextView = itemView.findViewById(R.id.tvNominal)
            val tvKeperluan: TextView = itemView.findViewById(R.id.tvKeperluan)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_status_pengajuan, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val s = items[position]
            val status = s.status.lowercase(Locale.getDefault())

            when {
                status.contains("approved") || status.contains("setuju") || status.contains("disetujui") -> {
                    holder.tvTitle.text = "Disetujui Orang Tua"
                    holder.tvSub.text = "Pengajuan berhasil disetujui"
                    holder.tvIcon.text = "✅"
                }
                status.contains("pending") || status.contains("menunggu") -> {
                    holder.tvTitle.text = "Menunggu Persetujuan"
                    holder.tvSub.text = "Pengajuan masih diproses"
                    holder.tvIcon.text = "⏳"
                }
                status.contains("rejected") || status.contains("ditolak") || status.contains("tolak") -> {
                    holder.tvTitle.text = "Ditolak"
                    holder.tvSub.text = "Pengajuan tidak disetujui"
                    holder.tvIcon.text = "❌"
                }
                else -> {
                    holder.tvTitle.text = s.status.ifBlank { "Pengajuan" }
                    holder.tvSub.text = ""
                    holder.tvIcon.text = "ℹ️"
                }
            }

            holder.tvNominal.text = formatRupiah(s.amount)
            holder.tvKeperluan.text = s.title.ifBlank { s.childName }
        }

        override fun getItemCount(): Int = items.size

        private fun formatRupiah(amount: Long): String {
            val nf = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            return nf.format(amount).replace("Rp", "Rp ").trim()
        }
    }
}