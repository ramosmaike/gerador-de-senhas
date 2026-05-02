package com.example.geradordesenhas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: PasswordAdapter
    private lateinit var tvPassword: TextView
    private lateinit var etName: EditText
    private lateinit var etLength: EditText
    private lateinit var cbUppercase: CheckBox
    private lateinit var cbLowercase: CheckBox
    private lateinit var cbNumbers: CheckBox
    private lateinit var cbSpecial: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dbHelper = DatabaseHelper(this)

        tvPassword = findViewById(R.id.tv_password)
        etName = findViewById(R.id.et_name)
        etLength = findViewById(R.id.et_length)
        cbUppercase = findViewById(R.id.cb_uppercase)
        cbLowercase = findViewById(R.id.cb_lowercase)
        cbNumbers = findViewById(R.id.cb_numbers)
        cbSpecial = findViewById(R.id.cb_special)
        val btnGenerate = findViewById<Button>(R.id.btn_generate)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val rvPasswords = findViewById<RecyclerView>(R.id.rv_passwords)

        // Hide UI until authenticated
        findViewById<View>(R.id.main).visibility = View.GONE
        checkBiometricSupportAndAuthenticate()

        setupRecyclerView(rvPasswords)

        val ivLogo = findViewById<ImageView>(R.id.iv_logo)
        ivLogo.setOnClickListener {
            showDeveloperInfoDialog()
        }

        btnGenerate.setOnClickListener {
            val lengthStr = etLength.text.toString()
            if (lengthStr.isNotEmpty()) {
                val length = lengthStr.toIntOrNull() ?: 12
                if (length in 1..100) {
                    val password = generatePassword(
                        length,
                        cbUppercase.isChecked,
                        cbLowercase.isChecked,
                        cbNumbers.isChecked,
                        cbSpecial.isChecked
                    )
                    if (password.isNotEmpty()) {
                        tvPassword.text = password
                    } else {
                        Toast.makeText(this, "Selecione pelo menos uma opção", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.msg_invalid_length), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.msg_empty_length), Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString()
            val password = tvPassword.text.toString()

            if (name.isNotEmpty() && password.isNotEmpty() && password != getString(R.string.placeholder_password)) {
                dbHelper.addPassword(name, password)
                updateList()
                etName.text.clear()
                Toast.makeText(this, "Salvo com sucesso!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Preencha o nome e gere uma senha", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        adapter = PasswordAdapter(
            emptyList(),
            onEditClick = { entry -> showEditDialog(entry) },
            onDeleteClick = { entry -> 
                dbHelper.deletePassword(entry.id)
                updateList()
            },
            onCopyClick = { password -> copyToClipboard(password) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        updateList()
    }

    private fun updateList() {
        val passwords = dbHelper.getAllPasswords()
        adapter.updateData(passwords)
    }

    private fun copyToClipboard(password: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.clip_label), password)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.msg_copied), Toast.LENGTH_SHORT).show()
    }

    private fun showEditDialog(entry: PasswordEntry) {
        val builder = AlertDialog.Builder(this)
        
        val editName = EditText(this)
        editName.hint = getString(R.string.item_name_placeholder)
        editName.setText(entry.name)
        
        val editPassword = EditText(this)
        editPassword.hint = getString(R.string.item_password_placeholder)
        editPassword.setText(entry.password)
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 40, 60, 10)
        layout.addView(editName)
        layout.addView(editPassword)
        
        builder.setTitle(getString(R.string.label_edit))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val newName = editName.text.toString()
                val newPassword = editPassword.text.toString()
                if (newName.isNotEmpty() && newPassword.isNotEmpty()) {
                    dbHelper.updatePassword(entry.id, newName, newPassword)
                    updateList()
                }
            }
            .setNegativeButton(getString(R.string.dev_close), null)
            .show()
    }

    private fun generatePassword(
        length: Int,
        upper: Boolean,
        lower: Boolean,
        numbers: Boolean,
        special: Boolean
    ): String {
        val charset = StringBuilder()
        if (upper) charset.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        if (lower) charset.append("abcdefghijklmnopqrstuvwxyz")
        if (numbers) charset.append("0123456789")
        if (special) charset.append("!@#$%^&*()-_=+[]{}|;:,.<>?")

        if (charset.isEmpty()) return ""

        return (1..length)
            .map { charset[Random.nextInt(charset.length)] }
            .joinToString("")
    }

    private fun checkBiometricSupportAndAuthenticate() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                showBiometricPrompt()
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Toast.makeText(this, "Hardware biométrico não disponível", Toast.LENGTH_LONG).show()
                findViewById<View>(R.id.main).visibility = View.VISIBLE
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                Toast.makeText(this, "Hardware biométrico indisponível no momento", Toast.LENGTH_LONG).show()
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Toast.makeText(this, "Nenhuma biometria cadastrada. Por favor, configure nas configurações do sistema.", Toast.LENGTH_LONG).show()
                findViewById<View>(R.id.main).visibility = View.VISIBLE
            }
            else -> {
                findViewById<View>(R.id.main).visibility = View.VISIBLE
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, getString(R.string.msg_auth_error, errString), Toast.LENGTH_SHORT).show()
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        finish()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(applicationContext, getString(R.string.msg_auth_success), Toast.LENGTH_SHORT).show()
                    findViewById<View>(R.id.main).visibility = View.VISIBLE
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, getString(R.string.msg_auth_failed), Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.prompt_title))
            .setSubtitle(getString(R.string.prompt_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showDeveloperInfoDialog() {
        val message = "${getString(R.string.dev_name)}\n\n" +
                "Portfólio: projetosmrsystem.free.nf\n" +
                "WhatsApp: (11) 96024-0070"

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dev_info_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.dev_whatsapp)) { _, _ ->
                try {
                    val url = "https://api.whatsapp.com/send?phone=5511960240070"
                    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (e: Exception) {
                    Toast.makeText(this, "Erro ao abrir WhatsApp", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(getString(R.string.dev_portfolio)) { _, _ ->
                try {
                    val url = "http://projetosmrsystem.free.nf"
                    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (e: Exception) {
                    Toast.makeText(this, "Erro ao abrir navegador", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.dev_close), null)
            .show()
    }
}
