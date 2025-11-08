package com.example.monegoal.ui.anak

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.example.monegoal.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.*

class AjukanDanaFragment : Fragment() {

    private lateinit var inputNominal: EditText
    private lateinit var inputAlasan: EditText
    private lateinit var btnAjukan: LinearLayout

    private lateinit var db: FirebaseFirestore
    private val auth = FirebaseAuth.getInstance()

    private var selectedPriority: String? = null
    private var selectedAmount: Int? = null
    private var saldoAnak: Long = 0L  // ✅ simpan saldo anak di sini

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ajukan_dana, container, false)

        db = FirebaseFirestore.getInstance()

        inputNominal = view.findViewById(R.id.inputcustomnominal)
        inputAlasan = view.findViewById(R.id.inputAlasan)
        btnAjukan = view.findViewById(R.id.btnAjukan)

        setupNominalShortcuts(view)
        setupPrioritySelection(view)
        setupSubmitButton()

        // ✅ Ambil saldo anak dari Firestore
        loadSaldoAnak()

        return view
    }

    private fun loadSaldoAnak() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                saldoAnak = doc.getLong("saldoAnak")
                    ?: doc.getLong("saldo")
                            ?: doc.getLong("balance")
                            ?: 0L
            }
            .addOnFailureListener {
                saldoAnak = 0L
            }
    }

    private fun setupNominalShortcuts(view: View) {
        val nominalValues = listOf(10000, 20000, 30000, 50000, 75000, 100000)
        val cardIds = listOf(
            R.id.card10000, R.id.card20000, R.id.card30000,
            R.id.card50000, R.id.card75000, R.id.card100000
        )

        for ((index, cardId) in cardIds.withIndex()) {
            val card = view.findViewById<CardView>(cardId)
            val amount = nominalValues[index]
            card.setOnClickListener {
                inputNominal.setText(amount.toString())
                selectedAmount = amount
                resetCardColors(cardIds, view)
                card.setCardBackgroundColor(Color.parseColor("#BBDEFB"))
            }
        }
    }

    private fun resetCardColors(cardIds: List<Int>, view: View) {
        for (id in cardIds) {
            val card = view.findViewById<CardView>(id)
            card.setCardBackgroundColor(Color.parseColor("#F0F9FF"))
        }
    }

    private fun setupPrioritySelection(view: View) {
        val priorityCards = mapOf(
            "Penting" to view.findViewById<CardView>(R.id.cardPenting),
            "Biasa" to view.findViewById<CardView>(R.id.cardBiasa),
            "Nanti" to view.findViewById<CardView>(R.id.cardNanti)
        )

        for ((priority, card) in priorityCards) {
            card.setOnClickListener {
                selectedPriority = priority
                resetPriorityColors(priorityCards)
                card.setCardBackgroundColor(Color.parseColor("#C8E6C9"))
            }
        }
    }

    private fun resetPriorityColors(priorityCards: Map<String, CardView>) {
        for ((_, card) in priorityCards) {
            card.setCardBackgroundColor(Color.WHITE)
        }
    }

    private fun setupSubmitButton() {
        btnAjukan.setOnClickListener {
            val nominalText = inputNominal.text.toString().trim()
            val alasanText = inputAlasan.text.toString().trim()

            // ✅ Validasi input kosong
            if (nominalText.isEmpty() || alasanText.isEmpty() || selectedPriority == null) {
                showDialog("Data Belum Lengkap", "Lengkapi semua data dulu ya sebelum mengajukan.")
                return@setOnClickListener
            }

            val nominal = nominalText.toLongOrNull()
            if (nominal == null || nominal <= 0) {
                showDialog("Nominal Tidak Valid", "Masukkan nominal yang benar (lebih dari 0).")
                return@setOnClickListener
            }

            // ✅ Cek saldo cukup
            if (nominal > saldoAnak) {
                showDialog(
                    "Saldo Tidak Cukup",
                    "Saldo kamu saat ini ${formatRupiah(saldoAnak)}, sedangkan pengajuan kamu sebesar ${formatRupiah(nominal)}.\n\nTurunkan jumlah pengajuan ya."
                )
                return@setOnClickListener
            }

            val user = auth.currentUser
            val childId = user?.uid
            val childName = user?.displayName ?: "Anak"

            val pengajuan = hashMapOf(
                "nominal" to nominal,
                "amount" to nominal,
                "alasan" to alasanText,
                "purpose" to alasanText,
                "prioritas" to selectedPriority,
                "status" to "pending",
                "tanggal" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "anak" to childName,
                "childName" to childName,
                "childId" to childId
            )

            db.collection("pengajuan_dana")
                .add(pengajuan)
                .addOnSuccessListener {
                    showDialog(
                        "Berhasil Diajukan 🎉",
                        "Pengajuan kamu berhasil dikirim dan sedang menunggu persetujuan orang tua."
                    )
                    inputNominal.setText("")
                    inputAlasan.setText("")
                }
                .addOnFailureListener {
                    showDialog("Gagal", "Gagal mengirim pengajuan. Coba lagi nanti.")
                }
        }
    }

    // 🔹 Helper dialog
    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Tutup", null)
            .show()
    }

    // 🔹 Format uang ke Rupiah
    private fun formatRupiah(amount: Long): String {
        val nf = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        return nf.format(amount).replace("Rp", "Rp ").trim()
    }
}