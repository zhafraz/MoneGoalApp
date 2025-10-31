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

        // cari anak (dok users) yang inviteCode == code
        firestore.collection("users")
            .whereEqualTo("inviteCode", code)
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

                // id dok parent di koleksi "parents" menggunakan encoding email
                val parentDocId = encodeEmailToId(email)
                val parentRef = firestore.collection("parents").document(parentDocId)
                val childRef = firestore.collection("users").document(childUid)

                // ambil snapshot parent untuk memutuskan create/update
                parentRef.get()
                    .addOnSuccessListener { pDoc ->
                        val batch = firestore.batch()

                        if (pDoc.exists()) {
                            // jika dokumen parent ada, update fields (merge) dan tambahkan childUid jika belum ada
                            // gunakan arrayUnion untuk childrenUIDs
                            batch.set(parentRef, mapOf(
                                "email" to email,
                                "updatedAt" to FieldValue.serverTimestamp()
                            ), com.google.firebase.firestore.SetOptions.merge())

                            // tambahkan childUid ke array (arrayUnion aman untuk duplikat)
                            batch.update(parentRef, "childrenUIDs", FieldValue.arrayUnion(childUid))

                            // jika Anda ingin maintain totalChildren, gunakan increment
                            batch.update(parentRef, "totalChildren", FieldValue.increment(1))
                        } else {
                            // buat dokumen parent baru
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

                        // update dok anak: tambahkan parentDocId ke field "parents"
                        // (disini kami menyimpan parentDocId agar konsisten dengan encodeEmailToId)
                        batch.update(childRef, "parents", FieldValue.arrayUnion(parentDocId))

                        // commit batch
                        batch.commit()
                            .addOnSuccessListener {
                                progressDialog?.dismiss()
                                Toast.makeText(this, "Berhasil terhubung ke $childName", Toast.LENGTH_SHORT).show()

                                // mulai HomeOrtuActivity dan kirim parentEmail supaya Home tahu siapa parent
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

    private fun encodeEmailToId(email: String): String {
        return email.replace("@", "_at_").replace(".", "_dot_")
    }

    // extension helper: capitalize tiap kata (sederhana)
    private fun String.capitalizeWords(): String =
        this.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}