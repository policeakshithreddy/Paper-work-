package com.example.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.preferences.UserPreferencesState
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PdfQuality
import com.example.domain.model.PrintPageOrder
import com.example.domain.model.ThemePreference
import com.example.ui.components.formatFileSize

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferencesState,
    vaultStorageBytes: Long,
    documentCount: Int,
    onThemeChange: (ThemePreference) -> Unit,
    onDefaultQualityChange: (PdfQuality) -> Unit,
    onDefaultPageSizeChange: (PageSizeOption) -> Unit,
    onDefaultPrintOrderChange: (PrintPageOrder) -> Unit,
    onDefaultEnhancedScanChange: (Boolean) -> Unit,
    onConfigurePin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onLockVaultNow: () -> Unit,
    onPurgeTempCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Appearance, hardware Keystore AES-256-GCM encryption, scanner defaults, and privacy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Appearance (System default, Light, Dark)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemePreference.entries.forEachIndexed { idx, theme ->
                            SegmentedButton(
                                selected = preferences.themePreference == theme,
                                onClick = { onThemeChange(theme) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = ThemePreference.entries.size
                                ),
                                modifier = Modifier.testTag("theme_option_${theme.name.lowercase()}")
                            ) {
                                Text(theme.label)
                            }
                        }
                    }
                }
            }
        }

        // 2. Security & Hardware Encryption Architecture
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Local Encryption & PIN Lock",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Android Keystore AES-256-GCM (No deprecated MasterKey)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = "Encrypted Assets: PDF files, Original & Enhanced scan layers, document thumbnails, and Room database metadata/OCR caches (bound with GCM Associated Authenticated Data). Excluded from cloud backups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "4-digit App PIN Lock",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (preferences.pinEnabled) {
                                    "Active • Salted PBKDF2-HMAC-SHA256 (120k iterations)"
                                } else {
                                    "Optional PIN gate separated from hardware encryption key"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = preferences.pinEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showPinSetupDialog = true
                                } else {
                                    onDisablePin()
                                }
                            },
                            modifier = Modifier.testTag("pin_lock_switch")
                        )
                    }

                    if (preferences.pinEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { showPinSetupDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Change PIN")
                            }
                            FilledTonalButton(
                                onClick = onLockVaultNow,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("lock_vault_now_button")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lock Now")
                            }
                        }
                    }
                }
            }
        }

        // 3. Scanner, PDF & Print Preferences
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Scanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Scanner, PDF & Print Defaults",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default to Enhanced scan preview",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Original scan is always preserved permanently",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.defaultEnhancedScan,
                            onCheckedChange = onDefaultEnhancedScanChange
                        )
                    }

                    Text("Default PDF quality", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdfQuality.entries.forEach { q ->
                            FilterChip(
                                selected = preferences.defaultPdfQuality == q,
                                onClick = { onDefaultQualityChange(q) },
                                label = { Text(q.label) }
                            )
                        }
                    }

                    Text("Default page size", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PageSizeOption.entries.forEach { s ->
                            FilterChip(
                                selected = preferences.defaultPageSize == s,
                                onClick = { onDefaultPageSizeChange(s) },
                                label = { Text("${s.label} (${s.dimensionsDesc})") }
                            )
                        }
                    }

                    Text("Default print page order", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        PrintPageOrder.entries.forEachIndexed { idx, order ->
                            SegmentedButton(
                                selected = preferences.defaultPrintOrder == order,
                                onClick = { onDefaultPrintOrderChange(order) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = PrintPageOrder.entries.size
                                )
                            ) {
                                Text(order.label)
                            }
                        }
                    }
                }
            }
        }

        // 4. Storage Information & Temporary Cache Cleanup
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Encrypted Vault Storage",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text(
                        text = "$documentCount encrypted documents • ${formatFileSize(vaultStorageBytes)} used on device",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Save location: App-private hardware-encrypted vault (/files/vault_pdfs & /files/vault_scans)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FilledTonalButton(
                        onClick = onPurgeTempCache,
                        modifier = Modifier.testTag("purge_temp_cache_button")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wipe Temporary Decrypted Session Cache")
                    }
                }
            }
        }

        // 5. About Paperwork & Privacy Policy
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "About Paperwork",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "Version 1.0.0 • Material 3 Expressive • Official Jetpack PDF 1.0.0-beta01 + Apache PDFBox-Android 2.0.27.0 + ML Kit Document Scanner",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showPrivacyPolicyDialog = true },
                        modifier = Modifier.testTag("privacy_policy_button")
                    ) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Privacy & Local Encryption Policy")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    if (showPinSetupDialog) {
        var pinInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = { Text("Set 4-digit Vault PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your PIN is hashed with a 16-byte cryptographic salt using PBKDF2-HMAC-SHA256 (120,000 iterations) and is separated from the Android Keystore AES-256-GCM key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pinInput = it },
                        label = { Text("4-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_setup_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfigurePin(pinInput)
                        showPinSetupDialog = false
                    },
                    enabled = pinInput.length == 4,
                    modifier = Modifier.testTag("pin_setup_confirm_button")
                ) {
                    Text("Enable PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = { Text("Privacy & Security Architecture") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "1. 100% On-Device Processing: Paperwork processes all scans, PDF conversions, OCR text recognition, and print jobs locally on your device. No documents are ever uploaded to external servers.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Hardware-Backed AES-256-GCM: PDF files, Original & Enhanced scan layers, thumbnails, and Room database metadata/OCR caches are encrypted with a 256-bit AES-GCM key inside AndroidKeyStore.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Zero Broad Storage Permissions: Paperwork uses Android's zero-permission Photo Picker and Storage Access Framework (SAF), and excludes vault files from cloud backups.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicyDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun PinLockGateScreen(
    failedAttempts: Int,
    lockoutUntilEpochMs: Long,
    onUnlockAttempt: (String, (Boolean, String?) -> Unit) -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("pin_lock_gate_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = "Unlock Paperwork Vault",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Enter your 4-digit PIN to access your AES-256-GCM encrypted documents.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        if (it.length <= 4 && it.all(Char::isDigit)) {
                            pinInput = it
                            feedbackMessage = null
                        }
                    },
                    label = { Text("4-digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_unlock_input")
                )

                if (feedbackMessage != null) {
                    Text(
                        text = feedbackMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        onUnlockAttempt(pinInput) { success, err ->
                            if (!success) {
                                feedbackMessage = err
                                pinInput = ""
                            }
                        }
                    },
                    enabled = pinInput.length == 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pin_unlock_submit_button")
                ) {
                    Text("Unlock Vault")
                }
            }
        }
    }
}
