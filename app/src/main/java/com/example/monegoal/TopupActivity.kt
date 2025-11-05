package com.example.monegoal

import android.app.AlertDialog
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
    private var currentSaldoChild: Long = 0L

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Bisa diberikan via intent (dari HomeOrtuActivity)
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

        // selalu muat saldo orang tua (dipakai untuk validasi topup)
        loadSaldoOrtu()

        // Jika childId tidak ada — ambil daftar anak terhubung dan minta pilihan.
        if (childId.isNullOrBlank()) {
            fetchChildrenForParent { children ->
                when {
                    children.isEmpty() -> {
                        Toast.makeText(this, "Tidak ada akun anak terhubung. Pilih akun anak lewat profil.", Toast.LENGTH_LONG).show()
                        // jika tidak ada anak, biarkan tvUserNameTopup menampilkan nama orang tua (sudah di-set oleh loadSaldoOrtu)
                    }
                    children.size == 1 -> {
                        val (id, name) = children[0]
                        childId = id
                        childName = name
                        tvUserNameTopup.text = "Top-up: ${childName ?: "Anak"}"
                        loadChildBalance(childId!!)
                    }
                    else -> {
                        // minta pilih anak lewat dialog
                        val names = children.map { it.second.ifBlank { it.first } }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Pilih anak untuk top-up")
                            .setItems(names) { _, which ->
                                val (id, name) = children[which]
                                childId = id
                                childName = name
                                tvUserNameTopup.text = "Top-up: ${childName ?: "Anak"}"
                                loadChildBalance(childId!!)
                            }
                            .setCancelable(false)
                            .show()
                    }
                }
            }
        } else {
            // sudah ada childId dari intent
            tvUserNameTopup.text = "Top-up: ${childName ?: "Anak"}"
            loadChildBalance(childId!!)
        }
    }

    private fun initViews() {
        tvNominalDisplay = findViewById(R.id.tvNominalDisplay)
        inputCustomNominal = findViewById(R.id.inputCustomNominal)
        tvUserNameTopup = findViewById(R.id.tvUserNameTopup)
        btnLanjut = findViewById(R.id.btnLanjut)
        tvSaldo = findViewById(R.id.tvSaldo)
    }

    /**
     * Muat saldo orang tua (dipakai untuk validasi bahwa orang tua punya cukup uang).
     * Tidak mengubah tampilan saldo anak bila child dipilih — gunakan loadChildBalance untuk itu.
     */
    private fun loadSaldoOrtu() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val namaOrtu = doc.getString("name") ?: "Orang Tua"
                    val saldoOrtu = doc.getLong("saldoOrtu")
                        ?: doc.getLong("saldo")
                        ?: doc.getLong("balance")
                        ?: 0L

                    // jika belum ada child yang dipilih, tampilkan nama orang tua
                    if (childName.isNullOrBlank()) {
                        tvUserNameTopup.text = namaOrtu
                    }
                    currentSaldoOrtu = saldoOrtu

                    // only update display if no child selected — otherwise child saldo has priority
                    if (childId.isNullOrBlank()) {
                        updateSaldoText()
                    }
                } else {
                    if (childName.isNullOrBlank()) tvUserNameTopup.text = "Orang Tua"
                    currentSaldoOrtu = 0L
                    if (childId.isNullOrBlank()) updateSaldoText()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data pengguna", Toast.LENGTH_SHORT).show()
                if (childName.isNullOrBlank()) tvUserNameTopup.text = "Orang Tua"
            }
    }

    /**
     * Muat saldo anak tertentu dan tampilkan di tvSaldo.
     * Jika dokumen anak tidak ditemukan, fallback menampilkan saldo orang tua (jika ada).
     */
    private fun loadChildBalance(childUid: String) {
        db.collection("users").document(childUid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val name = doc.getString("name") ?: "Anak"
                    childName = name
                    // simpan saldo anak
                    currentSaldoChild = doc.getLong("saldoAnak")
                        ?: doc.getLong("saldo")
                                ?: doc.getLong("balance")
                                ?: 0L

                    // tampilkan label yang jelas: Saldo Anak
                    tvSaldo.text = "Saldo anak: ${formatCurrency(currentSaldoChild)}"
                    tvUserNameTopup.text = "Top-up: ${childName ?: "Anak"}"
                } else {
                    // fallback
                    currentSaldoChild = 0L
                    // jika parent data tersedia, tampilkan parent saldo
                    if (currentSaldoOrtu > 0L) {
                        updateSaldoText()
                    } else {
                        tvSaldo.text = "Saldo: ${formatCurrency(0L)}"
                    }
                }
            }
            .addOnFailureListener {
                currentSaldoChild = 0L
                if (currentSaldoOrtu > 0L) updateSaldoText() else tvSaldo.text = "Saldo: ${formatCurrency(0L)}"
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
                Toast.makeText(this, "Pilih anak terlebih dahulu.", Toast.LENGTH_LONG).show()
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
     * - catat transaksi di masing2 subcollection transactions (setelah runTransaction sukses)
     */
    private fun prosesTopupAtomic(amount: Long, childUid: String) {
        val parentUid = auth.currentUser?.uid ?: return
        val parentRef = db.collection("users").document(parentUid)
        val childRef = db.collection("users").document(childUid)

        db.runTransaction { tx ->
            // baca kedua dokumen dulu (Firestore transaction require reads sebelum updates)
            val parentSnap = tx.get(parentRef)
            val childSnap = tx.get(childRef)

            val parentSaldoOrtu = parentSnap.getLong("saldoOrtu")
                ?: parentSnap.getLong("saldo")
                ?: parentSnap.getLong("balance")
                ?: 0L

            if (parentSaldoOrtu < amount) {
                throw Exception("Saldo ortu tidak mencukupi")
            }

            val childSaldoAnak = childSnap.getLong("saldoAnak")
                ?: childSnap.getLong("saldo")
                ?: childSnap.getLong("balance")
                ?: 0L

            // lakukan update di dalam transaksi
            tx.update(parentRef, "saldoOrtu", parentSaldoOrtu - amount)
            // update legacy field 'saldo' jika ada (agar kompatibel)
            val parentLegacySaldo = parentSnap.getLong("saldo") ?: parentSaldoOrtu
            tx.update(parentRef, "saldo", parentLegacySaldo - amount)

            tx.update(childRef, "saldoAnak", childSaldoAnak + amount)
            val childLegacySaldo = childSnap.getLong("saldo") ?: childSaldoAnak
            tx.update(childRef, "saldo", childLegacySaldo + amount)

            // return null (nilai tidak dipakai)
            null
        }.addOnSuccessListener {
            // tambah transaksi non-transactional (best-effort)
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
                    // jika tidak ada child dipilih, tampilkan parent saldo; kalau child ada, muat ulang anak
                    if (childId.isNullOrBlank()) updateSaldoText()
                    else loadChildBalance(childId!!)
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

    /**
     * Update tampilan tvSaldo.
     * Jika childId ada -> tampilkan saldo anak. Jika tidak -> tampilkan saldo orang tua.
     */
    private fun updateSaldoText() {
        if (!childId.isNullOrBlank()) {
            // tampilkan saldo child (jika sudah dimuat), jika belum dimuat tampilkan "memuat..."
            if (currentSaldoChild >= 0L) {
                tvSaldo.text = "Saldo anak: ${formatCurrency(currentSaldoChild)}"
            } else {
                tvSaldo.text = "Saldo anak: Memuat..."
            }
        } else {
            tvSaldo.text = "Saldo: ${formatCurrency(currentSaldoOrtu)}"
        }
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
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                // selesai, kembalikan RESULT_OK jika dipanggil dari parent activity
                setResult(RESULT_OK)
                finish()
            }
            .show()
    }

    /**
     * Cari anak-anak yang terhubung dengan parent saat ini.
     * Mengembalikan list Pair(childId, childName).
     */
    private fun fetchChildrenForParent(callback: (List<Pair<String, String>>) -> Unit) {
        val parentEmail = auth.currentUser?.email
        val parentUid = auth.currentUser?.uid

        if (parentEmail.isNullOrBlank() && parentUid.isNullOrBlank()) {
            callback(emptyList())
            return
        }

        val candidateParents = mutableListOf<String>()
        parentUid?.let { candidateParents.add(it) }
        parentEmail?.let {
            // encoding yang dipakai di register: replace(".", "_").replace("@","_at_")
            candidateParents.add(it.replace(".", "_").replace("@", "_at_"))
            candidateParents.add(it)
            // legacy style
            candidateParents.add(it.replace("@", "_at_").replace(".", "_dot_"))
        }

        val toTry = candidateParents.filter { it.isNotBlank() }.distinct()
        if (toTry.isEmpty()) {
            callback(emptyList())
            return
        }

        val collected = mutableMapOf<String, String>() // id -> name
        var remaining = toTry.size

        for (p in toTry) {
            db.collection("users")
                .whereArrayContains("parents", p)
                .get()
                .addOnSuccessListener { snap ->
                    for (d in snap.documents) {
                        val name = d.getString("name") ?: d.id
                        collected[d.id] = name
                    }
                    remaining--
                    if (remaining <= 0) {
                        callback(collected.map { it.key to it.value })
                    }
                }
                .addOnFailureListener {
                    remaining--
                    if (remaining <= 0) {
                        callback(collected.map { it.key to it.value })
                    }
                }
        }
    }

    private fun formatCurrency(amount: Long): String {
        val formatted = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(amount)
        return formatted.replace("Rp", "Rp ").trim()
    }
}