package com.example.monegoal.ui.anak

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.example.monegoal.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AjukanDanaFragment : Fragment() {

    private lateinit var inputNominal: EditText
    private lateinit var inputAlasan: EditText
    private lateinit var btnAjukan: LinearLayout

    private lateinit var db: FirebaseFirestore
    private val auth = FirebaseAuth.getInstance()

    private var selectedPriority: String? = null
    private var selectedAmount: Int? = null

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

        return view
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
            val nominalText = inputNominal.text.toString()
            val alasanText = inputAlasan.text.toString()

            if (nominalText.isEmpty() || alasanText.isEmpty() || selectedPriority == null) {
                Toast.makeText(requireContext(), "Lengkapi semua data dulu ya!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = auth.currentUser
            val childId = user?.uid
            val childName = user?.displayName ?: inputAlasan // fallback - idealnya ambil nama dari profile

            // simpan dengan field yang konsisten / lengkap agar parent bisa menemukan dokumen
            val pengajuan = hashMapOf(
                "nominal" to nominalText.toInt(),
                "amount" to nominalText.toInt(),                // beberapa kode memakai "amount"
                "alasan" to alasanText,
                "purpose" to alasanText,
                "prioritas" to selectedPriority,
                "status" to "pending",                           // gunakan status standar (pending)
                "tanggal" to FieldValue.serverTimestamp(),       // gunakan serverTimestamp
                "createdAt" to FieldValue.serverTimestamp(),
                "anak" to childName,
                "childName" to childName,
                "childId" to childId
            )

            // tulis ke collection 'pengajuan_dana' (kamu bisa juga duplikat ke 'submissions' jika perlu)
            db.collection("pengajuan_dana")
                .add(pengajuan)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Pengajuan berhasil dikirim!", Toast.LENGTH_SHORT).show()
                    inputNominal.setText("")
                    inputAlasan.setText("")
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Gagal mengirim pengajuan 😢", Toast.LENGTH_SHORT).show()
                }
        }
    }
}