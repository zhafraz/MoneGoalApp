package com.example.monegoal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import org.json.JSONObject
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

@OptIn(ExperimentalGetImage::class)
class ScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnBack: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var tvInstruction: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null

    // flag untuk mencegah multi-trigger
    private var isProcessing = false

    private val db = FirebaseFirestore.getInstance()

    // cache optional (untuk UI); pengecekan kritis memakai direct .get()
    private var cachedSaldoAnak: Long = 0L
    private var cachedApprovedLimit: Long = 0L

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        previewView = findViewById(R.id.cameraPreview)
        btnBack = findViewById(R.id.btnBack)
        btnFlash = findViewById(R.id.btnFlash)
        tvInstruction = findViewById(R.id.tvInstruction)

        btnBack.setOnClickListener { finish() }
        btnFlash.setOnClickListener {
            camera?.cameraControl?.enableTorch(true)
            previewView.postDelayed({ camera?.cameraControl?.enableTorch(false) }, 1500)
        }

        checkCameraPermission()
        loadCache() // isi cache opsional
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * Isi cache awal (opsional) agar UI bisa tunjukkan nilai; pengecekan kritis tetap pakai .get() real-time.
     */
    private fun loadCache() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                cachedSaldoAnak = doc.getLong("saldoAnak") ?: doc.getLong("balance") ?: doc.getLong("saldo") ?: 0L
                cachedApprovedLimit = when (val v = doc.get("approvedLimit")) {
                    is Number -> v.toLong()
                    is String -> v.toString().toLongOrNull() ?: 0L
                    else -> 0L
                }
                Log.d("QR", "cache loaded: saldoAnak=$cachedSaldoAnak, approvedLimit=$cachedApprovedLimit")
            }
            .addOnFailureListener {
                cachedSaldoAnak = 0L
                cachedApprovedLimit = 0L
            }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val scanner = BarcodeScanning.getClient()

            @OptIn(ExperimentalGetImage::class)
            val analyzer = ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@Analyzer
                }

                if (isProcessing) {
                    imageProxy.close()
                    return@Analyzer
                }

                try {
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

                    scanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            if (barcodes.isNotEmpty()) {
                                val raw = barcodes[0].rawValue
                                if (!raw.isNullOrEmpty()) {
                                    isProcessing = true
                                    handleScannedJsonWithConfirmation(raw)
                                }
                            }
                        }
                        .addOnFailureListener { ex ->
                            Log.w("QR", "Scan failure: ${ex.message}")
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } catch (e: Exception) {
                    imageProxy.close()
                    Log.w("QR", "Exception processing image: ${e.message}")
                }
            }

            imageAnalysis.setAnalyzer(executor, analyzer)

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, preview, imageAnalysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal memulai kamera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Ambil data user langsung saat scan agar tidak ada race.
     * **Aturan baru:** pembayaran hanya boleh jika approvedLimit >= harga.
     * Jika approvedLimit mencukupi -> commit transaksi: kurangi approvedLimit dan kurangi saldoAnak sebesar jumlah.
     */
    private fun handleScannedJsonWithConfirmation(raw: String) {
        try {
            val obj = JSONObject(raw)
            val namaToko = obj.optString("nama_toko", "Toko")
            val namaBarang = obj.optString("nama_barang", "Barang")
            val harga = obj.optLong("harga", -1L)

            if (harga <= 0L) {
                runOnUiThread { tvInstruction.text = "QR tidak berisi harga valid" }
                isProcessing = false
                return
            }

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                runOnUiThread {
                    Toast.makeText(this, "User tidak terdeteksi", Toast.LENGTH_SHORT).show()
                    tvInstruction.text = "User tidak terdeteksi"
                }
                isProcessing = false
                return
            }

            // baca user doc real-time sebelum menampilkan konfirmasi
            db.collection("users").document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    val curSaldoAnak = doc.getLong("saldoAnak") ?: doc.getLong("balance") ?: doc.getLong("saldo") ?: 0L
                    val curApproved = when (val v = doc.get("approvedLimit")) {
                        is Number -> v.toLong()
                        is String -> v.toString().toLongOrNull() ?: 0L
                        else -> 0L
                    }

                    // Update cache optional
                    cachedSaldoAnak = curSaldoAnak
                    cachedApprovedLimit = curApproved

                    Log.d("QR", "fresh read before confirm: saldoAnak=$curSaldoAnak, approved=$curApproved, harga=$harga")

                    // RULE: require approvedLimit >= harga
                    if (curApproved < harga) {
                        runOnUiThread {
                            Toast.makeText(this, "Izin (approvedLimit) tidak mencukupi untuk Rp ${String.format("%,d", harga)}", Toast.LENGTH_LONG).show()
                            tvInstruction.text = "Izin tidak cukup"
                        }
                        isProcessing = false
                        return@addOnSuccessListener
                    }

                    // Also ensure saldoAnak (actual funds) >= harga (so there's real money to charge)
                    if (curSaldoAnak < harga) {
                        runOnUiThread {
                            Toast.makeText(this, "Saldo anak tidak mencukupi untuk Rp ${String.format("%,d", harga)}", Toast.LENGTH_LONG).show()
                            tvInstruction.text = "Saldo tidak cukup"
                        }
                        isProcessing = false
                        return@addOnSuccessListener
                    }

                    // tampilkan dialog konfirmasi
                    runOnUiThread {
                        val formatted = "Rp ${String.format("%,d", harga)}"
                        val message = "Bayar $formatted ke:\n\n$namaToko\nItem: $namaBarang\n\nLanjutkan pembayaran?"

                        AlertDialog.Builder(this)
                            .setTitle("Konfirmasi Pembayaran")
                            .setMessage(message)
                            .setPositiveButton("Bayar") { dialog, _ ->
                                dialog.dismiss()
                                performPayment(namaToko, namaBarang, harga)
                            }
                            .setNegativeButton("Batal") { dialog, _ ->
                                dialog.dismiss()
                                Handler(Looper.getMainLooper()).postDelayed({
                                    isProcessing = false
                                }, 700)
                            }
                            .setOnCancelListener {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    isProcessing = false
                                }, 700)
                            }
                            .show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.w("QR", "Gagal baca user doc: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this, "Gagal memeriksa data user: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    isProcessing = false
                }
        } catch (e: Exception) {
            runOnUiThread {
                tvInstruction.text = "QR tidak valid"
            }
            isProcessing = false
        }
    }

    /**
     * Commit pembayaran di dalam transaction.
     * - Require approvedLimit >= jumlah (double-check)
     * - Require saldoAnak >= jumlah (double-check)
     * - Kurangi approvedLimit dengan jumlah
     * - Kurangi saldoAnak (atau balance/saldo) dengan jumlah
     * - Tulis transaksi audit
     */
    private fun performPayment(toko: String, barang: String, jumlah: Long) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            isProcessing = false
            return
        }
        val userRef = db.collection("users").document(uid)

        db.runTransaction { tr ->
            val userDoc = tr.get(userRef)

            val curSaldoAnak = userDoc.getLong("saldoAnak") ?: userDoc.getLong("balance") ?: userDoc.getLong("saldo") ?: 0L
            val curApproved = when (val v = userDoc.get("approvedLimit")) {
                is Number -> v.toLong()
                is String -> v.toString().toLongOrNull() ?: 0L
                else -> 0L
            }

            Log.d("QR", "inside transaction: curSaldoAnak=$curSaldoAnak, curApproved=$curApproved, jumlah=$jumlah")

            if (curApproved < jumlah) {
                throw Exception("Izin (approvedLimit) tidak mencukupi saat commit")
            }
            if (curSaldoAnak < jumlah) {
                throw Exception("Saldo anak tidak mencukupi saat commit")
            }

            val newApproved = (curApproved - jumlah).coerceAtLeast(0L)
            val newSaldoAnak = (curSaldoAnak - jumlah).coerceAtLeast(0L)

            // update approvedLimit
            tr.update(userRef, "approvedLimit", newApproved)

            // update saldoAnak / fallback
            if (userDoc.contains("saldoAnak")) {
                tr.update(userRef, "saldoAnak", newSaldoAnak)
            } else if (userDoc.contains("balance")) {
                tr.update(userRef, "balance", newSaldoAnak)
            } else if (userDoc.contains("saldo")) {
                tr.update(userRef, "saldo", newSaldoAnak)
            } else {
                tr.update(userRef, "saldoAnak", newSaldoAnak)
            }

            // catat transaksi (audit): tunjukkan berapa dikurangi dari approval dan saldo
            val txDoc = userRef.collection("transactions").document()
            val txData = mapOf(
                "type" to "pengeluaran",
                "toko" to toko,
                "barang" to barang,
                "amount" to jumlah,
                // both values same conceptually (approval acted as permission equal to amount consumed)
                "usedFromApproved" to jumlah,
                "usedFromSaldoAnak" to jumlah,
                "createdAt" to FieldValue.serverTimestamp()
            )
            tr.set(txDoc, txData)
        }.addOnSuccessListener {
            // refresh cache
            loadCache()
            runOnUiThread {
                Toast.makeText(this, "Pembayaran Rp ${String.format("%,d", jumlah)} berhasil", Toast.LENGTH_LONG).show()
                tvInstruction.text = "Pembayaran berhasil ✅"
            }
        }.addOnFailureListener { e ->
            runOnUiThread {
                Toast.makeText(this, "Gagal melakukan pembayaran: ${e.message}", Toast.LENGTH_LONG).show()
                tvInstruction.text = "Pembayaran gagal"
            }
        }.addOnCompleteListener {
            Handler(Looper.getMainLooper()).postDelayed({
                isProcessing = false
            }, 700)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}