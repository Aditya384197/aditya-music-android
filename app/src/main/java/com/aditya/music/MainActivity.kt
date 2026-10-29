package com.aditya.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aditya.music.media.service.MusicService
import com.aditya.music.ui.navigation.AdityaNavGraph
import com.aditya.music.ui.theme.AdityaMusicTheme
import com.aditya.music.ui.viewmodel.MusicViewModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: MusicViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.onPermissionResult(isGranted)
        if (isGranted) requestNotificationPermissionIfNeeded()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestAudioPermission()
        setupMediaController()

        val crashReportFile = File(filesDir, AdityaMusicApp.CRASH_REPORT_FILE)
        var savedCrashReport by mutableStateOf(
            runCatching {
                if (crashReportFile.exists()) crashReportFile.readText() else null
            }.getOrNull()
        )

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val useDarkTheme = themeMode != "light"
            val clipboard = LocalClipboardManager.current

            AdityaMusicTheme(darkTheme = useDarkTheme, amoled = themeMode == "amoled") {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AdityaNavGraph(
                        viewModel = viewModel,
                        onRequestPermission = { checkAndRequestAudioPermission() }
                    )

                    savedCrashReport?.let { report ->
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("पिछले क्रैश की रिपोर्ट") },
                            text = {
                                Text(
                                    text = report,
                                    modifier = Modifier
                                        .heightIn(max = 420.dp)
                                        .verticalScroll(rememberScrollState()),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(report))
                                }) { Text("रिपोर्ट कॉपी करें") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    runCatching { crashReportFile.delete() }
                                    savedCrashReport = null
                                }) { Text("बंद करें और हटाएँ") }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.onPermissionResult(true)
                requestNotificationPermissionIfNeeded()
            }
            else -> requestPermissionLauncher.launch(permission)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setupMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controllerFuture?.get()?.let { controller ->
                viewModel.bindMediaController(controller)
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
