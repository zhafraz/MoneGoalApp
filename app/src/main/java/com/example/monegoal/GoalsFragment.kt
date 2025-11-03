package com.example.monegoal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.monegoal.adapter.ChildGoalsAdapter
import com.example.monegoal.models.Goal
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class GoalsFragment : Fragment() {

    private lateinit var adapter: ChildGoalsAdapter
    private val goalList = mutableListOf<Goal>()
    private lateinit var db: FirebaseFirestore
    private lateinit var rvGoals: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private var goalsListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null
    private var currentBalance: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_goals, container, false)

        db = FirebaseFirestore.getInstance()
        rvGoals = view.findViewById(R.id.rvChildGoals)
        layoutEmpty = view.findViewById(R.id.layoutEmptyState)
        val fabAdd: FloatingActionButton = view.findViewById(R.id.fabAddGoal)

        adapter = ChildGoalsAdapter(goalList,
            onClick = { goal ->
                // aksi klik item (buka detail, dsb). Untuk sekarang cuma toast.
                Toast.makeText(requireContext(), "Klik: ${goal.title}", Toast.LENGTH_SHORT).show()
            },
            onComplete = { goal ->
                // dipanggil saat user menekan tombol "Selesai"
                markGoalComplete(goal)
            }
        )

        rvGoals.layoutManager = LinearLayoutManager(requireContext())
        rvGoals.adapter = adapter

        fabAdd.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(TambahTargetFragment(), menuItemId = R.id.nav_goals)
        }

        startListeners()

        return view
    }

    private fun startListeners() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = db.collection("users").document(userId)

        // listener untuk balance user (realtime)
        userListener = userRef.addSnapshotListener { snap, e ->
            if (e != null) return@addSnapshotListener
            currentBalance = snap?.getLong("balance") ?: 0L
            adapter.updateBalance(currentBalance)
        }

        // listener untuk goals subcollection (realtime)
        goalsListener = userRef.collection("goals").addSnapshotListener { snapshot, e ->
            if (e != null) {
                Toast.makeText(requireContext(), "Gagal memuat goals: ${e.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }
            val temp = mutableListOf<Goal>()
            snapshot?.forEach { doc ->
                val goal = doc.toObject(Goal::class.java)
                goal.id = doc.id
                temp.add(goal)
            }
            goalList.clear()
            goalList.addAll(temp)
            adapter.updateGoals(goalList)
            layoutEmpty.visibility = if (goalList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun markGoalComplete(goal: Goal) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = db.collection("users").document(userId)
        val goalRef = userRef.collection("goals").document(goal.id)

        // Simple batch: increment user's points, hapus goal
        val batch = db.batch()
        batch.update(userRef, "points", FieldValue.increment(goal.points.toLong()))
        batch.delete(goalRef)

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Goal selesai — ${goal.points} poin ditambahkan!", Toast.LENGTH_SHORT).show()
                // listener realtime akan menghapus item dari list otomatis
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Gagal menyelesaikan goal: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        goalsListener?.remove()
        userListener?.remove()
    }
}