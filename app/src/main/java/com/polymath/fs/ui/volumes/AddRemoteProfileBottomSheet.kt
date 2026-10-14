package com.polymath.fs.ui.volumes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.polymath.fs.PolymathApp
import com.polymath.fs.R
import com.polymath.fs.data.remote.RemoteProfileEntity
import com.polymath.fs.viewmodels.FileSystemViewModel
import java.util.UUID

class AddRemoteProfileBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: FileSystemViewModel by activityViewModels {
        FileSystemViewModel.provideFactory(requireActivity().application as PolymathApp)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_add_remote_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tilProfileLabel = view.findViewById<TextInputLayout>(R.id.tilProfileLabel)
        val etProfileLabel = view.findViewById<TextInputEditText>(R.id.etProfileLabel)
        val tilHost = view.findViewById<TextInputLayout>(R.id.tilHost)
        val etHost = view.findViewById<TextInputEditText>(R.id.etHost)
        val tilPort = view.findViewById<TextInputLayout>(R.id.tilPort)
        val etPort = view.findViewById<TextInputEditText>(R.id.etPort)
        val tilUsername = view.findViewById<TextInputLayout>(R.id.tilUsername)
        val etUsername = view.findViewById<TextInputEditText>(R.id.etUsername)
        val rgAuthMode = view.findViewById<RadioGroup>(R.id.rgAuthMode)
        val rbPassword = view.findViewById<RadioButton>(R.id.rbPassword)
        val rbKey = view.findViewById<RadioButton>(R.id.rbKey)
        val tilPassword = view.findViewById<TextInputLayout>(R.id.tilPassword)
        val etPassword = view.findViewById<TextInputEditText>(R.id.etPassword)
        val tilKeyPath = view.findViewById<TextInputLayout>(R.id.tilKeyPath)
        val etKeyPath = view.findViewById<TextInputEditText>(R.id.etKeyPath)
        val tilPassphrase = view.findViewById<TextInputLayout>(R.id.tilPassphrase)
        val etPassphrase = view.findViewById<TextInputEditText>(R.id.etPassphrase)
        val tilDefaultPath = view.findViewById<TextInputLayout>(R.id.tilDefaultPath)
        val etDefaultPath = view.findViewById<TextInputEditText>(R.id.etDefaultPath)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        rgAuthMode.setOnCheckedChangeListener { _, checkedId ->
            val isKey = checkedId == R.id.rbKey
            tilPassword.visibility = if (isKey) View.GONE else View.VISIBLE
            tilKeyPath.visibility = if (isKey) View.VISIBLE else View.GONE
            tilPassphrase.visibility = if (isKey) View.VISIBLE else View.GONE
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnSave.setOnClickListener {
            val host = etHost.text?.toString()?.trim() ?: ""
            val portText = etPort.text?.toString()?.trim() ?: "22"
            val username = etUsername.text?.toString()?.trim() ?: ""
            val labelText = etProfileLabel.text?.toString()?.trim() ?: ""
            val defaultPathText = etDefaultPath.text?.toString()?.trim() ?: "/"

            var hasError = false
            if (host.isEmpty()) {
                tilHost.error = "Host is required"
                hasError = true
            } else {
                tilHost.error = null
            }

            if (username.isEmpty()) {
                tilUsername.error = "Username is required"
                hasError = true
            } else {
                tilUsername.error = null
            }

            if (hasError) return@setOnClickListener

            val port = portText.toIntOrNull() ?: 22
            val isKeyAuth = rbKey.isChecked
            val authType = if (isKeyAuth) "key" else "password"
            val keyPath = if (isKeyAuth) {
                etKeyPath.text?.toString()?.trim()?.ifBlank { null }
            } else null

            val secret = if (isKeyAuth) {
                etPassphrase.text?.toString()?.ifEmpty { null }
            } else {
                etPassword.text?.toString()?.ifEmpty { null }
            }

            val profileId = UUID.randomUUID().toString()
            val resolvedLabel = if (labelText.isNotBlank()) labelText else "$username@$host"
            val resolvedPath = when {
                defaultPathText.isBlank() -> "/"
                defaultPathText.startsWith("/") -> defaultPathText
                else -> "/$defaultPathText"
            }

            val profile = RemoteProfileEntity(
                id = profileId,
                label = resolvedLabel,
                scheme = "sftp",
                host = host,
                port = port,
                username = username,
                authType = authType,
                keyPath = keyPath,
                encryptedSecretRef = if (!secret.isNullOrEmpty()) "remote_secret_$profileId" else null,
                hostKeyFingerprint = null,
                defaultPath = resolvedPath
            )

            viewModel.addRemoteProfile(profile, secret)
            dismiss()
        }
    }

    companion object {
        const val TAG = "AddRemoteProfileBottomSheet"

        fun newInstance(): AddRemoteProfileBottomSheet {
            return AddRemoteProfileBottomSheet()
        }
    }
}
