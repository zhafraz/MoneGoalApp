package com.example.monegoal

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.*

class TopupFragment : Fragment() {

    private lateinit var tvNominalDisplay: TextView
    private lateinit var tvUserNameTopup: TextView
    private lateinit var inputCustomNominal: EditText
    private lateinit var btnLanjut: LinearLayout
    private lateinit var tvSaldo: TextView

    private var selectedNominal: Int = 0
    private var currentSaldoAnak: Long = 0L

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_topup, container, false)
        initViews(view)
        setupNominalCards(view)
        setupCustomInput()
        setupBtnLanjut()
        loadSaldoAnak()
        return view
    }

    private fun initViews(view: View) {
        tvNominalDisplay = view.findViewById(R.id.tvNominalDisplay)
        inputCustomNominal = view.findViewById(R.id.inputCustomNominal)
        tvUserNameTopup = view.findViewById(R.id.tvUserNameTopup)
        btnLanjut = view.findViewById(R.id.btnLanjut)
        tvSaldo = view.findViewById(R.id.tvSaldo)
    }

    private fun loadSaldoAnak() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val nama = doc.getString("name") ?: "Pengguna"
                    val saldo = doc.getLong("saldoAnak")
                        ?: doc.getLong("saldo")
                        ?: doc.getLong("balance")
                        ?: 0L

                    tvUserNameTopup.text = nama
                    currentSaldoAnak = saldo
                    updateSaldoText()
                } else {
                    tvUserNameTopup.text = "Pengguna"
                    currentSaldoAnak = 0L
                    updateSaldoText()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Gagal memuat data pengguna", Toast.LENGTH_SHORT).show()
                tvUserNameTopup.text = "Pengguna"
            }
    }

    private fun setupNominalCards(view: View) {
        val cards = listOf(
            view.findViewById<CardView>(R.id.card10k) to 10_000,
            view.findViewById<CardView>(R.id.card20k) to 20_000,
            view.findViewById<CardView>(R.id.card50k) to 50_000,
            view.findViewById<CardView>(R.id.card100k) to 100_000,
            view.findViewById<CardView>(R.id.card150k) to 150_000,
            view.findViewById<CardView>(R.id.card200k) to 200_000
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
            card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), colorRes))
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
                Toast.makeText(requireContext(), "Pilih atau masukkan nominal terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prosesTopupAnak(selectedNominal.toLong())
        }
    }

    private fun prosesTopupAnak(amount: Long) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(userId)

        userRef.update("saldoAnak", FieldValue.increment(amount))
            .addOnSuccessListener {
                // tambahkan transaksi pemasukan
                val tx = hashMapOf(
                    "type" to "pemasukan",
                    "category" to "topup",
                    "amount" to amount,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "note" to "Topup via fragment anak"
                )
                userRef.collection("transactions").add(tx)

                // refresh UI
                userRef.get().addOnSuccessListener { refreshed ->
                    currentSaldoAnak = refreshed.getLong("saldoAnak")
                        ?: refreshed.getLong("saldo")
                                ?: refreshed.getLong("balance")
                                ?: (currentSaldoAnak + amount)
                    updateSaldoText()
                    showSuccessDialog(amount)
                    resetNominal()
                }.addOnFailureListener {
                    currentSaldoAnak += amount
                    updateSaldoText()
                    showSuccessDialog(amount)
                    resetNominal()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Top-up gagal: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateNominalDisplay() {
        val formatted = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(selectedNominal)
        tvNominalDisplay.text = formatted.replace(",00", "")
    }

    private fun updateSaldoText() {
        val formattedSaldo = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(currentSaldoAnak)
        tvSaldo.text = "Saldo: ${formattedSaldo.replace(",00", "")}"
    }

    private fun resetNominal() {
        selectedNominal = 0
        tvNominalDisplay.text = "Rp 0"
        inputCustomNominal.text.clear()
    }

    private fun showSuccessDialog(amount: Long) {
        val formatted = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(amount).replace(",00", "")
        AlertDialog.Builder(requireContext())
            .setTitle("Top-up Berhasil 🎉")
            .setMessage("Anda berhasil menambahkan $formatted ke saldo akun.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}