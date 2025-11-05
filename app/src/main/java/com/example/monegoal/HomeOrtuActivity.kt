package com.example.monegoal

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
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

    private lateinit var adapter: SubmissionAdapter
    private val submissionList = mutableListOf<Submission>()

    // launcher untuk membuka DetailAjuanAnakActivity dan refresh saat RESULT_OK
    private val detailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // reload antrian (pengajuan terbaru/terlama)
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

        bindViews()          // inisialisasi semua view
        setupRecycler()      // setup adapter & recycler
        setupNavigation()    // pasang listener setelah view di-bind

        loadParentHomeData() // load data
    }

    private fun bindViews() {
        // pastikan ID di layout sama persis dengan ini
        tvUserName = findViewById(R.id.tvUserName)
        tvUserRole = findViewById(R.id.tvUserRole)
        tvTotalBalance = findViewById(R.id.tvTotalChildBalance)
        tvPendingCount = findViewById(R.id.tvPendingSubmissions)
        recyclerView = findViewById(R.id.recyclerViewSubmissions)
        tvNoSubmissions = findViewById(R.id.tvNoSubmissions)
        cardDetailPengajuan = findViewById(R.id.cardSetTask)
        cardTopup = findViewById(R.id.cardTopup)
    }

    private fun setupRecycler() {
        adapter = SubmissionAdapter(submissionList) { submission ->
            val intent = Intent(this, DetailAjuanAnakActivity::class.java).apply {
                putExtra("submissionId", submission.id)
                putExtra("childId", submission.childId)
            }
            // gunakan launcher agar kita dapat refresh ketika detail selesai (RESULT_OK)
            detailLauncher.launch(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupNavigation() {
        cardDetailPengajuan.setOnClickListener {
            // buka activity detail (tampilkan pengajuan terlama jika ada)
            // kalau mau buka oldest submission via intent, bisa ambil dari submissionList[0] jika ada
            if (submissionList.isNotEmpty()) {
                val s = submissionList.minByOrNull { it.createdAt }!!
                val intent = Intent(this, DetailAjuanAnakActivity::class.java).apply {
                    putExtra("submissionId", s.id)
                    putExtra("childId", s.childId)
                }
                detailLauncher.launch(intent)
            } else {
                // buka halaman detail kosong
                startActivity(Intent(this, DetailAjuanAnakActivity::class.java))
            }
        }

        cardTopup.setOnClickListener {
            startActivity(Intent(this, TopupActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadParentHomeData()
    }

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

        // ambil nama parent (jika ada)
        firestore.collection("parents").document(parentId)
            .get()
            .addOnSuccessListener { parentSnap ->
                val parentName = parentSnap.getString("name") ?: parentEmail.substringBefore("@")
                tvUserName.text = parentName
            }
            .addOnFailureListener {
                tvUserName.text = parentEmail.substringBefore("@")
            }

        // Ambil semua anak yang memiliki parentId atau parentEmail di field "parents" (array)
        val childDocs = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()

        // 1) whereArrayContains encoded parentId
        firestore.collection("users")
            .whereArrayContains("parents", parentId)
            .get()
            .addOnSuccessListener { snap1 ->
                for (d in snap1.documents) childDocs[d.id] = d

                // 2) whereArrayContains plain parent email
                firestore.collection("users")
                    .whereArrayContains("parents", parentEmail)
                    .get()
                    .addOnSuccessListener { snap2 ->
                        for (d in snap2.documents) childDocs[d.id] = d

                        // selesai gabung hasil
                        handleChildrenDocs(childDocs.values.toList())
                    }
                    .addOnFailureListener {
                        // tetap handle hasil dari query pertama
                        handleChildrenDocs(childDocs.values.toList())
                    }
            }
            .addOnFailureListener {
                // kalau query pertama gagal, coba langsung berdasarkan email (fallback)
                firestore.collection("users")
                    .whereArrayContains("parents", parentEmail)
                    .get()
                    .addOnSuccessListener { snap2 ->
                        for (d in snap2.documents) childDocs[d.id] = d
                        handleChildrenDocs(childDocs.values.toList())
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Gagal memuat data anak", Toast.LENGTH_SHORT).show()
                        showEmptyState()
                    }
            }
    }

    private fun handleChildrenDocs(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        val totalChildren = docs.size
        var totalBalance = 0L
        val childIds = mutableListOf<String>()

        for (doc in docs) {
            // prioritas field untuk saldo anak: saldoAnak -> saldo -> balance (legacy)
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

        // chunk list karena whereIn maksimal 10
        val idChunks = chunkList(childIds, 10)

        var pendingQueries = 0
        val collectedDocs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()

        fun onQueryDone() {
            pendingQueries--
            if (pendingQueries <= 0) {
                // gabungkan, unique by id, filter status pending, lalu urutkan by createdAt ascending (terlama dulu)
                val distinct = collectedDocs
                    .distinctBy { it.id }
                    .filter { doc ->
                        val raw = (doc.getString("status") ?: doc.getString("state") ?: "").lowercase()
                        raw.contains("pending") || raw.contains("new") || raw.contains("menunggu")
                    }
                    .sortedBy { doc ->
                        // ambil timestamp/createdAt/tanggal dalam millis, fallback ke now
                        val ts = doc.get("createdAt") ?: doc.get("tanggal") ?: doc.get("date") ?: doc.get("created_at")
                        when (ts) {
                            is com.google.firebase.Timestamp -> ts.toDate().time
                            is com.google.firebase.Timestamp -> ts.toDate().time
                            is java.util.Date -> ts.time
                            is Number -> ts.toLong()
                            is Long -> ts
                            else -> Long.MAX_VALUE // kalau ga ada, taruh di akhir
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

    private fun encodeEmailToId(email: String): String {
        // menggunakan encoding yang konsisten
        return email.replace("@", "_at_").replace(".", "_dot_")
    }
}