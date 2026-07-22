package com.steel101.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.steel101.musicplayer.data.MusicRepository
import com.steel101.musicplayer.ui.MusicPlayerScreen
import com.steel101.musicplayer.ui.MusicViewModel
import com.steel101.musicplayer.ui.theme.MusicPlayerTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MusicViewModel

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val storageGranted = permissions[if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }] ?: false

        if (storageGranted) {
            viewModel.loadSongs()
            checkManageExternalStoragePermission()
        } else {
            Toast.makeText(this, "Storage permission denied. App cannot scan music.", Toast.LENGTH_LONG).show()
        }
        
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordAudioGranted) {
            Log.w("MainActivity", "Record audio permission denied. Visualizer will not work.")
        }
    }

    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d("MainActivity", "Write permission granted by user")
            viewModel.retryLastTagWrite()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        val repository = (application as MusicApplication).repository
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MusicViewModel(repository, applicationContext) as T
            }
        })[MusicViewModel::class.java]

        checkAndRequestPermissions()

        setContent {
            val dominantColorVal = viewModel.dominantColor.value
            val dynamicThemingEnabled = viewModel.dynamicThemingEnabled.collectAsState().value
            
            val seedColor = if (!dynamicThemingEnabled && dominantColorVal != 0xFF1C1B1F.toInt()) {
                androidx.compose.ui.graphics.Color(dominantColorVal)
            } else {
                null
            }

            MusicPlayerTheme(
                seedColor = seedColor,
                dynamicColor = dynamicThemingEnabled
            ) {
                val pendingRequest = viewModel.pendingWriteRequest.value
                LaunchedEffect(pendingRequest) {
                    pendingRequest?.let { pendingIntent ->
                        val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        intentSenderLauncher.launch(request)
                        viewModel.consumePendingWriteRequest()
                    }
                }

                MusicPlayerScreen(viewModel = viewModel)
            }
        }
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    }

    private fun checkAndRequestPermissions() {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, storagePermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(storagePermission)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkManageExternalStoragePermission()
        }
    }

    private fun checkManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivity(intent)
                    Toast.makeText(this, "Please grant 'All files access' to allow editing music tags without prompts.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivity(intent)
                }
            }
        }
    }
}
