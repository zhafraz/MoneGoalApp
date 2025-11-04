package com.example.monegoal

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.fragment.app.commit

class CampaignFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_campaign, container, false)

        // Ambil referensi CardView
        val cardLomba1 = view.findViewById<CardView>(R.id.cardCampaign1)
        val cardLomba2 = view.findViewById<CardView>(R.id.cardCampaign2)
        val cardLomba3 = view.findViewById<CardView>(R.id.cardCampaign3)

        // Ketika cardCampaign1 diklik → buka BeritaLomba1Fragment
        cardLomba1.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, BeritaLomba1Fragment())
                addToBackStack(null)
            }
        }

        cardLomba2.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, BeritaLomba2Fragment())
                addToBackStack(null)
            }
        }

        cardLomba3.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, BeritaLomba3Fragment())
                addToBackStack(null)
            }
        }

        return view
    }
}
