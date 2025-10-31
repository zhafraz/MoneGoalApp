package com.example.monegoal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_navigation)

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
                R.id.nav_program -> ProgramFragment()
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
    }
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