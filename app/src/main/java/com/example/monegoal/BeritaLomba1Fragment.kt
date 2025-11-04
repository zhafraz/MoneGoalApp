package com.example.monegoal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * Fragment untuk menampilkan detail berita/kampanye Lomba 1: Samsung Innovation Campus.
 * Konten (Teks dan Gambar) dimuat secara STATIS dari XML.
 */
class BeritaLomba1Fragment : Fragment() {

    // Menghapus parameter dan newInstance yang tidak terpakai agar kode lebih bersih
    // Jika Anda menggunakan NavArgs, bagian ini akan diubah.

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Menggunakan layout fragment_berita_lomba1
        return inflater.inflate(R.layout.fragment_berita_lomba1, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Dapatkan referensi komponen UI
        // btnBack digunakan untuk kembali ke halaman sebelumnya
        val btnBack: ImageButton? = view.findViewById(R.id.btnBack)

        // tvLinkCTA digunakan untuk membuka tautan pendaftaran
        val tvLinkCTA: TextView? = view.findViewById(R.id.tvLinkCTA)

        // Tautan tujuan (URL) statis untuk Samsung Innovation Campus (SIC)
        val linkUrl = "https://www.hacktiv8.com/projects/samsung-indonesia?utm_source=chatgpt.com"

        // 2. Implementasi Tombol Kembali
        // Tombol kembali akan menutup Fragment saat ini dan kembali ke Fragment sebelumnya.
        btnBack?.setOnClickListener {
            // Menggunakan FragmentManager untuk kembali ke Fragment sebelumnya di Back Stack
            parentFragmentManager.popBackStack()
        }

        // 3. Implementasi Tombol CTA (Membuka Tautan)
        // Saat diklik, akan membuka URL di browser eksternal.
        tvLinkCTA?.setOnClickListener {
            try {
                // Membuat Intent untuk melihat (ACTION_VIEW) URI
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl))
                startActivity(intent)
            } catch (e: Exception) {
                // Log error jika ada masalah saat membuka tautan
                android.util.Log.e("BeritaLomba1Fragment", "Error membuka tautan: ${e.message}")
            }
        }
    }

    // Menghapus fungsi newInstance dan parameter yang tidak terpakai
}
