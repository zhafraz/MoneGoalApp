package com.example.monegoal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.camera.core.ImageProxy
import androidx.camera.core.ExperimentalGetImage
import org.json.JSONObject
import java.util.concurrent.Executors

class QrScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnBack: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var tvInstruction: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var isProcessing = false

    private val db = FirebaseFirestore.getInstance()
    private var saldoUser: Long = 0L

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
            // Toggle torch if available
            camera?.cameraControl?.enableTorch(true)
            // Simple toggle example: enable once then disable after 1s (atau implement toggle state)
            previewView.postDelayed({ camera?.cameraControl?.enableTorch(false) }, 1500)
        }

        checkCameraPermission()
        loadSaldoUser()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun loadSaldoUser() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                saldoUser = doc.getLong("balance") ?: doc.getLong("saldo") ?: 0L
                Log.d("QR", "Saldo user = $saldoUser")
            }
    }

    @OptIn(ExperimentalGetImage::class)
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

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                if (isProcessing) {
                    // sedang memproses hasil sebelumnya, lepas frame ini
                    imageProxy.close()
                    return@setAnalyzer
                }

                try {
                    // Buat InputImage dari mediaImage
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

                    // jalankan pemindaian barcode
                    scanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            if (barcodes.isNotEmpty()) {
                                val raw = barcodes[0].rawValue
                                if (!raw.isNullOrEmpty()) {
                                    isProcessing = true
                                    handleScannedJson(raw)
                                }
                            }
                        }
                        .addOnFailureListener { ex ->
                            // bisa log jika perlu
                            Log.w("QR", "Scan failure: ${ex.message}")
                        }
                        .addOnCompleteListener {
                            // pastikan selalu menutup imageProxy agar pipeline CameraX tidak hang
                            imageProxy.close()
                        }
                } catch (e: Exception) {
                    // Jika ada exception saat konversi/process, pastikan imageProxy di-close
                    imageProxy.close()
                }
            }

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, preview, imageAnalysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal memulai kamera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleScannedJson(raw: String) {
        // raw biasanya JSON sesuai generator QR kamu
        try {
            val obj = JSONObject(raw)
            val namaToko = obj.optString("nama_toko", "Toko")
            val namaBarang = obj.optString("nama_barang", "Barang")
            val harga = obj.optLong("harga", -1L)

            if (harga <= 0L) {
                runOnUiThread {
                    tvInstruction.text = "QR tidak berisi harga valid"
                }
                isProcessing = false
                return
            }

            if (saldoUser < harga) {
                runOnUiThread {
                    Toast.makeText(this, "Saldo tidak mencukupi untuk Rp ${String.format("%,d", harga)}", Toast.LENGTH_LONG).show()
                    tvInstruction.text = "Saldo tidak cukup"
                }
                isProcessing = false
                return
            }

            // lanjutkan proses pembayaran
            performPayment(namaToko, namaBarang, harga)
        } catch (e: Exception) {
            runOnUiThread {
                tvInstruction.text = "QR tidak valid"
            }
            isProcessing = false
        }
    }

    private fun performPayment(toko: String, barang: String, jumlah: Long) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            isProcessing = false
            return
        }

        val newSaldo = (saldoUser - jumlah)
        val tx = hashMapOf(
            "type" to "pengeluaran",
            "toko" to toko,
            "barang" to barang,
            "amount" to jumlah,
            "createdAt" to System.currentTimeMillis()
        )

        // lakukan update saldo dan pencatatan transaksi
        db.collection("users").document(userId)
            .update("balance", newSaldo) // atau field "saldo" sesuai strukturmu
            .addOnSuccessListener {
                // catat transaksi
                db.collection("users").document(userId)
                    .collection("transactions")
                    .add(tx)
                    .addOnSuccessListener {
                        saldoUser = newSaldo
                        runOnUiThread {
                            Toast.makeText(this, "Pembayaran Rp ${String.format("%,d", jumlah)} berhasil", Toast.LENGTH_LONG).show()
                            tvInstruction.text = "Pembayaran berhasil ✅"
                        }
                    }
                    .addOnFailureListener {
                        runOnUiThread {
                            Toast.makeText(this, "Pembayaran berhasil, tapi gagal simpan transaksi", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    Toast.makeText(this, "Gagal mengupdate saldo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnCompleteListener {
                // setelah semua, boleh deteksi lagi
                isProcessing = false
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}