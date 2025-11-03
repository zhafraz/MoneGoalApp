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
    private var sumberDanaOrtu: Long = 100_000L // sementara dummy; nanti ambil dr akun ortu

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_topup, container, false) // layout XML kamu
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
                    val saldo = doc.getLong("balance") ?: 0L

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
                Toast.makeText(requireContext(), "Pilih atau masukkan nominal terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // cek sumber dana (dummy) harus >= topup
            if (sumberDanaOrtu < selectedNominal) {
                Toast.makeText(requireContext(), "Saldo sumber dana tidak mencukupi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prosesTopupAtomic(selectedNominal.toLong())
        }
    }

    /**
     * Gunakan update atomic dengan FieldValue.increment agar aman concurrent.
     * - menambah balance anak
     * - mengurangi sumber dana (di sini hanya dummy local, tapi contoh operasi di Firestore disediakan)
     */
    private fun prosesTopupAtomic(amount: Long) {
        val userId = auth.currentUser?.uid ?: return

        val userRef = db.collection("users").document(userId)

        // 1) update balance anak secara atomic
        userRef.update("balance", FieldValue.increment(amount))
            .addOnSuccessListener {
                // jika kamu juga menyimpan transaksi/history:
                val tx = hashMapOf(
                    "type" to "topup",
                    "amount" to amount,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "source" to "rekening_orangtua_dummy"
                )
                userRef.collection("transactions").add(tx) // best-effort, tidak memblokir UI

                // kurangi sumber dana lokal (jika nanti di parent koleksi, lakukan update di koleksi parent)
                sumberDanaOrtu -= amount
                // refresh UI saldo anak dari Firestore (atau kita menambah variabel lokal)
                // ambil saldo terbaru agar UI sinkron
                userRef.get().addOnSuccessListener { refreshed ->
                    currentSaldoAnak = (refreshed.getLong("balance") ?: 0L)
                    updateSaldoText()
                    showSuccessDialog(amount)
                    resetNominal()
                }.addOnFailureListener {
                    // meskipun gagal fetch, tetap tunjukkan sukses (saldo sudah diincrement di server)
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
        val formatted = NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(selectedNominal)
        tvNominalDisplay.text = formatted.replace(",00", "")
    }

    private fun updateSaldoText() {
        val formattedSaldo = NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(currentSaldoAnak)
        tvSaldo.text = "Saldo: ${formattedSaldo.replace(",00", "")}"
    }

    private fun resetNominal() {
        selectedNominal = 0
        tvNominalDisplay.text = "Rp 0"
        inputCustomNominal.text.clear()
    }

    private fun showSuccessDialog(amount: Long) {
        val formatted = NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(amount).replace(",00", "")
        AlertDialog.Builder(requireContext())
            .setTitle("Top-up Berhasil 🎉")
            .setMessage("Anda berhasil top-up sejumlah $formatted.\n\nSaldo telah ditambahkan ke akun anak.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}