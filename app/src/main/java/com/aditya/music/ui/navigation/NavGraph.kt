package com.aditya.music.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.aditya.music.ui.components.AdityaLogo
import com.aditya.music.ui.components.MiniPlayer
import com.aditya.music.ui.screens.*
import com.aditya.music.ui.viewmodel.MusicViewModel

data class BottomNavItem(val screen: Screen, val icon: ImageVector, val label: String)

@Composable
fun AdityaNavGraph(
    viewModel: MusicViewModel,
    onRequestPermission: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val navItems = listOf(
        BottomNavItem(Screen.Home, Icons.Rounded.Home, "Home"),
        BottomNavItem(Screen.Songs, Icons.Rounded.MusicNote, "Songs"),
        BottomNavItem(Screen.Albums, Icons.Rounded.Album, "Albums"),
        BottomNavItem(Screen.Artists, Icons.Rounded.Person, "Artists"),
        BottomNavItem(Screen.Playlists, Icons.Rounded.QueueMusic, "Playlists")
    )

    Scaffold(
        bottomBar = {
            if (currentRoute != Screen.NowPlaying.route) {
                NavigationBar {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.screen.route,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToSongs = { navController.navigate(Screen.Songs.route) },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onOpenNowPlaying = { navController.navigate(Screen.NowPlaying.route) }
                    )
                }
                composable(Screen.Songs.route) {
                    SongsScreen(viewModel = viewModel)
                }
                composable(Screen.Albums.route) {
                    AlbumsScreen(
                        viewModel = viewModel,
                        onAlbumClick = { albumId -> navController.navigate(Screen.AlbumDetail.createRoute(albumId)) }
                    )
                }
                composable(Screen.Artists.route) {
                    ArtistsScreen(
                        viewModel = viewModel,
                        onArtistClick = { artistId -> navController.navigate(Screen.ArtistDetail.createRoute(artistId)) }
                    )
                }
                composable(Screen.Playlists.route) {
                    PlaylistsScreen(viewModel = viewModel)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.NowPlaying.route) {
                    NowPlayingScreen(
                        viewModel = viewModel,
                        onClose = { navController.popBackStack() },
                        onOpenQueue = { navController.navigate(Screen.Queue.route) }
                    )
                }
                composable(Screen.Queue.route) {
                    QueueScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.AlbumDetail.route,
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) { entry ->
                    val albumId = entry.arguments?.getLong("albumId")
                    val album by viewModel.albums.collectAsState()
                    val selectedAlbum = album.firstOrNull { it.id == albumId }
                    MediaDetailScreen(
                        title = selectedAlbum?.name ?: "Album",
                        subtitle = selectedAlbum?.artist ?: "Unknown Artist",
                        songs = selectedAlbum?.songs.orEmpty(),
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.ArtistDetail.route,
                    arguments = listOf(navArgument("artistId") { type = NavType.LongType })
                ) { entry ->
                    val artistId = entry.arguments?.getLong("artistId")
                    val artists by viewModel.artists.collectAsState()
                    val selectedArtist = artists.firstOrNull { it.id == artistId }
                    MediaDetailScreen(
                        title = selectedArtist?.name ?: "Artist",
                        subtitle = selectedArtist?.let { "${it.albumCount} albums • ${it.songCount} songs" } ?: "",
                        songs = selectedArtist?.songs.orEmpty(),
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // Floating MiniPlayer above bottom bar
            if (currentSong != null && currentRoute != Screen.NowPlaying.route) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    MiniPlayer(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.playNext() },
                        onClick = { navController.navigate(Screen.NowPlaying.route) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaDetailScreen(
    title: String,
    subtitle: String,
    songs: List<com.aditya.music.data.model.Song>,
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No songs available")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    ListItem(
                        headlineContent = { Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                        supportingContent = { if (subtitle.isNotBlank()) Text(subtitle) },
                        leadingContent = { Icon(Icons.Rounded.MusicNote, contentDescription = null) }
                    )
                    HorizontalDivider()
                }
                items(songs, key = { it.id }) { song ->
                    ListItem(
                        headlineContent = { Text(song.title, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                        supportingContent = { Text("${song.artist} • ${song.formattedDuration}") },
                        leadingContent = { AdityaLogo(size = 40.dp) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.playSongs(songs, songs.indexOf(song)) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                            }
                        },
                        modifier = Modifier.clickable {
                            viewModel.playSongs(songs, songs.indexOf(song))
                        }
                    )
                }
            }
        }
    }
}
