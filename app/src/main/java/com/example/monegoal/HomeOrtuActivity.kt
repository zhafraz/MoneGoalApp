package com.example.monegoal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.monegoal.adapter.SubmissionAdapter
import com.example.monegoal.models.Submission
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.DocumentSnapshot
import java.text.NumberFormat
import java.util.Locale

class HomeOrtuActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var tvUserName: TextView
    private lateinit var tvUserRole: TextView
    private lateinit var tvTotalBalance: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoSubmissions: TextView

    private lateinit var cardManageChildren: CardView
    private lateinit var cardDetailPengajuan: CardView
    private lateinit var cardTopup: CardView

    private lateinit var adapter: SubmissionAdapter
    private val submissionList = mutableListOf<Submission>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_ortu)

        // padding edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Inisialisasi view dari layout
        tvUserName = findViewById(R.id.tvUserName)
        tvUserRole = findViewById(R.id.tvUserRole)
        tvTotalBalance = findViewById(R.id.tvTotalChildBalance)
        tvPendingCount = findViewById(R.id.tvPendingSubmissions)
        recyclerView = findViewById(R.id.recyclerViewSubmissions)
        tvNoSubmissions = findViewById(R.id.tvNoSubmissions)
        cardManageChildren = findViewById(R.id.cardManageChildren)
        cardDetailPengajuan = findViewById(R.id.cardSetTask)
        cardTopup = findViewById(R.id.cardTopup)

        // Setup RecyclerView
        adapter = SubmissionAdapter(submissionList) { submission ->
            val intent = Intent(this, DetailAjuanAnakActivity::class.java)
            intent.putExtra("submissionId", submission.id)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadParentHomeData()
        setupNavigation()
    }

    /**
     * Load data yang diperlukan untuk home orang tua:
     * 1) Tentukan parentEmail: dari Intent extra atau fallback ke auth.currentUser?.email
     * 2) Ambil parent doc dari koleksi "parents" berdasarkan encoded email (parentId)
     * 3) Query users dimana parents array contains parentId untuk mendapatkan daftar anak
     */
    private fun loadParentHomeData() {
        // Ambil parentEmail yang dikirim lewat Intent (LoginOrtuActivity mengirimkan ini).
        val intentEmail = intent.getStringExtra("parentEmail")
        val parentEmail = intentEmail ?: auth.currentUser?.email

        if (parentEmail.isNullOrBlank()) {
            // Tidak ada parent email -> fallback: tampilkan nama default dan kosongkan data
            tvUserName.text = "Orang Tua"
            tvUserRole.text = "Pengelola Keluarga (0 Anak)"
            tvTotalBalance.text = formatCurrency(0L)
            tvPendingCount.text = "0"
            tvNoSubmissions.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            return
        }

        val parentId = encodeEmailToId(parentEmail)

        // Ambil info parent dari koleksi "parents" untuk menampilkan nama yang benar.
        firestore.collection("parents").document(parentId)
            .get()
            .addOnSuccessListener { parentSnap: DocumentSnapshot ->
                val parentName = parentSnap.getString("name") ?: parentEmail.substringBefore("@")
                tvUserName.text = parentName
            }
            .addOnFailureListener {
                // jika gagal, gunakan email sebagai fallback
                tvUserName.text = parentEmail.substringBefore("@")
            }

        // Query koleksi "users" -> cari anak yang memiliki parents array contains parentId
        firestore.collection("users")
            .whereArrayContains("parents", parentId)
            .get()
            .addOnSuccessListener { docs: QuerySnapshot ->
                val totalChildren = docs.size()
                var totalBalance = 0L
                val childIds = mutableListOf<String>()

                // iterasi setiap dokumen anak
                for (doc in docs.documents) {
                    val bal = doc.getLong("balance") ?: 0L
                    totalBalance += bal
                    childIds.add(doc.id)
                }

                // Update UI: jumlah anak, total balance
                tvUserRole.text = "Pengelola Keluarga ($totalChildren Anak)"
                tvTotalBalance.text = formatCurrency(totalBalance)

                // Load pengajuan (submissions) untuk anak-anak ini
                loadSubmissionsForChildren(childIds)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat data anak: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                // tampil data kosong / default
                tvUserRole.text = "Pengelola Keluarga (0 Anak)"
                tvTotalBalance.text = formatCurrency(0L)
                tvPendingCount.text = "0"
                tvNoSubmissions.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
    }

    private fun loadSubmissionsForChildren(childIds: List<String>) {
        if (childIds.isEmpty()) {
            tvNoSubmissions.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            tvPendingCount.text = "0"
            submissionList.clear()
            adapter.updateData(submissionList)
            return
        }

        // Firestore tidak mendukung whereIn > 10 elements; mengambil maksimal 10
        val queryIds = if (childIds.size > 10) childIds.take(10) else childIds

        firestore.collection("submissions")
            .whereIn("childId", queryIds)
            .get()
            .addOnSuccessListener { snapshot: QuerySnapshot ->
                submissionList.clear()

                // filter untuk status pending/new (sesuaikan field status di db Anda)
                val filtered = snapshot.documents.filter { d ->
                    val status = d.getString("status") ?: ""
                    status.equals("pending", ignoreCase = true) || status.equals("new", ignoreCase = true)
                }

                for (doc in filtered) {
                    submissionList.add(
                        Submission(
                            id = doc.id,
                            childName = doc.getString("childName") ?: "Anak",
                            title = doc.getString("title") ?: (doc.getString("category") ?: "Pengajuan"),
                            amount = doc.getLong("amount") ?: 0,
                            status = doc.getString("status") ?: "unknown",
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                    )
                }

                adapter.updateData(submissionList)

                val pendingCount = submissionList.count {
                    it.status.equals("pending", true) || it.status.equals("new", true)
                }

                tvPendingCount.text = pendingCount.toString()
                tvNoSubmissions.visibility = if (submissionList.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (submissionList.isEmpty()) View.GONE else View.VISIBLE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat pengajuan: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupNavigation() {
        cardManageChildren.setOnClickListener {
            Toast.makeText(this, "Fitur Riwayat Transaksi belum diimplementasikan", Toast.LENGTH_SHORT).show()
        }

        cardDetailPengajuan.setOnClickListener {
            startActivity(Intent(this, DetailAjuanAnakActivity::class.java))
        }

        cardTopup.setOnClickListener {
            startActivity(Intent(this, TopupActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // reload data setiap resume agar UI up-to-date
        loadParentHomeData()
    }

    private fun formatCurrency(amount: Long): String {
        val formatted = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(amount)
        // memastikan format "Rp 1.000" (ada spasi setelah Rp)
        return formatted.replace("Rp", "Rp ").trim()
    }

    private fun encodeEmailToId(email: String): String {
        return email.replace("@", "_at_").replace(".", "_dot_")
    }
}