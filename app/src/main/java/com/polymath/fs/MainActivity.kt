package com.polymath.fs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.polymath.fs.databinding.ActivityMainBinding
import com.polymath.fs.ui.FileBrowserFragment
import com.polymath.fs.ui.HomeDashboardFragment
import com.polymath.fs.ui.ScriptManagerActivity
import com.polymath.fs.viewers.EditorActivity
import com.polymath.fs.viewmodels.FileSystemViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: FileSystemViewModel by viewModels {
        FileSystemViewModel.provideFactory(application as PolymathApp)
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupMainInterface()
        } else {
            showPermissionGate()
        }
    }
    
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                setupMainInterface()
            } else {
                showPermissionGate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val currentTheme = prefs.getString("theme", "cyberpunk")
        val themeResId = when (currentTheme) {
            "cyberpunk" -> R.style.Theme_Cyberpunk
            "ocean_breeze" -> R.style.Theme_OceanBreeze
            "dracula" -> R.style.Theme_Dracula
            "solarized_dark" -> R.style.Theme_SolarizedDark
            "sunset" -> R.style.Theme_Sunset
            "hacker_green" -> R.style.Theme_HackerGreen
            "neon" -> R.style.Theme_Neon
            "material_you" -> R.style.Theme_MaterialYou
            "ios_like" -> R.style.Theme_IosLike
            "true_amoled" -> R.style.Theme_TrueAmoled
            else -> R.style.Theme_Cyberpunk
        }
        setTheme(themeResId)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermission.setOnClickListener {
            requestStoragePermission()
        }

        setupBottomNavigation()
        checkPermissions()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    showDashboard()
                    true
                }
                R.id.nav_files -> {
                    showFileBrowser()
                    true
                }
                R.id.nav_scripts -> {
                    startActivity(Intent(this, ScriptManagerActivity::class.java))
                    false
                }
                R.id.nav_editor -> {
                    startActivity(Intent(this, EditorActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    private fun checkPermissions() {
        if (hasStoragePermission()) {
            setupMainInterface()
        } else {
            showPermissionGate()
        }
    }

    private fun setupMainInterface() {
        binding.permissionGate.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE
        binding.bottomNavigation.visibility = View.VISIBLE

        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            showDashboard()
        }
    }

    fun showDashboard() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HomeDashboardFragment())
            .commit()
    }

    fun showFileBrowser() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, FileBrowserFragment())
            .commit()
    }

    fun navigateToDirectory(path: String) {
        binding.bottomNavigation.selectedItemId = R.id.nav_files
        viewModel.navigateTo(path)
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun showPermissionGate() {
        binding.permissionGate.visibility = View.VISIBLE
        binding.fragmentContainer.visibility = View.GONE
        binding.bottomNavigation.visibility = View.GONE
    }
}
