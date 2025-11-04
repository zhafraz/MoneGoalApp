package com.example.monegoal

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.*

class TopupActivity : AppCompatActivity() {

    private lateinit var tvNominalDisplay: TextView
    private lateinit var tvUserNameTopup: TextView
    private lateinit var inputCustomNominal: EditText
    private lateinit var btnLanjut: LinearLayout
    private lateinit var tvSaldo: TextView

    private var selectedNominal: Int = 0
    private var currentSaldoOrtu: Long = 0L

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // IMPORTANT: TopupActivity harus dipanggil dengan extra "childId" (UID anak)
    private var childId: String? = null
    private var childName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topup)

        childId = intent.getStringExtra("childId")
        childName = intent.getStringExtra("childName")

        initViews()
        setupNominalCards()
        setupCustomInput()
        setupBtnLanjut()
        loadSaldoOrtu()
    }

    private fun initViews() {
        tvNominalDisplay = findViewById(R.id.tvNominalDisplay)
        inputCustomNominal = findViewById(R.id.inputCustomNominal)
        tvUserNameTopup = findViewById(R.id.tvUserNameTopup)
        btnLanjut = findViewById(R.id.btnLanjut)
        tvSaldo = findViewById(R.id.tvSaldo)
    }

    private fun loadSaldoOrtu() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val namaOrtu = doc.getString("name") ?: "Orang Tua"
                    // fallback chain: saldoOrtu -> saldo -> balance
                    val saldoOrtu = doc.getLong("saldoOrtu")
                        ?: doc.getLong("saldo")
                        ?: doc.getLong("balance")
                        ?: 0L

                    tvUserNameTopup.text = namaOrtu
                    currentSaldoOrtu = saldoOrtu
                    updateSaldoText()
                } else {
                    tvUserNameTopup.text = "Orang Tua"
                    currentSaldoOrtu = 0L
                    updateSaldoText()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data pengguna", Toast.LENGTH_SHORT).show()
                tvUserNameTopup.text = "Orang Tua"
            }
    }

    private fun setupNominalCards() {
        val cards = listOf(
            findViewById<CardView>(R.id.card10k) to 10_000,
            findViewById<CardView>(R.id.card20k) to 20_000,
            findViewById<CardView>(R.id.card50k) to 50_000,
            findViewById<CardView>(R.id.card100k) to 100_000,
            findViewById<CardView>(R.id.card150k) to 150_000,
            findViewById<CardView>(R.id.card200k) to 200_000
        )

        cards.forEach { (card, nominal) ->
            card.setOnClickListener {
                selectedNominal = nominal
                inputCustomNominal.text.clear()
                updateNominalDisplay()
                highlightSelectedCard(card, cards.map { it.first })
            }
        }
    }

    private fun highlightSelectedCard(selected: CardView, allCards: List<CardView>) {
        allCards.forEach { card ->
            val colorRes = if (card == selected) R.color.light_blue else R.color.card_default
            card.setCardBackgroundColor(ContextCompat.getColor(this, colorRes))
        }
    }

    private fun setupCustomInput() {
        inputCustomNominal.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().replace("[^0-9]".toRegex(), "")
                selectedNominal = if (input.isNotEmpty()) input.toInt() else 0
                updateNominalDisplay()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupBtnLanjut() {
        btnLanjut.setOnClickListener {
            if (childId.isNullOrBlank()) {
                Toast.makeText(this, "Child ID tidak ditemukan. Pilih anak terlebih dahulu.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (selectedNominal <= 0) {
                Toast.makeText(this, "Pilih atau masukkan nominal terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentSaldoOrtu < selectedNominal) {
                Toast.makeText(this, "Saldo orang tua tidak mencukupi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prosesTopupAtomic(selectedNominal.toLong(), childId!!)
        }
    }

    /**
     * Transaksi atomik:
     * - kurangi saldoOrtu pada dokumen orang tua (current user)
     * - tambahkan saldoAnak pada dokumen anak (childId)
     * - catat transaksi di masing2 subcollection transactions
     */
    private fun prosesTopupAtomic(amount: Long, childUid: String) {
        val parentUid = auth.currentUser?.uid ?: return
        val parentRef = db.collection("users").document(parentUid)
        val childRef = db.collection("users").document(childUid)

        // gunakan runTransaction agar both updates atomic
        db.runTransaction { tx ->
            // ambil current parent saldo (fallback)
            val parentSnap = tx.get(parentRef)
            val parentSaldoOrtu = parentSnap.getLong("saldoOrtu")
                ?: parentSnap.getLong("saldo")
                ?: parentSnap.getLong("balance")
                ?: 0L

            if (parentSaldoOrtu < amount) {
                throw Exception("Saldo ortu tidak mencukupi")
            }

            // ambil current child saldo
            val childSnap = tx.get(childRef)
            val childSaldoAnak = childSnap.getLong("saldoAnak")
                ?: childSnap.getLong("saldo")
                ?: childSnap.getLong("balance")
                ?: 0L

            // update nilai
            tx.update(parentRef, "saldoOrtu", parentSaldoOrtu - amount)
            // juga update legacy field 'saldo' for backward compatibility (optional)
            tx.update(parentRef, "saldo", (parentSnap.getLong("saldo") ?: parentSaldoOrtu) - amount)

            tx.update(childRef, "saldoAnak", childSaldoAnak + amount)
            tx.update(childRef, "saldo", (childSnap.getLong("saldo") ?: childSaldoAnak) + amount)

            // simpan transaksi (best-effort: simpan doc langsung di subcollection dengan serverTimestamp)
            val parentTx = hashMapOf(
                "type" to "pengeluaran",
                "category" to "topup",
                "amount" to amount,
                "timestamp" to FieldValue.serverTimestamp(),
                "note" to "Topup ke anak ${childUid}"
            )
            val childTx = hashMapOf(
                "type" to "pemasukan",
                "category" to "topup",
                "amount" to amount,
                "timestamp" to FieldValue.serverTimestamp(),
                "note" to "Topup dari orang tua ${parentUid}"
            )

            // jangan gunakan tx.set() untuk subcollections (tidak memungkinkan per rules) — gunakan add setelah transaksi berhasil.
            // Namun Firestore transaction supports set on any document; kita lebih aman menambahkan after success.
            // Return map just to satisfy runTransaction lambda (value unused)
            null
        }.addOnSuccessListener {
            // setelah sukses transaction, kita menambahkan dokumen transaksi (non-transactional)
            // parent transaction doc:
            val parentTx = hashMapOf(
                "type" to "pengeluaran",
                "category" to "topup",
                "amount" to amount,
                "timestamp" to FieldValue.serverTimestamp(),
                "note" to "Topup ke anak ${childId}"
            )
            db.collection("users").document(parentUid).collection("transactions").add(parentTx)

            val childTx = hashMapOf(
                "type" to "pemasukan",
                "category" to "topup",
                "amount" to amount,
                "timestamp" to FieldValue.serverTimestamp(),
                "note" to "Topup dari orang tua ${parentUid}"
            )
            db.collection("users").document(childUid).collection("transactions").add(childTx)

            // refresh local saldo orang tua
            db.collection("users").document(parentUid).get()
                .addOnSuccessListener { refreshed ->
                    currentSaldoOrtu = refreshed.getLong("saldoOrtu")
                        ?: refreshed.getLong("saldo")
                                ?: refreshed.getLong("balance")
                                ?: 0L
                    updateSaldoText()
                }

            showSuccessDialog(amount)
            resetNominal()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Top-up gagal: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateNominalDisplay() {
        val formatted = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(selectedNominal)
        tvNominalDisplay.text = formatted.replace(",00", "")
    }

    private fun updateSaldoText() {
        val formattedSaldo = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(currentSaldoOrtu)
        tvSaldo.text = "Saldo: ${formattedSaldo.replace(",00", "")}"
    }

    private fun resetNominal() {
        selectedNominal = 0
        tvNominalDisplay.text = "Rp 0"
        inputCustomNominal.text.clear()
    }

    private fun showSuccessDialog(amount: Long) {
        val formatted = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(amount).replace(",00", "")
        val title = if (!childName.isNullOrBlank()) "Top-up ke $childName Berhasil 🎉" else "Top-up Berhasil 🎉"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Anda berhasil top-up sejumlah $formatted ke akun anak.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}