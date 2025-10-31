package com.example.monegoal

import android.content.Intent
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
    private lateinit var etNamaOrtu: EditText
    private lateinit var etUsername: EditText
    private lateinit var etSekolah: EditText
    private lateinit var spJenjang: Spinner
    private lateinit var etEmail: EditText
    private lateinit var etEmailOrtu: EditText
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
        etNamaOrtu = findViewById(R.id.etNamaOrtu)
        etUsername = findViewById(R.id.etUsername)
        etSekolah = findViewById(R.id.etSekolah)
        spJenjang = findViewById(R.id.spJenjang)
        etEmail = findViewById(R.id.etEmail)
        etEmailOrtu = findViewById(R.id.etEmailOrtu)
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
        val namaOrtu = etNamaOrtu.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val sekolah = etSekolah.text.toString().trim()
        val jenjang = if (spJenjang.selectedItemPosition > 0) spJenjang.selectedItem.toString() else ""
        val email = etEmail.text.toString().trim()
        val emailOrtu = etEmailOrtu.text.toString().trim()
        val pass = etPassword.text.toString()
        val passConfirm = etPasswordConfirm.text.toString()

        when {
            nama.isEmpty() -> { etNama.error = "Nama diperlukan"; etNama.requestFocus(); return }
            namaOrtu.isEmpty() -> { etNamaOrtu.error = "Nama Ortu diperlukan"; etNamaOrtu.requestFocus(); return }
            username.isEmpty() -> { etUsername.error = "Username diperlukan"; etUsername.requestFocus(); return }
            sekolah.isEmpty() -> { etSekolah.error = "Asal sekolah diperlukan"; etSekolah.requestFocus(); return }
            jenjang.isEmpty() -> { Toast.makeText(this, "Pilih jenjang sekolah", Toast.LENGTH_SHORT).show(); return }
            email.isEmpty() -> { etEmail.error = "Email diperlukan"; etEmail.requestFocus(); return }
            emailOrtu.isEmpty() -> { etEmailOrtu.error = "Email Orang Tua diperlukan"; etEmailOrtu.requestFocus(); return }
            pass.length < 6 -> { etPassword.error = "Password minimal 6 karakter"; etPassword.requestFocus(); return }
            pass != passConfirm -> { etPasswordConfirm.error = "Password tidak cocok"; etPasswordConfirm.requestFocus(); return }
        }

        btnRegister.isEnabled = false
        progressDialog?.show()

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    val uid = firebaseUser?.uid ?: return@addOnCompleteListener showError("UID tidak ditemukan")

                    firebaseUser.updateProfile(userProfileChangeRequest { displayName = nama })

                    val inviteCode = generateInviteCode()
                    val userMap = hashMapOf(
                        "uid" to uid,
                        "name" to nama,
                        "parentName" to namaOrtu,
                        "username" to username,
                        "school" to sekolah,
                        "grade" to jenjang,
                        "email" to email,
                        "balance" to 0,
                        "points" to 0,
                        "inviteCode" to inviteCode,
                        "parents" to arrayListOf(emailOrtu),
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    firestore.collection("users").document(uid)
                        .set(userMap)
                        .addOnSuccessListener {
                            linkParentWithChild(uid, namaOrtu, emailOrtu, inviteCode)
                        }
                        .addOnFailureListener { e ->
                            firebaseUser.delete()
                            showError("Gagal menyimpan data: ${e.message}")
                        }
                } else {
                    showError(task.exception?.localizedMessage ?: "Registrasi gagal")
                }
            }
    }

    private fun linkParentWithChild(
        uid: String,
        namaOrtu: String,
        emailOrtu: String,
        inviteCode: String
    ) {
        val parentId = encodeEmailToId(emailOrtu)
        val parentRef = firestore.collection("parents").document(parentId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(parentRef)
            if (snapshot.exists()) {
                val currentChildren = snapshot.get("childrenUIDs") as? List<String> ?: emptyList()
                val updatedChildren = (currentChildren + uid).distinct()
                val totalChildren = updatedChildren.size
                transaction.update(
                    parentRef,
                    mapOf("childrenUIDs" to updatedChildren, "totalChildren" to totalChildren)
                )
            } else {
                val parentData = hashMapOf(
                    "email" to emailOrtu,
                    "name" to namaOrtu,
                    "childrenUIDs" to arrayListOf(uid),
                    "totalChildren" to 1,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                transaction.set(parentRef, parentData)
            }
        }.addOnSuccessListener {
            progressDialog?.dismiss()
            btnRegister.isEnabled = true

            // setelah semua sukses -> langsung buka HomeActivity
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra("inviteCode", inviteCode)
            intent.putExtra("emailOrtu", emailOrtu)
            startActivity(intent)
            finish()
        }.addOnFailureListener { e ->
            showError("Gagal menyimpan data ortu: ${e.message}")
        }
    }

    private fun encodeEmailToId(email: String): String {
        return email.replace(".", "_").replace("@", "_at_")
    }

    private fun showError(message: String) {
        progressDialog?.dismiss()
        btnRegister.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}