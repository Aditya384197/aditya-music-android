package com.aditya.music.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
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

    LaunchedEffect(navController) {
        viewModel.openNowPlayingEvents.collect {
            if (navController.currentDestination?.route != Screen.NowPlaying.route) {
                navController.navigate(Screen.NowPlaying.route) { launchSingleTop = true }
            }
        }
    }

    val navItems = listOf(
        BottomNavItem(Screen.Home, Icons.Rounded.Home, "Home"),
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
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
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
                        onClick = { navController.navigate(Screen.NowPlaying.route) },
                        onDismiss = { viewModel.dismissPlayer() }
                    )
                }
            }
        }
    }
}
