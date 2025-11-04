package com.example.monegoal

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fabQr: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_navigation)
        fabQr = findViewById(R.id.fab_qr_scan)

        // default fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment())
                .commit()
            bottomNav.selectedItemId = R.id.nav_beranda
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_beranda -> HomeFragment()
                R.id.nav_campaign -> CampaignFragment()
                R.id.nav_goals -> GoalsFragment()
                R.id.nav_ai_bantuan -> AiBantuanFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> null
            }

            fragment?.let {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, it)
                    .commit()
                true
            } ?: false
        }

        // FAB: buka ScannerActivity (activity, bukan fragment)
        fabQr.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Membantu navigasi dari Fragment ke Fragment (dipakai di beberapa tempat)
     * Jika ingin navigate ke Activity, langsung gunakan startActivity(intent)
     */
    fun navigateTo(
        fragment: Fragment,
        menuItemId: Int? = null,
        addToBackStack: Boolean = true
    ) {
        if (menuItemId != null) {
            bottomNav.selectedItemId = menuItemId
        }

        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)

        if (addToBackStack) {
            transaction.addToBackStack(null)
        }

        transaction.commit()
    }
}