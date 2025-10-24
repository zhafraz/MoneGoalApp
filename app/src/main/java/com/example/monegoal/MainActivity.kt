package com.example.monegoal

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var tvUserName: TextView
    private lateinit var tvUserClass: TextView
    private lateinit var tvCurrentBalance: TextView
    private lateinit var tvCurrentPoints: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvUserName = findViewById(R.id.tvUserName)
        tvUserClass = findViewById(R.id.tvUserClass)
        tvCurrentBalance = findViewById(R.id.tvCurrentBalance)
        tvCurrentPoints = findViewById(R.id.tvCurrentPoints)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        loadUserData()
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user == null) return

        val uid = user.uid
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name") ?: ""
                    val school = document.getString("school") ?: ""
                    val balance = document.getLong("balance") ?: 0
                    val points = document.getLong("points") ?: 0

                    tvUserName.text = name
                    tvUserClass.text = school
                    tvCurrentBalance.text = "Rp %,d".format(balance)
                    tvCurrentPoints.text = points.toString()
                }
            }
    }
}