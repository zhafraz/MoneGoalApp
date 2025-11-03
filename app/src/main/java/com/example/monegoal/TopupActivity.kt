package com.example.monegoal

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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
    private var sumberDanaOrtu: Long = 500_000L // contoh dummy saldo ortu

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topup)
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
                    // ambil nama orang tua
                    val namaOrtu = doc.getString("parentName") ?: "Orang Tua"
                    val saldo = doc.getLong("balance") ?: 0L

                    tvUserNameTopup.text = namaOrtu
                    currentSaldoOrtu = saldo
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
            card.setCardBackgroundColor(resources.getColor(colorRes, null))
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
            if (selectedNominal <= 0) {
                Toast.makeText(this, "Pilih atau masukkan nominal terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (sumberDanaOrtu < selectedNominal) {
                Toast.makeText(this, "Saldo sumber dana tidak mencukupi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prosesTopupAtomic(selectedNominal.toLong())
        }
    }

    private fun prosesTopupAtomic(amount: Long) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(userId)

        // update balance orang tua secara atomic
        userRef.update("balance", FieldValue.increment(-amount))
            .addOnSuccessListener {
                sumberDanaOrtu -= amount

                // misal simpan transaksi top-up anak
                val tx = hashMapOf(
                    "type" to "topup",
                    "amount" to amount,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "target" to "akun_anak"
                )
                userRef.collection("transactions").add(tx)

                // refresh saldo
                userRef.get().addOnSuccessListener { refreshed ->
                    currentSaldoOrtu = (refreshed.getLong("balance") ?: 0L)
                    updateSaldoText()
                    showSuccessDialog(amount)
                    resetNominal()
                }.addOnFailureListener {
                    currentSaldoOrtu -= amount
                    updateSaldoText()
                    showSuccessDialog(amount)
                    resetNominal()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Top-up gagal: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateNominalDisplay() {
        val formatted = NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(selectedNominal)
        tvNominalDisplay.text = formatted.replace(",00", "")
    }

    private fun updateSaldoText() {
        val formattedSaldo = NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(currentSaldoOrtu)
        tvSaldo.text = "Saldo: ${formattedSaldo.replace(",00", "")}"
    }

    private fun resetNominal() {
        selectedNominal = 0
        tvNominalDisplay.text = "Rp 0"
        inputCustomNominal.text.clear()
    }

    private fun showSuccessDialog(amount: Long) {
        val formatted = NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(amount).replace(",00", "")
        AlertDialog.Builder(this)
            .setTitle("Top-up Berhasil 🎉")
            .setMessage("Anda berhasil top-up sejumlah $formatted ke akun anak.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

}