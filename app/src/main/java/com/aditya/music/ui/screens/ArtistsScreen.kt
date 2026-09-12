package com.aditya.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aditya.music.ui.components.AdityaLogo
import com.aditya.music.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    viewModel: MusicViewModel,
    onArtistClick: (Long) -> Unit
) {
    val artists by viewModel.artists.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artists (${artists.size})", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(artists) { artist ->
                ListItem(
                    headlineContent = { Text(artist.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("${artist.songCount} songs • ${artist.albumCount} albums") },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            AdityaLogo(size = 32.dp)
                        }
                    },
                    modifier = Modifier.clickable { onArtistClick(artist.id) }
                )
            }
        }
    }
}
