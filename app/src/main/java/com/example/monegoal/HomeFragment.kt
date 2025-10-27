package com.example.monegoal

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private lateinit var tvUserName: TextView
    private lateinit var tvUserClass: TextView
    private lateinit var tvCurrentBalance: TextView
    private lateinit var tvCurrentPoints: TextView

    private lateinit var cardChat: CardView
    private lateinit var cardAddMoney: CardView
    private lateinit var cardAddPrestasi: CardView
    private lateinit var cardNews: CardView
    private lateinit var recyclerViewGoals: RecyclerView

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // init Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Find views inside fragment_home.xml
        tvUserName = view.findViewById(R.id.tvUserName)
        tvUserClass = view.findViewById(R.id.tvUserClass)
        tvCurrentBalance = view.findViewById(R.id.tvCurrentBalance)
        tvCurrentPoints = view.findViewById(R.id.tvCurrentPoints)

        cardChat = view.findViewById(R.id.cardChat)
        cardAddMoney = view.findViewById(R.id.cardAddMoney)
        cardAddPrestasi = view.findViewById(R.id.cardAddPrestasi)
        cardNews = view.findViewById(R.id.cardNews)
        recyclerViewGoals = view.findViewById(R.id.recyclerViewGoals)

        // Setup click listeners for cards
        cardChat.setOnClickListener {
            startActivity(Intent(requireContext(), AIBantuanActivity::class.java))
        }
        cardAddMoney.setOnClickListener {
            startActivity(Intent(requireContext(), AjukanDanaActivity::class.java))
        }
        cardAddPrestasi.setOnClickListener {
            startActivity(Intent(requireContext(), HomeFragment::class.java))
        }
        cardNews.setOnClickListener {
            startActivity(Intent(requireContext(), HomeFragment::class.java))
        }

        loadUserData()
    }

    private fun loadUserData() {
        val user = auth.currentUser ?: run {
            // user belum login — tampilkan default atau arahkan ke login
            tvUserName.text = "Halo!"
            tvUserClass.text = ""
            tvCurrentBalance.text = "Rp 0"
            tvCurrentPoints.text = "0"
            return
        }

        val uid = user.uid
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name") ?: "Nama"
                    val school = document.getString("school") ?: ""
                    val balance = document.getLong("balance") ?: 0
                    val points = document.getLong("points") ?: 0

                    tvUserName.text = name
                    tvUserClass.text = school
                    tvCurrentBalance.text = "Rp %,d".format(balance)
                    tvCurrentPoints.text = points.toString()
                }
            }
            .addOnFailureListener {
                // fallback ringan
                tvUserName.text = "Halo!"
                tvCurrentBalance.text = "Rp 0"
                tvCurrentPoints.text = "0"
            }
    }
}