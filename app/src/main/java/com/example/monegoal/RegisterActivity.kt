package com.example.monegoal

import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        // Atur padding otomatis biar tidak terpotong oleh status bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- Bagian Spinner / Dropdown ---
        val spinner: Spinner = findViewById(R.id.spJenjang)

        // Daftar jenjang sekolah
        val jenjangList = listOf("Pilih jenjang sekolah", "SD", "SMP", "SMA", "SMK")

        // Adapter untuk spinner
        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            jenjangList
        ) {
            override fun isEnabled(position: Int): Boolean {
                // Nonaktifkan item pertama (sebagai hint)
                return position != 0
            }
        }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Aksi ketika item dipilih
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                if (position > 0) {
                    val selected = parent.getItemAtPosition(position).toString()
                    Toast.makeText(this@RegisterActivity, "Dipilih: $selected", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }
}
