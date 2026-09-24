package com.stegatext.app

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private var pickTarget = 0
    private var filePayload: Payload.File? = null
    private var extracted: Revealed? = null
    private var pendingSave: Pair<String, ByteArray>? = null

    private lateinit var etCarrier: TextInputEditText
    private lateinit var etSecret: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etCarrierX: TextInputEditText
    private lateinit var etPasswordX: TextInputEditText
    private lateinit var tvFileName: TextView
    private lateinit var tvCapacity: TextView
    private lateinit var tvOutput: TextView
    private lateinit var tvResultX: TextView
    private lateinit var switchRobust: MaterialSwitch

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onFilePicked(uri)
    }

    private val saver = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) writePending(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa"))
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etCarrier = findViewById(R.id.etCarrier)
        etSecret = findViewById(R.id.etSecret)
        etPassword = findViewById(R.id.etPassword)
        etCarrierX = findViewById(R.id.etCarrierX)
        etPasswordX = findViewById(R.id.etPasswordX)
        tvFileName = findViewById(R.id.tvFileName)
        tvCapacity = findViewById(R.id.tvCapacity)
        tvOutput = findViewById(R.id.tvOutput)
        tvResultX = findViewById(R.id.tvResultX)
        switchRobust = findViewById(R.id.switchRobust)

        val modeGroup = findViewById<MaterialButtonToggleGroup>(R.id.modeGroup)
        modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val hide = checkedId == R.id.btnModeHide
            findViewById<View>(R.id.hideSection).visibility = if (hide) View.VISIBLE else View.GONE
            findViewById<View>(R.id.revealSection).visibility = if (hide) View.GONE else View.VISIBLE
        }
        modeGroup.check(R.id.btnModeHide)

        findViewById<TextView>(R.id.btnLang).setOnClickListener {
            val cur = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(if (cur.startsWith("fa")) "en" else "fa")
            )
        }

        findViewById<View>(R.id.radioFile).setOnClickListener { onPayloadTypeChanged() }
        findViewById<View>(R.id.radioText).setOnClickListener { onPayloadTypeChanged() }

        findViewById<View>(R.id.btnSample).setOnClickListener {
            etCarrier.setText(getString(R.string.sample_story))
            etCarrier.setSelection(etCarrier.text?.length ?: 0)
            updateCapacity()
        }

        findViewById<View>(R.id.btnPickFile).setOnClickListener {
            pickTarget = 2
            filePicker.launch(arrayOf("*/*"))
        }

        btnHide0().setOnClickListener { doHide() }
        btnReveal0().setOnClickListener { doReveal() }

        findViewById<View>(R.id.btnCopy).setOnClickListener { copyToClipboard(tvOutput.text.toString()) }
        findViewById<View>(R.id.btnShare).setOnClickListener { shareText(tvOutput.text.toString()) }
        findViewById<View>(R.id.btnSave).setOnClickListener {
            requestSave("stegatext.txt", tvOutput.text.toString().toByteArray(Charsets.UTF_8))
        }
        findViewById<View>(R.id.btnSaveX).setOnClickListener {
            val e = extracted
            if (e != null) requestSave(e.name ?: "secret.bin", e.bytes)
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { updateCapacity() }
        }
        etCarrier.addTextChangedListener(watcher)
        etSecret.addTextChangedListener(watcher)
        etCarrierX.addTextChangedListener(watcher)
    }

    private fun btnHide0(): View = findViewById(R.id.btnHide)
    private fun btnReveal0(): View = findViewById(R.id.btnReveal)

    private fun onPayloadTypeChanged() {
        val isFile = findViewById<RadioGroup>(R.id.payloadGroup).checkedRadioButtonId == R.id.radioFile
        findViewById<View>(R.id.etSecret).visibility = if (isFile) View.GONE else View.VISIBLE
            val parent = findViewById<View>(R.id.etSecret).parent as? View
            if (parent != null) parent.visibility = if (isFile) View.GONE else View.VISIBLE
        findViewById<View>(R.id.fileRow).visibility = if (isFile) View.VISIBLE else View.GONE
        if (!isFile) filePayload = null
        updateCapacity()
    }

    private fun onFilePicked(uri: Uri) {
        if (pickTarget == 2) {
            try {
                val name = queryDisplayName(uri) ?: "file.bin"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: run { toast(getString(R.string.err_file_read)); return }
                if (bytes.size > 8_000_000) { toast(getString(R.string.err_file_big)); return }
                filePayload = Payload.File(name, bytes)
                tvFileName.text = "$name (${bytes.size} B)"
                updateCapacity()
            } catch (e: Exception) {
                toast(getString(R.string.err_file_read))
            }
        } else {
            try {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: return
                etCarrier.setText(text)
                updateCapacity()
            } catch (e: Exception) {
                toast(getString(R.string.err_file_read))
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && !c.isNull(i)) return c.getString(i)
            }
        }
        return null
    }

    private fun updateCapacity() {
        val carrier = etCarrier.text?.toString() ?: ""
        val capBits = StegoEngine.capacityBits(carrier, switchRobust.isChecked)
        val needBits: Int = if (findViewById<RadioGroup>(R.id.payloadGroup).checkedRadioButtonId == R.id.radioFile) {
            val f = filePayload
            if (f == null) 0 else StegoEngine.neededBits(StegoEngine.plainFileSize(f.name.toByteArray(Charsets.UTF_8).size, f.bytes.size))
        } else {
            val secret = etSecret.text?.toString() ?: ""
            if (secret.isEmpty()) 0 else StegoEngine.neededBits(StegoEngine.plainTextSize(secret.toByteArray(Charsets.UTF_8).size))
        }
        tvCapacity.text = formatCapacity(capBits, needBits)
    }

    private fun formatCapacity(capBits: Int, needBits: Int): String {
        if (capBits <= 0) return getString(R.string.capacity_zero)
        val capBytes = capBits / 8
        val need = (needBits + 7) / 8
        return getString(R.string.capacity, capBits.toString(), need.toString())
    }

    private fun doHide() {
        val carrier = etCarrier.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""
        val useFile = findViewById<RadioGroup>(R.id.payloadGroup).checkedRadioButtonId == R.id.radioFile
        if (carrier.isEmpty() || password.isEmpty()) { toast(getString(R.string.empty_warn)); return }
        val payload = if (useFile) {
            val f = filePayload
            if (f == null) { toast(getString(R.string.empty_warn)); return }
            f as Payload
        } else {
            val secret = etSecret.text?.toString() ?: ""
            if (secret.isEmpty()) { toast(getString(R.string.empty_warn)); return }
            Payload.Text(secret) as Payload
        }
        try {
            val stego = StegoEngine.hide(carrier, payload, password.toCharArray(), switchRobust.isChecked)
            tvOutput.text = stego
            findViewById<View>(R.id.outputCard).visibility = View.VISIBLE
            toast(getString(R.string.done_hidden))
        } catch (e: CapacityException) {
            toast(getString(R.string.err_capacity, ((e.neededBits + 7) / 8).toString(), (e.availableBits / 8).toString()))
        } catch (e: Exception) {
            toast(e.message ?: "error")
        }
    }

    private fun doReveal() {
        val stego = etCarrierX.text?.toString() ?: ""
        val password = etPasswordX.text?.toString() ?: ""
        if (stego.isEmpty() || password.isEmpty()) { toast(getString(R.string.empty_warn)); return }
        val r = StegoEngine.reveal(stego, password.toCharArray())
        if (r == null) {
            toast(getString(R.string.err_badkey))
            return
        }
        extracted = r
        if (r.isFile) {
            tvResultX.text = getString(R.string.file_result, r.name ?: "file.bin", r.bytes.size.toString())
            findViewById<View>(R.id.btnSaveX).visibility = View.VISIBLE
        } else {
            tvResultX.text = r.asText()
            findViewById<View>(R.id.btnSaveX).visibility = View.GONE
        }
        findViewById<View>(R.id.resultCard).visibility = View.VISIBLE
    }

    private fun requestSave(suggested: String, bytes: ByteArray) {
        pendingSave = suggested to bytes
        saver.launch(suggested)
    }

    private fun writePending(uri: Uri) {
        val p = pendingSave ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(p.second) }
            toast(getString(R.string.done_saved))
        } catch (e: Exception) {
            toast(e.message ?: "error")
        }
    }

    private fun copyToClipboard(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(ClipDescription.MIMETYPE_TEXT_PLAIN, text)
        )
        toast(getString(R.string.done_copied))
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(intent, getString(R.string.btn_share)))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
