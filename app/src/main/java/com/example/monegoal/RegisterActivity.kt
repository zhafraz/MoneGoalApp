package com.example.monegoal

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var etNama: EditText
    private lateinit var etUsername: EditText
    private lateinit var etSekolah: EditText
    private lateinit var spJenjang: Spinner
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPasswordConfirm: EditText
    private lateinit var btnRegister: androidx.appcompat.widget.AppCompatButton
    private lateinit var tvLogin: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        etNama = findViewById(R.id.etNama)
        etUsername = findViewById(R.id.etUsername)
        etSekolah = findViewById(R.id.etSekolah)
        spJenjang = findViewById(R.id.spJenjang)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm)
        btnRegister = findViewById(R.id.btnRegister)
        tvLogin = findViewById(R.id.tvLogin)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val jenjangList = listOf("Pilih jenjang sekolah", "SD", "SMP", "SMA", "SMK")
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, jenjangList) {
            override fun isEnabled(position: Int): Boolean = position != 0
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spJenjang.adapter = adapter

        progressDialog = AlertDialog.Builder(this)
            .setView(ProgressBar(this).apply { isIndeterminate = true })
            .setCancelable(false)
            .create()

        btnRegister.setOnClickListener { registerUser() }
        tvLogin.setOnClickListener { finish() }
    }

    private fun registerUser() {
        val nama = etNama.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val sekolah = etSekolah.text.toString().trim()
        val jenjang = if (spJenjang.selectedItemPosition > 0) spJenjang.selectedItem.toString() else ""
        val email = etEmail.text.toString().trim()
        val pass = etPassword.text.toString()
        val passConfirm = etPasswordConfirm.text.toString()

        when {
            nama.isEmpty() -> {
                etNama.error = "Nama diperlukan"
                etNama.requestFocus()
                return
            }
            username.isEmpty() -> {
                etUsername.error = "Username diperlukan"
                etUsername.requestFocus()
                return
            }
            sekolah.isEmpty() -> {
                etSekolah.error = "Asal sekolah diperlukan"
                etSekolah.requestFocus()
                return
            }
            jenjang.isEmpty() -> {
                Toast.makeText(this, "Pilih jenjang sekolah", Toast.LENGTH_SHORT).show()
                return
            }
            email.isEmpty() -> {
                etEmail.error = "Email diperlukan"
                etEmail.requestFocus()
                return
            }
            pass.length < 6 -> {
                etPassword.error = "Password minimal 6 karakter"
                etPassword.requestFocus()
                return
            }
            pass != passConfirm -> {
                etPasswordConfirm.error = "Password tidak cocok"
                etPasswordConfirm.requestFocus()
                return
            }
        }

        btnRegister.isEnabled = false
        progressDialog?.show()

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    val uid = firebaseUser?.uid

                    if (uid == null) {
                        showError("Registrasi gagal: UID tidak tersedia")
                        return@addOnCompleteListener
                    }

                    val profileUpdates = userProfileChangeRequest { displayName = nama }
                    firebaseUser.updateProfile(profileUpdates)

                    val userMap = hashMapOf<String, Any?>(
                        "uid" to uid,
                        "name" to nama,
                        "username" to username,
                        "school" to sekolah,
                        "grade" to jenjang,
                        "email" to email,
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    firestore.collection("users").document(uid)
                        .set(userMap)
                        .addOnSuccessListener {
                            progressDialog?.dismiss()
                            btnRegister.isEnabled = true
                            Toast.makeText(this, "Registrasi berhasil. Silakan login.", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            firebaseUser.delete()
                            showError("Gagal menyimpan data: ${e.message}")
                        }
                } else {
                    val err = authTask.exception?.localizedMessage ?: "Registrasi gagal"
                    showError(err)
                }
            }
    }

    private fun showError(message: String) {
        progressDialog?.dismiss()
        btnRegister.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}