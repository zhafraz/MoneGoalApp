package com.example.monegoal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.monegoal.models.Goal
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TambahTargetFragment : Fragment() {
    private lateinit var etTitle: EditText
    private lateinit var etDescription: EditText
    private lateinit var etTargetAmount: EditText
    private lateinit var btnSave: Button
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tambah_target, container, false)

        db = FirebaseFirestore.getInstance()
        etTitle = view.findViewById(R.id.etGoalTitle)
        etDescription = view.findViewById(R.id.etGoalDescription)
        etTargetAmount = view.findViewById(R.id.etGoalPrice)
        btnSave = view.findViewById(R.id.btnSaveGoal)

        btnSave.setOnClickListener { saveGoal() }

        return view
    }

    private fun saveGoal() {
        val title = etTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val targetStr = etTargetAmount.text.toString().trim()

        if (title.isEmpty() || targetStr.isEmpty()) {
            Toast.makeText(requireContext(), "Judul dan target harus diisi", Toast.LENGTH_SHORT).show()
            return
        }

        val targetAmount = targetStr.toLongOrNull()
        if (targetAmount == null || targetAmount <= 0) {
            Toast.makeText(requireContext(), "Target harus angka lebih dari 0", Toast.LENGTH_SHORT).show()
            return
        }

        val points = (targetAmount / 1000).toInt() // 1 poin per 1000

        val goal = Goal(
            title = title,
            description = description,
            targetAmount = targetAmount,
            currentAmount = 0,
            points = points
        )

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(userId).collection("goals")
            .add(goal)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Goal berhasil ditambahkan", Toast.LENGTH_SHORT).show()

                // Navigasi manual ke GoalsFragment
                (activity as? MainActivity)?.navigateTo(
                    GoalsFragment(),
                    menuItemId = R.id.nav_goals,
                    addToBackStack = false
                )
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Gagal menambahkan goal", Toast.LENGTH_SHORT).show()
            }
    }
}