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
import java.text.NumberFormat
import java.util.*

class TopupFragment : Fragment() {

    private lateinit var tvNominalDisplay: TextView
    private lateinit var inputCustomNominal: EditText
    private lateinit var btnLanjut: LinearLayout
    private lateinit var tvSaldo: TextView

    private var selectedNominal: Int = 0
    private var currentSaldo: Int = 45000 // contoh saldo awal, bisa dari SharedPref / database

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_topup, container, false) // pakai layout kamu
        initViews(view)
        setupNominalCards(view)
        setupCustomInput()
        setupBtnLanjut()
        return view
    }

    private fun initViews(view: View) {
        tvNominalDisplay = view.findViewById(R.id.tvNominalDisplay)
        inputCustomNominal = view.findViewById(R.id.inputCustomNominal)
        btnLanjut = view.findViewById(R.id.btnLanjut)
        tvSaldo = view.findViewById(R.id.tvSaldo)

        updateSaldoText()
    }

    private fun setupNominalCards(view: View) {
        val cards = listOf(
            view.findViewById<CardView>(R.id.card10k) to 10000,
            view.findViewById<CardView>(R.id.card20k) to 20000,
            view.findViewById<CardView>(R.id.card50k) to 50000,
            view.findViewById<CardView>(R.id.card100k) to 100000,
            view.findViewById<CardView>(R.id.card150k) to 150000,
            view.findViewById<CardView>(R.id.card200k) to 200000
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
            card.setCardBackgroundColor(
                resources.getColor(
                    if (card == selected) R.color.light_blue else R.color.card_default,
                    null
                )
            )
        }
    }

    private fun setupCustomInput() {
        inputCustomNominal.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
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

            currentSaldo += selectedNominal
            updateSaldoText()
            showSuccessDialog(selectedNominal)
            selectedNominal = 0
            tvNominalDisplay.text = "Rp 0"
            inputCustomNominal.text.clear()
        }
    }

    private fun updateNominalDisplay() {
        val formatted = NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(selectedNominal)
        tvNominalDisplay.text = formatted.replace(",00", "")
    }

    private fun updateSaldoText() {
        val formattedSaldo = NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(currentSaldo)
        tvSaldo.text = "Saldo: ${formattedSaldo.replace(",00", "")}"
    }

    private fun showSuccessDialog(amount: Int) {
        val formatted = NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(amount).replace(",00", "")
        AlertDialog.Builder(requireContext())
            .setTitle("Top-up Berhasil 🎉")
            .setMessage("Anda berhasil top-up sejumlah $formatted.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
