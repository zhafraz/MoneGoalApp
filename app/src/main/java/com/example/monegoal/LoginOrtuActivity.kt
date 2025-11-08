package com.example.monegoal

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class LoginOrtuActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etCode: EditText
    private lateinit var tvAnak: TextView
    private lateinit var btnLogin: androidx.appcompat.widget.AppCompatButton
    private var progressDialog: AlertDialog? = null

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_ortu) // pastikan nama layout sesuai

        // atur edge-to-edge padding (sama gaya seperti activity lain)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etEmail = findViewById(R.id.etEmail)
        etCode = findViewById(R.id.etCode)
        btnLogin = findViewById(R.id.btnLogin)
        tvAnak = findViewById(R.id.tvAnak)

        progressDialog = AlertDialog.Builder(this)
            .setView(android.widget.ProgressBar(this).apply { isIndeterminate = true })
            .setCancelable(false)
            .create()

        // tombol untuk kembali ke login anak
        tvAnak.setOnClickListener {
            startActivity(Intent(this@LoginOrtuActivity, LoginActivity::class.java))
        }

        btnLogin.setOnClickListener { attemptLinkParent() }
    }

    private fun attemptLinkParent() {
        val email = etEmail.text.toString().trim()
        val code = etCode.text.toString().trim()

        if (email.isEmpty()) {
            etEmail.error = "Email diperlukan"
            etEmail.requestFocus()
            return
        }
        if (code.isEmpty()) {
            etCode.error = "Kode undangan diperlukan"
            etCode.requestFocus()
            return
        }

        progressDialog?.show()

        val usersRef = firestore.collection("users")
        val parentsRef = firestore.collection("parents")

        // 1️⃣ Pastikan email ini BUKAN email anak
        usersRef.whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { userQuery ->
                if (!userQuery.isEmpty) {
                    progressDialog?.dismiss()
                    Toast.makeText(
                        this,
                        "Email ini terdaftar sebagai akun anak. Gunakan akun orang tua untuk login.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                // 2️⃣ Cari anak berdasarkan kode undangan
                usersRef.whereEqualTo("inviteCode", code)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { q ->
                        if (q.isEmpty) {
                            progressDialog?.dismiss()
                            Toast.makeText(this, "Kode undangan tidak ditemukan.", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        val childDoc = q.documents[0]
                        val childUid = childDoc.id
                        val childName = childDoc.getString("name") ?: "Anak"

                        val parentDocId = encodeEmailToId(email)
                        val parentRef = parentsRef.document(parentDocId)
                        val childRef = usersRef.document(childUid)

                        parentRef.get()
                            .addOnSuccessListener { pDoc ->
                                val batch = firestore.batch()

                                if (pDoc.exists()) {
                                    batch.set(parentRef, mapOf(
                                        "email" to email,
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    ), com.google.firebase.firestore.SetOptions.merge())

                                    batch.update(parentRef, "childrenUIDs", FieldValue.arrayUnion(childUid))
                                    batch.update(parentRef, "totalChildren", FieldValue.increment(1))
                                } else {
                                    val parentData = hashMapOf(
                                        "email" to email,
                                        "name" to email.substringBefore("@").replace('.', ' ').capitalizeWords(),
                                        "childrenUIDs" to arrayListOf(childUid),
                                        "totalChildren" to 1,
                                        "createdAt" to FieldValue.serverTimestamp(),
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    )
                                    batch.set(parentRef, parentData)
                                }

                                batch.update(childRef, "parents", FieldValue.arrayUnion(parentDocId))

                                batch.commit()
                                    .addOnSuccessListener {
                                        progressDialog?.dismiss()
                                        Toast.makeText(
                                            this,
                                            "Berhasil terhubung ke $childName",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        val intent = Intent(this@LoginOrtuActivity, HomeOrtuActivity::class.java)
                                        intent.putExtra("parentEmail", email)
                                        startActivity(intent)
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        progressDialog?.dismiss()
                                        Toast.makeText(this, "Gagal menghubungkan: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                            }
                            .addOnFailureListener { e ->
                                progressDialog?.dismiss()
                                Toast.makeText(this, "Gagal membaca data parent: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        progressDialog?.dismiss()
                        Toast.makeText(this, "Kesalahan jaringan: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                progressDialog?.dismiss()
                Toast.makeText(this, "Kesalahan jaringan: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun encodeEmailToId(email: String): String {
        return email.replace("@", "_at_").replace(".", "_dot_")
    }

    // extension helper: capitalize tiap kata (sederhana)
    private fun String.capitalizeWords(): String =
        this.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}