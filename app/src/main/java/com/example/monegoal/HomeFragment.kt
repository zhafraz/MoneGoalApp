package com.example.monegoal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.monegoal.ui.anak.AjukanDanaFragment
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

    // new: containers that should be clickable
    private lateinit var profileContainer: View
    private lateinit var financeContainer: View

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

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // text views
        tvUserName = view.findViewById(R.id.tvUserName)
        tvUserClass = view.findViewById(R.id.tvUserClass)
        tvCurrentBalance = view.findViewById(R.id.tvCurrentBalance)
        tvCurrentPoints = view.findViewById(R.id.tvCurrentPoints)

        // cards
        cardChat = view.findViewById(R.id.cardChat)
        cardAddMoney = view.findViewById(R.id.cardAddMoney)
        cardAddPrestasi = view.findViewById(R.id.cardAddPrestasi)
        cardNews = view.findViewById(R.id.cardNews)
        recyclerViewGoals = view.findViewById(R.id.recyclerViewGoals)

        // new: find clickable containers
        profileContainer = view.findViewById(R.id.profile)
        financeContainer = view.findViewById(R.id.finance)

        // card listeners (existing)
        cardChat.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(AiBantuanFragment(), R.id.nav_ai_bantuan, addToBackStack = true)
                ?: navigateToFragment(AiBantuanFragment())
        }
        cardAddMoney.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(TopupFragment(), null, addToBackStack = true)
                ?: navigateToFragment(TopupFragment())
        }
        cardAddPrestasi.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(ProgramFragment(), R.id.nav_program, addToBackStack = true)
                ?: navigateToFragment(ProgramFragment())
        }
        cardNews.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(CampaignFragment(), R.id.nav_campaign, addToBackStack = true)
                ?: navigateToFragment(CampaignFragment())
        }

        // new: profile & finance click handlers
        profileContainer.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(ProfileFragment(), R.id.nav_profile, addToBackStack = true)
                ?: navigateToFragment(ProfileFragment())
        }

        financeContainer.setOnClickListener {
            // FinanceFra
            (activity as? MainActivity)?.navigateTo(PengajuanFragment(), null, addToBackStack = true)
                ?: navigateToFragment(PengajuanFragment())
        }

        loadUserData()
    }

    private fun navigateToFragment(fragment: Fragment) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun loadUserData() {
        val user = auth.currentUser ?: return

        val uid = user.uid
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    tvUserName.text = document.getString("name") ?: ""
                    tvUserClass.text = document.getString("school") ?: ""
                    val balance = document.getLong("balance") ?: 0
                    val points = document.getLong("points") ?: 0

                    tvCurrentBalance.text = "Rp %,d".format(balance)
                    tvCurrentPoints.text = points.toString()
                }
            }
    }
}