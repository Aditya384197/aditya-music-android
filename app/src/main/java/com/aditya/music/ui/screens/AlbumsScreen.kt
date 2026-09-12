package com.aditya.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aditya.music.ui.components.AdityaLogo
import com.aditya.music.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    viewModel: MusicViewModel,
    onAlbumClick: (Long) -> Unit
) {
    val albums by viewModel.albums.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Albums (${albums.size})", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums) { album ->
                Card(
                    modifier = Modifier.clickable { onAlbumClick(album.id) },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AdityaLogo(size = 50.dp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${album.artist} • ${album.songCount} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
