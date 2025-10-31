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
    private lateinit var tvOrtu: TextView
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
        tvOrtu = findViewById(R.id.tvOrtu)
        cbRemember = findViewById(R.id.cbRemember)

        // edge-to-edge padding (optional)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val inviteCode = intent.getStringExtra("inviteCode")
        val emailOrtu = intent.getStringExtra("emailOrtu")

        if (!inviteCode.isNullOrEmpty()) {
            showInviteCodeDialog(inviteCode, emailOrtu)
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

        tvOrtu.setOnClickListener {
            startActivity(Intent(this@LoginActivity, LoginOrtuActivity::class.java))
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
    private fun showInviteCodeDialog(inviteCode: String, parentEmail: String? = null) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Kode Undangan Orang Tua")
        builder.setMessage(
            "Bagikan kode ini ke orang tua agar dapat terhubung:\n\n$inviteCode\n\n" +
                    (parentEmail?.let { "Email ortu: $it\n\n" } ?: "")
        )
        builder.setPositiveButton("OK") { d, _ -> d.dismiss() }
        builder.setNeutralButton("Salin") { _, _ ->
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("invite_code", inviteCode)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Kode disalin ke clipboard", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Bagikan") { _, _ ->
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_SUBJECT, "Kode Undangan MoneGoal")
            intent.putExtra(Intent.EXTRA_TEXT, "Kode undangan: $inviteCode\nEmail ortu: $parentEmail")
            startActivity(Intent.createChooser(intent, "Bagikan kode lewat"))
        }
        builder.show()
    }
}