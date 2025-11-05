package com.example.monegoal

import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.monegoal.adapter.SubmissionAdapter
import com.example.monegoal.models.Submission
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

class HomeOrtuActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var tvUserName: TextView
    private lateinit var tvUserRole: TextView
    private lateinit var tvTotalBalance: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoSubmissions: TextView

    private lateinit var cardDetailPengajuan: CardView
    private lateinit var cardTopup: CardView
    private lateinit var btnLogout: ImageView

    private lateinit var adapter: SubmissionAdapter
    private val submissionList = mutableListOf<Submission>()

    // launcher untuk membuka DetailAjuanAnakActivity dan refresh saat RESULT_OK
    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                loadParentHomeData()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_ortu)

        // safe edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        bindViews()
        setupRecycler()
        setupNavigation()

        loadParentHomeData()
    }

    private fun bindViews() {
        tvUserName = findViewById(R.id.tvUserName)
        tvUserRole = findViewById(R.id.tvUserRole)
        tvTotalBalance = findViewById(R.id.tvTotalChildBalance)
        tvPendingCount = findViewById(R.id.tvPendingSubmissions)
        recyclerView = findViewById(R.id.recyclerViewSubmissions)
        tvNoSubmissions = findViewById(R.id.tvNoSubmissions)
        cardDetailPengajuan = findViewById(R.id.cardSetTask)
        cardTopup = findViewById(R.id.cardTopup)
        // perbaikan: gunakan findViewById di Activity, bukan `view`
        btnLogout = findViewById(R.id.btnLogOut)
    }

    private fun setupRecycler() {
        adapter = SubmissionAdapter(submissionList) { submission ->
            val intent = Intent(this, DetailAjuanAnakActivity::class.java).apply {
                putExtra("submissionId", submission.id)
                putExtra("childId", submission.childId)
            }
            detailLauncher.launch(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupNavigation() {
        cardDetailPengajuan.setOnClickListener {
            if (submissionList.isNotEmpty()) {
                val s = submissionList.minByOrNull { it.createdAt }!!
                val intent = Intent(this, DetailAjuanAnakActivity::class.java).apply {
                    putExtra("submissionId", s.id)
                    putExtra("childId", s.childId)
                }
                detailLauncher.launch(intent)
            } else {
                startActivity(Intent(this, DetailAjuanAnakActivity::class.java))
            }
        }

        cardTopup.setOnClickListener {
            // saat cardTopup diklik, ambil daftar anak dulu lalu buka TopupActivity sesuai pilihan
            fetchChildrenForParent { children ->
                when {
                    children.isEmpty() -> {
                        Toast.makeText(
                            this,
                            "Tidak ada akun anak terhubung. Anda dapat menambah anak di profil.",
                            Toast.LENGTH_LONG
                        ).show()
                        startActivity(Intent(this, TopupActivity::class.java))
                    }
                    children.size == 1 -> {
                        val (id, name) = children[0]
                        val i = Intent(this, TopupActivity::class.java).apply {
                            putExtra("childId", id)
                            putExtra("childName", name)
                        }
                        startActivity(i)
                    }
                    else -> {
                        val names = children.map { it.second.ifBlank { it.first } }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Pilih anak untuk top-up")
                            .setItems(names) { _, which ->
                                val (id, name) = children[which]
                                val i = Intent(this, TopupActivity::class.java).apply {
                                    putExtra("childId", id)
                                    putExtra("childName", name)
                                }
                                startActivity(i)
                            }
                            .setNegativeButton("Batal", null)
                            .show()
                    }
                }
            }
        }

        // pasang listener logout di sini (di dalam method, setelah findViewById)
        btnLogout.setOnClickListener {
            // jika ingin konfirmasi sebelum logout
            AlertDialog.Builder(this)
                .setTitle("Keluar")
                .setMessage("Apakah Anda yakin ingin keluar?")
                .setPositiveButton("Ya") { _, _ ->
                    auth.signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadParentHomeData()
    }

    /**
     * Load summary parent home:
     * - tampilkan nama parent (jika ada)
     * - hit semua anak yang terhubung (gabungan beberapa query)
     * - hit submissions untuk anak-anak tersebut
     */
    private fun loadParentHomeData() {
        val intentEmail = intent.getStringExtra("parentEmail")
        val parentEmail = intentEmail ?: auth.currentUser?.email

        if (parentEmail.isNullOrBlank()) {
            tvUserName.text = "Orang Tua"
            tvUserRole.text = "Pengelola Keluarga (0 Anak)"
            tvTotalBalance.text = formatCurrency(0L)
            tvPendingCount.text = "0"
            tvNoSubmissions.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            return
        }

        val parentId = encodeEmailToId(parentEmail)

        // ambil nama parent jika ada di collection 'parents'
        firestore.collection("parents").document(parentId)
            .get()
            .addOnSuccessListener { parentSnap ->
                val parentName = parentSnap.getString("name") ?: parentEmail.substringBefore("@")
                tvUserName.text = parentName
            }
            .addOnFailureListener {
                tvUserName.text = parentEmail.substringBefore("@")
            }

        // Ambil daftar anak: gabungkan hasil dari beberapa bentuk query
        val childDocs = mutableMapOf<String, DocumentSnapshot>()

        val parentUid = auth.currentUser?.uid
        val candidateParents = mutableListOf<String>().apply {
            parentUid?.let { add(it) }
            add(parentId) // encoded with RegisterActivity style
            add(parentEmail)
            add(parentEmail.replace("@", "_at_").replace(".", "_dot_")) // legacy variant
        }.filter { it.isNotBlank() }.distinct()

        fun runNextQuery(index: Int) {
            if (index >= candidateParents.size) {
                handleChildrenDocs(childDocs.values.toList())
                return
            }
            val v = candidateParents[index]
            firestore.collection("users")
                .whereArrayContains("parents", v)
                .get()
                .addOnSuccessListener { snap ->
                    for (d in snap.documents) childDocs[d.id] = d
                    runNextQuery(index + 1)
                }
                .addOnFailureListener {
                    runNextQuery(index + 1)
                }
        }

        runNextQuery(0)
    }

    /**
     * Mengembalikan daftar (childId, childName) melalui callback.
     * Query mencoba beberapa kemungkinan parent identifier:
     * - UID parent (jika ada)
     * - encoded email (RegisterActivity style: replace(".", "_").replace("@", "_at_"))
     * - plain email
     * - legacy encoded (replace(".", "_dot_")) — bila ada
     */
    private fun fetchChildrenForParent(callback: (List<Pair<String, String>>) -> Unit) {
        val parentEmail = intent.getStringExtra("parentEmail") ?: auth.currentUser?.email
        val parentUid = auth.currentUser?.uid

        if (parentEmail.isNullOrBlank() && parentUid.isNullOrBlank()) {
            callback(emptyList())
            return
        }

        val candidateParents = mutableListOf<String>()
        parentUid?.let { candidateParents.add(it) }
        parentEmail?.let {
            candidateParents.add(encodeEmailToId(it)) // main encoding: "." -> "_" ; "@" -> "_at_"
            candidateParents.add(it) // raw email
            candidateParents.add(it.replace("@", "_at_").replace(".", "_dot_")) // legacy
        }

        val toTry = candidateParents.filter { it.isNotBlank() }.distinct()
        if (toTry.isEmpty()) {
            callback(emptyList())
            return
        }

        val collected = mutableMapOf<String, String>() // id -> name
        var remaining = toTry.size

        for (p in toTry) {
            firestore.collection("users")
                .whereArrayContains("parents", p)
                .get()
                .addOnSuccessListener { snap ->
                    for (d in snap.documents) {
                        val name = d.getString("name") ?: d.id
                        collected[d.id] = name
                    }
                    remaining--
                    if (remaining <= 0) {
                        val result = collected.map { it.key to it.value }
                        callback(result)
                    }
                }
                .addOnFailureListener {
                    remaining--
                    if (remaining <= 0) {
                        val result = collected.map { it.key to it.value }
                        callback(result)
                    }
                }
        }
    }

    private fun handleChildrenDocs(docs: List<DocumentSnapshot>) {
        val totalChildren = docs.size
        var totalBalance = 0L
        val childIds = mutableListOf<String>()

        for (doc in docs) {
            val bal = doc.getLong("saldoAnak") ?: doc.getLong("saldo") ?: doc.getLong("balance") ?: 0L
            totalBalance += bal
            childIds.add(doc.id)
        }

        tvUserRole.text = "Pengelola Keluarga ($totalChildren Anak)"
        tvTotalBalance.text = formatCurrency(totalBalance)

        if (childIds.isNotEmpty()) {
            loadSubmissionsForChildren(childIds)
        } else {
            showEmptyState()
        }
    }

    // helper chunk karena whereIn max 10
    private fun <T> chunkList(list: List<T>, chunkSize: Int): List<List<T>> {
        val out = mutableListOf<List<T>>()
        var i = 0
        while (i < list.size) {
            val end = min(i + chunkSize, list.size)
            out.add(list.subList(i, end))
            i += chunkSize
        }
        return out
    }

    private fun loadSubmissionsForChildren(childIds: List<String>) {
        if (childIds.isEmpty()) {
            showEmptyState()
            return
        }

        submissionList.clear()

        val idChunks = chunkList(childIds, 10)
        var pendingQueries = 0
        val collectedDocs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()

        fun onQueryDone() {
            pendingQueries--
            if (pendingQueries <= 0) {
                val distinct = collectedDocs
                    .distinctBy { it.id }
                    .filter { doc ->
                        val raw = (doc.getString("status") ?: doc.getString("state") ?: "").lowercase()
                        raw.contains("pending") || raw.contains("new") || raw.contains("menunggu")
                    }
                    .sortedBy { doc ->
                        val ts = doc.get("createdAt") ?: doc.get("tanggal") ?: doc.get("date") ?: doc.get("created_at")
                        when (ts) {
                            is com.google.firebase.Timestamp -> ts.toDate().time
                            is java.util.Date -> ts.time
                            is Number -> ts.toLong()
                            else -> Long.MAX_VALUE
                        }
                    }

                submissionList.clear()
                for (doc in distinct) {
                    val amount = doc.getLong("nominal")
                        ?: doc.getLong("amount")
                        ?: (doc.get("amount") as? Number)?.toLong()
                        ?: 0L

                    val createdAtMillis: Long = when (val ts = doc.get("createdAt") ?: doc.get("tanggal") ?: doc.get("date")) {
                        is com.google.firebase.Timestamp -> ts.toDate().time
                        is java.util.Date -> ts.time
                        is Number -> ts.toLong()
                        else -> System.currentTimeMillis()
                    }

                    val childId = doc.getString("childId")
                        ?: doc.getString("userId")
                        ?: doc.getString("anakId")
                        ?: ""

                    val childName = doc.getString("childName")
                        ?: doc.getString("anak")
                        ?: doc.getString("childName")
                        ?: "Anak"

                    val title = doc.getString("title")
                        ?: doc.getString("category")
                        ?: doc.getString("keperluan")
                        ?: "Pengajuan"

                    submissionList.add(
                        Submission(
                            id = doc.id,
                            childId = childId,
                            childName = childName,
                            title = title,
                            amount = amount,
                            status = doc.getString("status") ?: doc.getString("state") ?: "unknown",
                            createdAt = createdAtMillis
                        )
                    )
                }

                adapter.updateData(submissionList)

                val pendingCount = submissionList.count {
                    val s = it.status.lowercase()
                    s.contains("pending") || s.contains("new") || s.contains("menunggu")
                }
                tvPendingCount.text = pendingCount.toString()

                if (submissionList.isEmpty()) showEmptyState() else showRecycler()
            }
        }

        for (chunk in idChunks) {
            pendingQueries++
            firestore.collection("submissions")
                .whereIn("childId", chunk)
                .get()
                .addOnSuccessListener { snap ->
                    collectedDocs.addAll(snap.documents)
                    onQueryDone()
                }
                .addOnFailureListener { onQueryDone() }

            pendingQueries++
            firestore.collection("pengajuan_dana")
                .whereIn("childId", chunk)
                .get()
                .addOnSuccessListener { snap ->
                    collectedDocs.addAll(snap.documents)
                    onQueryDone()
                }
                .addOnFailureListener { onQueryDone() }

            pendingQueries++
            firestore.collection("pengajuan_dana")
                .whereIn("anakId", chunk)
                .get()
                .addOnSuccessListener { snap ->
                    collectedDocs.addAll(snap.documents)
                    onQueryDone()
                }
                .addOnFailureListener { onQueryDone() }

            pendingQueries++
            firestore.collection("pengajuan_dana")
                .whereIn("userId", chunk)
                .get()
                .addOnSuccessListener { snap ->
                    collectedDocs.addAll(snap.documents)
                    onQueryDone()
                }
                .addOnFailureListener { onQueryDone() }

            pendingQueries++
            firestore.collection("pengajuan_dana")
                .whereIn("anak", chunk)
                .get()
                .addOnSuccessListener { snap ->
                    collectedDocs.addAll(snap.documents)
                    onQueryDone()
                }
                .addOnFailureListener { onQueryDone() }
        }
    }

    private fun showEmptyState() {
        tvNoSubmissions.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvPendingCount.text = "0"
    }

    private fun showRecycler() {
        tvNoSubmissions.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    private fun formatCurrency(amount: Long): String {
        val formatted = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(amount)
        return formatted.replace("Rp", "Rp ").trim()
    }

    /**
     * Encoding email yang konsisten dengan RegisterActivity:
     * register -> email.replace(".", "_").replace("@", "_at_")
     */
    private fun encodeEmailToId(email: String): String {
        return email.replace(".", "_").replace("@", "_at_")
    }
}