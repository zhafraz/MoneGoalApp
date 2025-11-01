package com.example.monegoal

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TopupActivity : AppCompatActivity() {

    private lateinit var tvNominalDisplay: TextView
    private lateinit var inputCustomNominal: EditText
    private lateinit var tvSaldo: TextView
    private lateinit var btnLanjut: LinearLayout

    private var nominalDipilih = 0
    private var saldoOrtu = 0
    private var saldoAnak = 0
    private var anakUid: String? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topup)

        tvNominalDisplay = findViewById(R.id.tvNominalDisplay)
        inputCustomNominal = findViewById(R.id.inputCustomNominal)
        tvSaldo = findViewById(R.id.tvSaldo)
        btnLanjut = findViewById(R.id.btnLanjut)

        val cardNominals = mapOf(
            R.id.card10k to 10000,
            R.id.card20k to 20000,
            R.id.card50k to 50000,
            R.id.card100k to 100000,
            R.id.card150k to 150000,
            R.id.card200k to 200000
        )

        val parentEmail = auth.currentUser?.email ?: return
        val parentId = encodeEmailToId(parentEmail)

        firestore.collection("parents").document(parentId)
            .get()
            .addOnSuccessListener { doc ->
                saldoOrtu = (doc.getLong("saldo") ?: 0).toInt()
                val children = doc.get("childrenUIDs") as? List<String>
                if (!children.isNullOrEmpty()) {
                    anakUid = children[0]
                    ambilSaldoAnak(anakUid!!)
                }
            }

        for ((id, nominal) in cardNominals) {
            findViewById<CardView>(id).setOnClickListener {
                nominalDipilih = nominal
                tvNominalDisplay.text = "Rp ${formatRupiah(nominal)}"
                inputCustomNominal.setText(nominal.toString())
            }
        }

        inputCustomNominal.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                nominalDipilih = inputCustomNominal.text.toString().toIntOrNull() ?: 0
                tvNominalDisplay.text = "Rp ${formatRupiah(nominalDipilih)}"
            }
        }

        btnLanjut.setOnClickListener {
            if (nominalDipilih <= 0) {
                Toast.makeText(this, "Masukkan nominal terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (nominalDipilih > saldoOrtu) {
                Toast.makeText(this, "Saldo orang tua tidak mencukupi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            anakUid?.let { transferSaldoKeAnak(it, nominalDipilih) }
        }
    }

    private fun ambilSaldoAnak(uid: String) {
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                saldoAnak = (doc.getLong("balance") ?: 0).toInt()
                tvSaldo.text = "Saldo Anak: Rp ${formatRupiah(saldoAnak)}"
            }
    }

    private fun transferSaldoKeAnak(anakUid: String, nominal: Int) {
        val parentEmail = auth.currentUser?.email ?: return
        val parentId = encodeEmailToId(parentEmail)

        val newSaldoAnak = saldoAnak + nominal
        val newSaldoOrtu = saldoOrtu - nominal

        val parentRef = firestore.collection("parents").document(parentId)
        val anakRef = firestore.collection("users").document(anakUid)

        firestore.runBatch { batch ->
            batch.update(parentRef, "saldo", newSaldoOrtu)
            batch.update(anakRef, "balance", newSaldoAnak)
        }.addOnSuccessListener {
            saldoOrtu = newSaldoOrtu
            saldoAnak = newSaldoAnak
            tvSaldo.text = "Saldo Anak: Rp ${formatRupiah(saldoAnak)}"
            tampilkanPopupSukses(nominal)
        }.addOnFailureListener {
            Toast.makeText(this, "Top-up gagal: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun tampilkanPopupSukses(nominal: Int) {
        AlertDialog.Builder(this)
            .setTitle("Top-up Berhasil 🎉")
            .setMessage("Anda berhasil melakukan top-up sebesar Rp ${formatRupiah(nominal)} ke akun anak.")
            .setPositiveButton("Oke") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun encodeEmailToId(email: String): String {
        return email.replace(".", "_").replace("@", "_at_")
    }

    private fun formatRupiah(value: Int): String {
        return String.format("%,d", value).replace(",", ".")
    }
}