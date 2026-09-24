package com.stegatext.app

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class FileVaultActivity : AppCompatActivity() {

    private var carrier: ByteArray? = null
    private var secret: Payload.File? = null
    private var stego: ByteArray? = null
    private var revealed: Revealed? = null
    private var pickTarget = 0
    private var pendingSave: Pair<String, ByteArray>? = null

    private lateinit var etPassword: TextInputEditText
    private lateinit var tvCarrier: TextView
    private lateinit var tvCapacity: TextView
    private lateinit var tvResult: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            when (pickTarget) {
                1 -> onCarrierPicked(uri)
                3 -> onSecretPicked(uri)
                else -> onStegoPicked(uri)
            }
        }
    }

    private val saver = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val p = pendingSave
        if (uri != null && p != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(p.second) }
                toast(getString(R.string.done_saved))
            } catch (e: Exception) {
                toast(e.message ?: "error")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_vault)

        etPassword = findViewById(R.id.etPasswordV)
        tvCarrier = findViewById(R.id.tvCarrierName)
        tvCapacity = findViewById(R.id.tvVaultCapacity)
        tvResult = findViewById(R.id.tvVaultResult)

        findViewById<View>(R.id.btnPickCarrier).setOnClickListener {
            pickTarget = 1
            picker.launch(arrayOf("*/*"))
        }
        findViewById<View>(R.id.btnPickSecret).setOnClickListener {
            pickTarget = 3
            picker.launch(arrayOf("*/*"))
        }
        findViewById<View>(R.id.btnVaultHide).setOnClickListener { hide() }
        findViewById<View>(R.id.btnVaultReveal).setOnClickListener { reveal() }
        findViewById<View>(R.id.btnVaultPickStego).setOnClickListener {
            pickTarget = 4
            picker.launch(arrayOf("*/*"))
        }
        findViewById<View>(R.id.btnVaultSave).setOnClickListener {
            val s = stego
            if (s != null) saver.launch("stegatext.bin")
        }
        findViewById<View>(R.id.btnVaultSaveRevealed).setOnClickListener {
            val r = revealed
            if (r != null) saver.launch(r.name ?: "file.bin")
        }
    }

    private fun onCarrierPicked(uri: Uri) {
        try {
            carrier = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            tvCarrier.text = displayName(uri) + " (" + (carrier?.size ?: 0) + " B)"
            updateCapacity()
        } catch (e: Exception) {
            toast(getString(R.string.err_file_read))
        }
    }

    private fun onSecretPicked(uri: Uri) {
        try {
            val name = displayName(uri)
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                secret = Payload.File(name, bytes)
                (findViewById<View>(R.id.tvSecretName) as TextView).text = "$name (${bytes.size} B)"
                updateCapacity()
            }
        } catch (e: Exception) {
            toast(getString(R.string.err_file_read))
        }
    }

    private fun onStegoPicked(uri: Uri) {
        try {
            stego = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            updateCapacity()
        } catch (e: Exception) {
            toast(getString(R.string.err_file_read))
        }
    }

    private fun displayName(uri: Uri): String {
        var name = "file.bin"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && !c.isNull(i)) name = c.getString(i)
            }
        }
        return name
    }

    private fun nameOf(uri: Uri): String = displayName(uri)

    private fun hide() {
        val c = carrier
        val s = secret
        val pw = etPassword.text?.toString() ?: ""
        if (c == null || s == null || pw.isEmpty()) { toast(getString(R.string.empty_warn)); return }
        try {
            val out = FileStegoEngine.hide(c, s, pw.toCharArray())
            stego = out
            findViewById<View>(R.id.vaultStegoCard).visibility = View.VISIBLE
            toast(getString(R.string.vault_done, out.size.toString()))
        } catch (e: CapacityException) {
            toast(getString(R.string.err_capacity, e.neededBits.toString(), e.availableBits.toString()))
        } catch (e: Exception) {
            toast(e.message ?: "error")
        }
    }

    private fun reveal() {
        val s = stego
        val pw = etPassword.text?.toString() ?: ""
        if (s == null || pw.isEmpty()) { toast(getString(R.string.empty_warn)); return }
        val r = FileStegoEngine.reveal(s, pw.toCharArray())
        if (r == null) {
            toast(getString(R.string.err_badkey))
            return
        }
        revealed = r
        if (r.isFile) {
            tvResult.text = getString(R.string.file_result, r.name ?: "file.bin", r.bytes.size.toString())
            findViewById<View>(R.id.btnVaultSaveRevealed).visibility = View.VISIBLE
        } else {
            tvResult.text = r.asText()
            findViewById<View>(R.id.btnVaultSaveRevealed).visibility = View.GONE
        }
        findViewById<View>(R.id.vaultResultCard).visibility = View.VISIBLE
    }

    private fun updateCapacity() {
        val c = carrier
        if (c == null) {
            tvCapacity.text = getString(R.string.capacity_zero)
            return
        }
        tvCapacity.text = getString(R.string.vault_capacity_txt, c.size.toString(), FileStegoEngine.capacity(c).toString())
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}