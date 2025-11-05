package com.example.monegoal

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit

data class Campaign(
    val id: Int,
    val title: String,
    val category: String, // "Trending", "Sekolah", "Finance"
    val cardView: CardView
)

class CampaignFragment : Fragment() {

    private lateinit var campaigns: List<Campaign>
    private lateinit var container: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_campaign, container, false)

        // Container
        this.container = view.findViewById(R.id.fragment_container)

        // CardViews
        val card1 = view.findViewById<CardView>(R.id.cardCampaign1)
        val card2 = view.findViewById<CardView>(R.id.cardCampaign2)
        val card3 = view.findViewById<CardView>(R.id.cardCampaign3)

        // Inisialisasi data dummy
        campaigns = listOf(
            Campaign(1, "Samsung Innovation Campus Batch 7 2024/2025", "Sekolah", card1),
            Campaign(2, "2025 Global Fintech Hackcelerator", "Finance", card2),
            Campaign(3, "Bagikan Informasi IDCamp 2023 dan Raih Hadiah Jutaan Rupiah!", "Trending", card3)
        )

        // Atur click listener untuk navigasi
        card1.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, BeritaLomba1Fragment())
                addToBackStack(null)
            }
        }
        card2.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, BeritaLomba2Fragment())
                addToBackStack(null)
            }
        }
        card3.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, BeritaLomba3Fragment())
                addToBackStack(null)
            }
        }

        // Search bar
        val etSearch = view.findViewById<EditText>(R.id.etSearchBar)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterCampaigns(s.toString(), currentCategory)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Filter buttons
        val btnAll = view.findViewById<Button>(R.id.btnFilterAll)
        val btnTrending = view.findViewById<Button>(R.id.btnFilterTrending)
        val btnSchool = view.findViewById<Button>(R.id.btnFilterSchool)
        val btnFinance = view.findViewById<Button>(R.id.btnFilterFinance)

        btnAll.setOnClickListener { setFilter("All") }
        btnTrending.setOnClickListener { setFilter("Trending") }
        btnSchool.setOnClickListener { setFilter("Sekolah") }
        btnFinance.setOnClickListener { setFilter("Finance") }

        campaigns.forEach { it.cardView.visibility = View.VISIBLE }

        return view
    }

    private var currentCategory = "All"

    private fun setFilter(category: String) {
        currentCategory = category
        filterCampaigns(view?.findViewById<EditText>(R.id.etSearchBar)?.text.toString(), category)
    }

    private fun filterCampaigns(searchText: String, category: String) {
        campaigns.forEach { campaign ->
            val matchesSearch = campaign.title.contains(searchText, ignoreCase = true)
            val matchesCategory = (category == "All") || (campaign.category == category)
            campaign.cardView.visibility = if (matchesSearch && matchesCategory) View.VISIBLE else View.GONE
        }
    }

}