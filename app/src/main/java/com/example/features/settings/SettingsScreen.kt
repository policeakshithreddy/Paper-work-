package com.example.features.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.preferences.UserPreferencesState
import com.example.domain.model.*
import com.example.ui.components.SectionTitle

@Composable
fun SettingsScreen(preferences: UserPreferencesState, onTheme: (ThemePreference) -> Unit,
    onDynamic: (Boolean) -> Unit, onQuality: (PdfQuality) -> Unit, onPageSize: (PageSizeOption) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SectionTitle("Make it yours") }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemePreference.entries.forEachIndexed { index, item ->
                    SegmentedButton(preferences.themePreference == item, { onTheme(item) },
                        shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(item.label) }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 31) item {
            Card {
                ListItem(headlineContent = { Text("Wallpaper colors") },
                    supportingContent = { Text("A palette that feels at home on your device") },
                    leadingContent = { Icon(Icons.Rounded.Palette, null) },
                    trailingContent = { Switch(preferences.useDynamicColor, onDynamic) })
            }
        }
        item { SectionTitle("Your page, your way") }
        item {
            Text("PDF quality", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                PdfQuality.entries.forEachIndexed { index, item ->
                    SegmentedButton(preferences.defaultPdfQuality == item, { onQuality(item) },
                        shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(item.label) }
                }
            }
        }
        item {
            Text("Paper size", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                PageSizeOption.entries.forEachIndexed { index, item ->
                    SegmentedButton(preferences.defaultPageSize == item, { onPageSize(item) },
                        shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(item.label) }
                }
            }
        }
        item { SectionTitle("Files you own") }
        item {
            Card {
                ListItem(headlineContent = { Text("Downloads / Paperwork") },
                    supportingContent = { Text("Saved documents are ordinary files. Open them in Files, share them, or keep them after uninstalling Paperwork. They are not encrypted by the app.") },
                    leadingContent = { Icon(Icons.Rounded.FolderOpen, null) })
            }
        }
        item { Text("Paperwork · 2.0\nA little less paper. A little more possibility.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
