package com.aditya.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Brightness1
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aditya.music.ui.components.AdityaLogo
import com.aditya.music.ui.theme.AmoledBlack
import com.aditya.music.ui.theme.AmoledSurface
import com.aditya.music.ui.theme.BackgroundDark
import com.aditya.music.ui.theme.BackgroundLight
import com.aditya.music.ui.theme.CardDark
import com.aditya.music.ui.theme.CardLight
import com.aditya.music.ui.theme.SurfaceDark
import com.aditya.music.ui.theme.SurfaceLight
import com.aditya.music.ui.theme.AdityaIndigo
import com.aditya.music.data.headset.HeadsetProfile
import com.aditya.music.data.headset.HeadsetProfile.Companion.TYPE_EARBUDS
import com.aditya.music.data.headset.HeadsetProfile.Companion.TYPE_HEADPHONES
import com.aditya.music.data.headset.HeadsetProfile.Companion.TYPE_NECKBAND
import com.aditya.music.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    var showThemePicker by remember { mutableStateOf(false) }
    var showHeadsetPicker by remember { mutableStateOf(false) }
    val selectedHeadset by viewModel.selectedHeadsetProfile.collectAsState()
    val selectedHeadsetType by viewModel.selectedHeadsetType.collectAsState()

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemePicker = true },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = {
                            Text("Theme", fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(themeLabel(themeMode))
                        },
                        trailingContent = {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHeadsetPicker = true },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = {
                            Text("Set your headset", fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(
                                selectedHeadset?.name
                                    ?: selectedHeadsetTypeLabel(selectedHeadsetType)
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.Rounded.BluetoothAudio,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            Divider()

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Text(
                    "About Aditya Music",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AdityaLogo(size = 48.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Aditya Music",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Developer: Aditya",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Version 1.0.0 (Release)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "A private, 100% offline, lightweight and high-fidelity Android audio player crafted for music lovers.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    if (showThemePicker) {
        ThemePickerDialog(
            currentMode = themeMode,
            onSelect = { mode ->
                viewModel.setTheme(mode)
                showThemePicker = false
            },
            onDismiss = { showThemePicker = false }
        )
    }

    if (showHeadsetPicker) {
        HeadsetSetupDialog(
            profiles = viewModel.headsetProfiles.collectAsState().value,
            currentProfile = selectedHeadset,
            currentType = selectedHeadsetType,
            onTypeSelected = viewModel::saveHeadsetType,
            onProfileSelected = viewModel::saveHeadsetProfile,
            onClear = viewModel::clearHeadsetProfile,
            onDismiss = { showHeadsetPicker = false }
        )
    }
}

@Composable
private fun HeadsetSetupDialog(
    profiles: List<HeadsetProfile>,
    currentProfile: HeadsetProfile?,
    currentType: String?,
    onTypeSelected: (String) -> Unit,
    onProfileSelected: (HeadsetProfile) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember(currentProfile?.id, currentType) {
        mutableIntStateOf(if (currentType == null) 0 else 1)
    }
    var type by remember(currentType) { mutableStateOf(currentType) }
    var query by remember { mutableStateOf("") }
    var selected by remember(currentProfile) { mutableStateOf(currentProfile) }

    val filtered = remember(profiles, type, query) {
        profiles
            .asSequence()
            .filter { profile ->
                when (type) {
                    TYPE_HEADPHONES -> profile.type == "headphones"
                    TYPE_EARBUDS -> profile.type == "earbuds"
                    TYPE_NECKBAND -> profile.type == "earbuds"
                    else -> true
                }
            }
            .filter { it.matches(query) }
            .take(80)
            .toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Set your headset", fontWeight = FontWeight.Bold)
                Text(
                    if (step == 0) "Choose the device type" else "Choose an offline measured model",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (step == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeadsetTypeCard("Earbuds", "In-ear / true wireless", Icons.Rounded.BluetoothAudio, type == TYPE_EARBUDS) {
                        type = TYPE_EARBUDS
                    }
                    HeadsetTypeCard("Neckband", "Wireless band with in-ear drivers", Icons.Rounded.BluetoothAudio, type == TYPE_NECKBAND) {
                        type = TYPE_NECKBAND
                    }
                    HeadsetTypeCard("Headphones", "On-ear / over-ear", Icons.Rounded.Headphones, type == TYPE_HEADPHONES) {
                        type = TYPE_HEADPHONES
                    }
                    Text(
                        "Model matching uses measured profiles only. Selecting a type alone does not invent an EQ curve; it keeps the source unchanged until a measured model is selected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        placeholder = { Text("Search brand or model") },
                        label = { Text("Headset model") }
                    )
                    Spacer(Modifier.height(10.dp))
                    if (filtered.isEmpty()) {
                        Text(
                            "No measured offline profile found. Try another model or use Skip.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 330.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filtered, key = { it.id }) { profile ->
                                val isSelected = selected?.id == profile.id
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { selected = profile },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(profile.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                            Text(
                                                "Measured profile • ${profile.source}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSelected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${profiles.size} offline profiles available in this build",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (step == 0) onDismiss()
                else { type = null; selected = null; onClear(); onDismiss() }
            }) {
                Text(if (step == 0) "Skip" else "Clear")
            }
        },
        confirmButton = {
            if (step == 0) {
                Button(
                    enabled = type != null,
                    onClick = {
                        type?.let(onTypeSelected)
                        step = 1
                    }
                ) { Text("Next") }
            } else {
                Button(
                    enabled = selected != null,
                    onClick = {
                        selected?.let(onProfileSelected)
                        onDismiss()
                    }
                ) { Text("Save") }
            }
        }
    )
}

@Composable
private fun HeadsetTypeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        ListItem(
            leadingContent = { Icon(icon, null) },
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(subtitle) },
            trailingContent = { if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }
        )
    }
}

private fun selectedHeadsetTypeLabel(type: String?): String = when (type) {
    TYPE_EARBUDS -> "Earbuds selected — choose a measured model"
    TYPE_NECKBAND -> "Neckband selected — choose a measured model"
    TYPE_HEADPHONES -> "Headphones selected — choose a measured model"
    else -> "Choose earbuds, neckband or headphones"
}

@Composable
private fun ThemePickerDialog(
    currentMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Choose theme", fontWeight = FontWeight.Bold)
                Text(
                    "Preview how Aditya Music will look",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemePreviewCard(
                    mode = "dark",
                    label = "Dark",
                    icon = Icons.Rounded.DarkMode,
                    selected = currentMode == "dark",
                    onClick = { onSelect("dark") }
                )
                ThemePreviewCard(
                    mode = "light",
                    label = "Light",
                    icon = Icons.Rounded.LightMode,
                    selected = currentMode == "light",
                    onClick = { onSelect("light") }
                )
                ThemePreviewCard(
                    mode = "amoled",
                    label = "AMOLED",
                    icon = Icons.Rounded.Brightness1,
                    selected = currentMode == "amoled",
                    onClick = { onSelect("amoled") }
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ThemePreviewCard(
    mode: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = when (mode) {
        "light" -> Triple(BackgroundLight, SurfaceLight, CardLight)
        "amoled" -> Triple(AmoledBlack, AmoledSurface, Color(0xFF121212))
        else -> Triple(BackgroundDark, SurfaceDark, CardDark)
    }
    val (background, surface, card) = colors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = if (selected) BorderStroke(2.dp, AdityaIndigo) else null,
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(122.dp)
                    .background(background, RoundedCornerShape(14.dp))
                    .padding(7.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(15.dp)
                                .background(AdityaIndigo, CircleShape)
                        )
                        Spacer(Modifier.width(5.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(if (mode == "light") Color(0xFFCBD5E1) else Color(0xFF334155), RoundedCornerShape(6.dp))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .background(card, RoundedCornerShape(9.dp))
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .background(card, RoundedCornerShape(7.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(14.dp)
                                    .background(card, RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    when (mode) {
                        "light" -> "Bright surfaces and clean contrast"
                        "amoled" -> "Pure black background for OLED screens"
                        else -> "Dark surfaces with soft contrast"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                AssistChip(
                    onClick = onClick,
                    label = { Text("On") }
                )
            }
        }
    }
}

private fun themeLabel(mode: String): String = when (mode) {
    "light" -> "Light"
    "amoled" -> "AMOLED"
    else -> "Dark"
}
