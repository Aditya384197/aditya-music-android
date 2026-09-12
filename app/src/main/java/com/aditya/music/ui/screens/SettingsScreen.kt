package com.aditya.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aditya.music.ui.components.AdityaLogo
import com.aditya.music.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = themeMode == "system",
                        onClick = { viewModel.setTheme("system") },
                        label = { Text("System") }
                    )
                    FilterChip(
                        selected = themeMode == "dark",
                        onClick = { viewModel.setTheme("dark") },
                        label = { Text("Dark") }
                    )
                    FilterChip(
                        selected = themeMode == "amoled",
                        onClick = { viewModel.setTheme("amoled") },
                        label = { Text("AMOLED") }
                    )
                    FilterChip(
                        selected = themeMode == "light",
                        onClick = { viewModel.setTheme("light") },
                        label = { Text("Light") }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text(
                    text = "About Aditya Music",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row {
                            AdityaLogo(size = 48.dp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Aditya Music", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Developer: Aditya", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Version 1.0.0 (Release)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "A private, 100% offline, lightweight and high-fidelity Android audio player crafted for music lovers.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
