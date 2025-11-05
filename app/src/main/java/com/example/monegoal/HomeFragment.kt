package com.example.monegoal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.monegoal.adapter.ChildGoalsAdapter
import com.example.monegoal.models.Goal
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HomeFragment : Fragment() {

    private lateinit var tvUserName: TextView
    private lateinit var tvUserClass: TextView
    private lateinit var tvCurrentBalance: TextView
    private lateinit var tvCurrentPoints: TextView

    private lateinit var cardChat: CardView
    private lateinit var cardAddMoney: CardView
    private lateinit var cardAddGoals: CardView
    private lateinit var cardNews: CardView
    private lateinit var recyclerViewGoals: RecyclerView

    private lateinit var profileContainer: View
    private lateinit var financeContainer: View

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var goalsAdapter: ChildGoalsAdapter
    private var goalList: MutableList<Goal> = mutableListOf()
    private var currentBalance: Long = 0L
    private var userListener: ListenerRegistration? = null
    private var goalsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // TextViews
        tvUserName = view.findViewById(R.id.tvUserName)
        tvUserClass = view.findViewById(R.id.tvUserClass)
        tvCurrentBalance = view.findViewById(R.id.tvCurrentBalance)
        tvCurrentPoints = view.findViewById(R.id.tvCurrentPoints)

        // Cards
        cardChat = view.findViewById(R.id.cardChat)
        cardAddMoney = view.findViewById(R.id.cardAddMoney)
        cardAddGoals = view.findViewById(R.id.cardAddGoals)
        cardNews = view.findViewById(R.id.cardNews)
        recyclerViewGoals = view.findViewById(R.id.recyclerViewGoals)

        // Containers
        profileContainer = view.findViewById(R.id.profile)
        financeContainer = view.findViewById(R.id.finance)

        // Setup RecyclerView
        recyclerViewGoals.layoutManager = LinearLayoutManager(requireContext())
        goalsAdapter = ChildGoalsAdapter(
            goalList,
            onClick = { goal ->
                // Klik item goal, misal buka detail
            },
            onComplete = { goal ->
                markGoalAsComplete(goal)
            }
        )
        recyclerViewGoals.adapter = goalsAdapter

        setupClickListeners()
        loadUserDataRealtime()
    }

    private fun setupClickListeners() {
        cardChat.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(AiBantuanFragment(), R.id.nav_ai_bantuan, addToBackStack = true)
                ?: navigateToFragment(AiBantuanFragment())
        }
        cardAddMoney.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(TopupFragment(), null, addToBackStack = true)
                ?: navigateToFragment(TopupFragment())
        }
        cardAddGoals.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(TambahTargetFragment(), R.id.nav_goals, addToBackStack = true)
                ?: navigateToFragment(GoalsFragment())
        }
        cardNews.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(CampaignFragment(), R.id.nav_campaign, addToBackStack = true)
                ?: navigateToFragment(CampaignFragment())
        }
        profileContainer.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(ProfileFragment(), R.id.nav_profile, addToBackStack = true)
                ?: navigateToFragment(ProfileFragment())
        }
        financeContainer.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(PengajuanFragment(), null, addToBackStack = true)
                ?: navigateToFragment(PengajuanFragment())
        }
    }

    private fun navigateToFragment(fragment: Fragment) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    /** Load data user secara realtime (balance & points) */
    private fun loadUserDataRealtime() {
        val uid = auth.currentUser?.uid ?: return
        val userRef = firestore.collection("users").document(uid)

        userListener = userRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            currentBalance = snapshot.getLong("saldoAnak") ?: 0L
            val points = snapshot.getLong("points") ?: 0L

            tvUserName.text = snapshot.getString("name") ?: ""
            tvUserClass.text = snapshot.getString("school") ?: ""
            tvCurrentBalance.text = "Rp %,d".format(currentBalance)
            tvCurrentPoints.text = points.toString()

            loadGoalsRealtime(currentBalance)
        }
    }

    /** Load goals secara realtime, pilih 1 goal terdekat */
    private fun loadGoalsRealtime(currentBalance: Long) {
        val uid = auth.currentUser?.uid ?: return
        val goalsRef = firestore.collection("users").document(uid).collection("goals")

        goalsListener?.remove()
        goalsListener = goalsRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener

            val allGoals = mutableListOf<Goal>()
            for (doc in snapshot) {
                val goal = doc.toObject(Goal::class.java)
                goal.id = doc.id
                allGoals.add(goal)
            }

            // Pilih goal terdekat berdasarkan currentAmount / currentBalance
            val closestGoal = allGoals.minByOrNull { goal ->
                val current = if (goal.currentAmount > 0L) goal.currentAmount else currentBalance
                (goal.targetAmount - current).coerceAtLeast(0L)
            }

            goalList.clear()
            closestGoal?.let { goalList.add(it) }

            goalsAdapter.updateBalance(currentBalance)
            goalsAdapter.updateGoals(goalList)
        }
    }

    /** Tandai goal sebagai complete */
    private fun markGoalAsComplete(goal: Goal) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = firestore.collection("users").document(uid)
        val goalRef = userRef.collection("goals").document(goal.id)

        val batch = firestore.batch()
        batch.update(userRef, "points", FieldValue.increment(goal.points.toLong()))
        batch.delete(goalRef)
        batch.commit().addOnSuccessListener {
            // otomatis listener realtime akan update goalList
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userListener?.remove()
        goalsListener?.remove()
    }

}