package com.example.monegoal

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var tvUserName: TextView
    private lateinit var tvUserGoal: TextView
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge() kalau kamu punya extension, panggil dulu. Kalau tidak, abaikan.
        // enableEdgeToEdge()

        setContentView(R.layout.activity_profile)

        // Pastikan root_profile ada di activity_profile.xml
        val root = findViewById<android.view.View>(R.id.root_profile)
            ?: throw IllegalStateException("root_profile tidak ditemukan. Tambahkan android:id='@+id/root_profile' pada root layout activity_profile.xml")

        // Atur window insets (safe)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // init firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // find views
        tvUserName = findViewById(R.id.tv_user_name)
        tvUserGoal = findViewById(R.id.tv_user_goal)
        btnLogout = findViewById(R.id.btn_logout)

        // load nama user (prefer displayName, fallback ke Firestore)
        loadUserName()

        // contoh logout handler
        btnLogout.setOnClickListener {
            auth.signOut()
            // kembali ke LoginActivity (ganti sesuai flow)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun loadUserName() {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            tvUserName.text = "Halo!"
            return
        }

        val displayName = firebaseUser.displayName
        if (!displayName.isNullOrBlank()) {
            tvUserName.text = displayName
            return
        }

        // Jika displayName kosong (atau kamu ingin selalu sinkron dari Firestore),
        // ambil dari collection 'users' field 'name'
        val uid = firebaseUser.uid
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val name = doc.getString("name") ?: "Pengguna"
                    tvUserName.text = name
                    // optional: isi goal dari doc jika ada
                    val goal = doc.getString("goal") ?: "Financial Goal Setter"
                    tvUserGoal.text = goal
                } else {
                    tvUserName.text = "Pengguna"
                }
            }
            .addOnFailureListener {
                tvUserName.text = firebaseUser.email ?: "Pengguna"
            }
    }
}