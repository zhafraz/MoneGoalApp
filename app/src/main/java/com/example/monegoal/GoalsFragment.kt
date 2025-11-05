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
    private var currentPoints: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_goals, container, false)

        db = FirebaseFirestore.getInstance()
        rvGoals = view.findViewById(R.id.rvChildGoals)
        layoutEmpty = view.findViewById(R.id.layoutEmptyState)
        val fabAdd: FloatingActionButton = view.findViewById(R.id.fabAddGoal)

        // adapter kosong dulu; saldo akan di-inject melalui updateBalance()
        adapter = ChildGoalsAdapter(
            goalList,
            onClick = { goal ->
                Toast.makeText(requireContext(), "Klik: ${goal.title}", Toast.LENGTH_SHORT).show()
            },
            onComplete = { goal ->
                // sumber yang sama seperti adapter: prioritas ke goal.currentAmount (jika >0)
                // jika tidak ada gunakan currentBalance (saldo user)
                val sourceAmount = if (goal.currentAmount > 0L) goal.currentAmount else currentBalance
                val progressPercent = if (goal.targetAmount > 0L) {
                    ((sourceAmount.toDouble() / goal.targetAmount.toDouble()) * 100).toInt()
                } else 0

                if (progressPercent >= 100) {
                    markGoalComplete(goal)
                } else {
                    Toast.makeText(requireContext(), "Goal belum 100% selesai!", Toast.LENGTH_SHORT).show()
                }
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

    /**
     * Mulai listener realtime:
     * - saldo anak (prioritas: "saldoAnak", lalu "saldo", lalu "balance")
     * - daftar goals
     */
    private fun startListeners() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = db.collection("users").document(userId)

        // listener untuk data user (realtime) — ambil field saldo prioritas ke "saldoAnak"
        userListener = userRef.addSnapshotListener { snap, e ->
            if (e != null || snap == null || !snap.exists()) return@addSnapshotListener

            currentBalance = extractLongFromDoc(snap.data, listOf("saldoAnak", "saldo", "balance"))
            currentPoints = extractLongFromDoc(snap.data, listOf("points"))

            // kirim saldo terbaru ke adapter sehingga progress bar bisa dihitung ulang
            adapter.updateBalance(currentBalance)
        }

        // listener untuk goals anak (realtime)
        goalsListener = userRef.collection("goals")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(requireContext(), "Gagal memuat goals: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val tempList = mutableListOf<Goal>()
                snapshot?.forEach { doc ->
                    val goal = doc.toObject(Goal::class.java)
                    goal.id = doc.id
                    tempList.add(goal)
                }

                goalList.clear()
                goalList.addAll(tempList)

                // pastikan adapter menerima data goals terbaru
                adapter.updateGoals(goalList)

                // pastikan adapter juga punya saldo terbaru agar progress langsung benar
                adapter.updateBalance(currentBalance)

                layoutEmpty.visibility = if (goalList.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    /**
     * Utility kecil untuk mengekstrak Long dari data map dengan beberapa possible keys
     */
    private fun extractLongFromDoc(data: Map<String, Any>?, keys: List<String>): Long {
        if (data == null) return 0L
        for (k in keys) {
            val v = data[k] ?: continue
            when (v) {
                is Number -> return v.toLong()
                is String -> return v.toLongOrNull() ?: continue
            }
        }
        return 0L
    }

    /**
     * Tandai goal selesai:
     * - Tambah poin anak
     * - Hapus goal dari Firestore
     */
    private fun markGoalComplete(goal: Goal) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = db.collection("users").document(userId)
        val goalRef = userRef.collection("goals").document(goal.id)

        val batch = db.batch()
        batch.update(userRef, "points", FieldValue.increment(goal.points.toLong()))
        batch.delete(goalRef)

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(
                    requireContext(),
                    "🎯 Goal '${goal.title}' selesai! +${goal.points} poin ditambahkan!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Gagal menyelesaikan goal: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        goalsListener?.remove()
        userListener?.remove()
    }
}