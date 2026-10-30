package com.music.player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.music.player.playback.MusicPlaybackService
import com.music.player.ui.MusicRoot
import com.music.player.ui.MusicViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MusicViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.onAudioPermissionResult(hasAudioPermission())
        connectPlaybackService()

        setContent {
            MusicRoot(
                viewModel = viewModel,
                onRequestAudioPermission = ::requestAudioPermission
            )
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        audioPermissionLauncher.launch(permission)
    }

    private fun connectPlaybackService() {
        val token = SessionToken(this, ComponentName(this, MusicPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                if (!future.isCancelled && !isDestroyed) {
                    viewModel.attachController(future.get())
                } else if (!future.isCancelled) {
                    MediaController.releaseFuture(future)
                }
            } catch (_: Exception) {
                if (!isDestroyed) {
                    viewModel.reportError("प्लेयर सेवा से कनेक्शन नहीं बन पाया। ऐप को दोबारा खोलकर कोशिश करो।")
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        viewModel.detachController()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}
