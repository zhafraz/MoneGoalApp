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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
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

    private lateinit var cardDetailPengajuan: CardView
    private lateinit var cardTopup: CardView

    private lateinit var adapter: SubmissionAdapter
    private val submissionList = mutableListOf<Submission>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_ortu)

        // edge-to-edge padding
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
    }

    private fun setupRecycler() {
        adapter = SubmissionAdapter(submissionList) { submission ->
            val intent = Intent(this, DetailAjuanAnakActivity::class.java).apply {
                putExtra("submissionId", submission.id)
                putExtra("childId", submission.childId)
            }
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupNavigation() {
        cardTopup.setOnClickListener {
            startActivity(Intent(this, TopupActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadParentHomeData()
    }

    private fun loadParentHomeData() {
        val parentEmail = intent.getStringExtra("parentEmail") ?: auth.currentUser?.email

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

        // Ambil nama parent
        firestore.collection("parents").document(parentId)
            .get()
            .addOnSuccessListener { parentSnap ->
                val parentName = parentSnap.getString("name") ?: parentEmail.substringBefore("@")
                tvUserName.text = parentName
            }
            .addOnFailureListener {
                tvUserName.text = parentEmail.substringBefore("@")
            }

        // Ambil semua anak yang punya parentId ini
        firestore.collection("users")
            .whereArrayContains("parents", parentId)
            .get()
            .addOnSuccessListener { docs ->
                val totalChildren = docs.size()
                var totalBalance = 0L
                val childIds = mutableListOf<String>()

                for (doc in docs.documents) {
                    val bal = doc.getLong("balance") ?: 0L
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
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat data anak: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
    }

    private fun loadSubmissionsForChildren(childIds: List<String>) {
        val queryIds = if (childIds.size > 10) childIds.take(10) else childIds

        firestore.collection("submissions")
            .whereIn("childId", queryIds)
            .get()
            .addOnSuccessListener { snapshot: QuerySnapshot ->
                submissionList.clear()

                val filtered = snapshot.documents.filter { d ->
                    val status = d.getString("status") ?: ""
                    status.equals("pending", true) || status.equals("new", true)
                }

                for (doc in filtered) {
                    val amount = doc.getLong("amount") ?: 0L
                    val createdAtMillis: Long = when (val ts = doc.get("createdAt")) {
                        is Timestamp -> ts.toDate().time
                        is Long -> ts
                        is Number -> ts.toLong()
                        else -> System.currentTimeMillis()
                    }

                    val childId = doc.getString("childId") ?: ""

                    submissionList.add(
                        Submission(
                            id = doc.id,
                            childId = childId,
                            childName = doc.getString("childName") ?: "Anak",
                            title = doc.getString("title") ?: (doc.getString("category") ?: "Pengajuan"),
                            amount = amount,
                            status = doc.getString("status") ?: "unknown",
                            createdAt = createdAtMillis
                        )
                    )
                }

                adapter.updateData(submissionList)

                val pendingCount = submissionList.count {
                    it.status.equals("pending", true) || it.status.equals("new", true)
                }
                tvPendingCount.text = pendingCount.toString()

                if (submissionList.isEmpty()) showEmptyState() else showRecycler()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat pengajuan: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
    }

    private fun showEmptyState() {
        tvNoSubmissions.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
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
        return email.replace("@", "_at_").replace(".", "_dot_")
    }
}