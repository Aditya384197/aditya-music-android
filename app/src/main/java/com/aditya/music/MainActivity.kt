package com.aditya.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aditya.music.media.service.MusicService
import com.aditya.music.media.player.VolumeController
import com.aditya.music.ui.navigation.AdityaNavGraph
import com.aditya.music.ui.theme.AdityaMusicTheme
import com.aditya.music.ui.viewmodel.MusicViewModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {

    private var volumeOverlayVisible by mutableStateOf(false)
    private val volumeOverlayHandler = Handler(Looper.getMainLooper())
    private val hideVolumeOverlay = Runnable { volumeOverlayVisible = false }

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

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val useDarkTheme = themeMode != "light"

            AdityaMusicTheme(darkTheme = useDarkTheme, amoled = themeMode == "amoled") {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AdityaNavGraph(
                            viewModel = viewModel,
                            onRequestPermission = { checkAndRequestAudioPermission() }
                        )
                    }

                    if (volumeOverlayVisible) {
                        val percent = viewModel.volumeState.collectAsState().value.percent
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                                    .padding(horizontal = 18.dp, vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.layout.Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (percent >= 100) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    androidx.compose.foundation.layout.Spacer(Modifier.width(5.dp))
                                    Text(
                                        "Volume ${percent}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
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
            else -> {
                requestPermissionLauncher.launch(permission)
            }
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val step = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 10 else -10
                val next = (viewModel.volumeState.value.percent + step).coerceIn(VolumeController.MIN_PERCENT, VolumeController.MAX_PERCENT)
                viewModel.setVolumePercent(next)
                volumeOverlayVisible = true
                volumeOverlayHandler.removeCallbacks(hideVolumeOverlay)
                volumeOverlayHandler.postDelayed(hideVolumeOverlay, 1200L)
            }
            // Consume the foreground volume key so Android's system volume panel does not appear.
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        volumeOverlayHandler.removeCallbacks(hideVolumeOverlay)
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
