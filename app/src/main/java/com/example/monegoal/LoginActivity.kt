package com.example.monegoal

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: androidx.appcompat.widget.AppCompatButton
    private lateinit var tvRegister: TextView
    private lateinit var cbRemember: CheckBox

    private lateinit var auth: FirebaseAuth
    private var progressDialog: AlertDialog? = null

    private val PREFS = "monegoal_prefs"
    private val KEY_REMEMBER = "remember_email"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // init firebase
        auth = FirebaseAuth.getInstance()

        // bind views
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvRegister = findViewById(R.id.tvRegister)
        cbRemember = findViewById(R.id.cbRemember)

        // edge-to-edge padding (optional)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // progress dialog
        progressDialog = AlertDialog.Builder(this)
            .setView(ProgressBar(this).apply { isIndeterminate = true })
            .setCancelable(false)
            .create()

        // Restore saved email if remember enabled
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedEmail = prefs.getString(KEY_REMEMBER, null)
        if (!savedEmail.isNullOrEmpty()) {
            etEmail.setText(savedEmail)
            cbRemember.isChecked = true
        }

        // Open RegisterActivity when user taps "Daftar Sekarang"
        tvRegister.setOnClickListener {
            startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
        }

        // Login action
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString()

            if (email.isEmpty()) {
                etEmail.error = "Masukkan email"
                etEmail.requestFocus()
                return@setOnClickListener
            }
            if (pass.length < 6) {
                etPassword.error = "Password minimal 6 karakter"
                etPassword.requestFocus()
                return@setOnClickListener
            }

            // show progress
            progressDialog?.show()
            btnLogin.isEnabled = false

            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    progressDialog?.dismiss()
                    btnLogin.isEnabled = true

                    if (task.isSuccessful) {
                        if (cbRemember.isChecked) {
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putString(KEY_REMEMBER, email)
                                .apply()
                        } else {
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .remove(KEY_REMEMBER)
                                .apply()
                        }

                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    } else {
                        val err = task.exception?.localizedMessage ?: "Gagal login"
                        Toast.makeText(this@LoginActivity, err, Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}