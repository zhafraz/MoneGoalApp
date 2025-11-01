package com.example.monegoal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.example.monegoal.ui.anak.AjukanDanaFragment
//import com.example.monegoal.ui.anak.RiwayatPengajuanFragment

class PengajuanFragment : Fragment() {

    private lateinit var cardAjukanDana: CardView
    private lateinit var cardRiwayat: CardView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pengajuan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ambil id dari layout
        cardAjukanDana = view.findViewById(R.id.cardAjukanDana)
        cardRiwayat = view.findViewById(R.id.cardRiwayat)

        // tombol kembali (kalau ada)
        view.findViewById<View>(R.id.btnBack)?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // klik Ajukan Dana
        cardAjukanDana.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(AjukanDanaFragment(), null, addToBackStack = true)
                ?: navigateToFragment(AjukanDanaFragment())
        }

        // klik Riwayat
//        cardRiwayat.setOnClickListener {
//            (activity as? MainActivity)?.navigateTo(RiwayatPengajuanFragment(), null, addToBackStack = true)
//                ?: navigateToFragment(RiwayatPengajuanFragment())
//        }
    }

    private fun navigateToFragment(fragment: Fragment) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }
}