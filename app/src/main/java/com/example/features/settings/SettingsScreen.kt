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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
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
import com.example.core.security.KeystoreSecurityLevel
import com.example.core.security.VaultKeyState
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
    keystoreSecurityLevel: KeystoreSecurityLevel,
    vaultKeyState: VaultKeyState,
    activeKeyVersion: Int,
    biometricAvailable: Boolean,
    vaultStorageBytes: Long,
    documentCount: Int,
    unreadableDocumentCount: Int = 0,
    onThemeChange: (ThemePreference) -> Unit,
    onDefaultQualityChange: (PdfQuality) -> Unit,
    onDefaultPageSizeChange: (PageSizeOption) -> Unit,
    onDefaultPrintOrderChange: (PrintPageOrder) -> Unit,
    onDefaultEnhancedScanChange: (Boolean) -> Unit,
    onBiometricUnlockChange: (Boolean) -> Unit,
    onConfigurePin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onLockVaultNow: () -> Unit,
    onCreateReplacementKeyAfterInvalidation: (Boolean) -> Unit,
    onPurgeTempCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showKeyRecoveryDialog by remember { mutableStateOf(false) }

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
                    text = "Appearance, Android Keystore AES-256-GCM encryption, Biometric/PIN access lock, and defaults.",
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

        // 2. Versioned Keystore Encryption & App Access Lock Boundary
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
                                text = "Vault Encryption & Access Lock",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Key v$activeKeyVersion • ${keystoreSecurityLevel.label} • State: ${vaultKeyState.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "19-Byte PWKV Header & Unique 12-Byte IV: Every PDF, Original/Enhanced scan, thumbnail, and Room metadata blob is encrypted with a fresh 96-bit SecureRandom IV and bound to its ID via GCM AAD.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Key Invalidation Disclosure: Because Paperwork is 100% local with zero cloud key escrow, if the OS invalidates your Keystore key, files encrypted with the old key version become permanently unreadable unless previously exported.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (vaultKeyState == VaultKeyState.INVALIDATED ||
                        vaultKeyState == VaultKeyState.RECOVERY_REQUIRED ||
                        unreadableDocumentCount > 0
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Keystore Key Recovery Required ($vaultKeyState)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Text(
                                    text = "Creating a new Keystore key (v${activeKeyVersion + 1}) allows new documents to be encrypted, but CANNOT decrypt $unreadableDocumentCount existing document(s) encrypted with an invalidated key.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Button(
                                    onClick = { showKeyRecoveryDialog = true }
                                ) {
                                    Text("Provision New Key (v${activeKeyVersion + 1})")
                                }
                            }
                        }
                    }

                    // App Access Lock (PIN + Biometric/Device Credential)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Access Lock (4-digit PIN + Biometric)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (preferences.pinEnabled) {
                                    "UI Session Gate Active • PBKDF2-HMAC-SHA256 (120k iterations)"
                                } else {
                                    "UI access lock separated from the Keystore AES-256-GCM key"
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
                        if (biometricAvailable) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Allow Biometric / Device Credential Unlock",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Use fingerprint, face, or OS screen lock on the unlock gate",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = preferences.biometricUnlockEnabled,
                                    onCheckedChange = onBiometricUnlockChange
                                )
                            }
                        }

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
                                text = "Default to Enhanced Color scan preview",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Preserves color stamps & ink while original scan is kept permanently",
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
                        text = "Flash Storage Note: Paperwork encrypts all vault assets prior to writing to flash storage and deletes temporary viewer/print session files deterministically on close.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FilledTonalButton(
                        onClick = onPurgeTempCache,
                        modifier = Modifier.testTag("purge_temp_cache_button")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clean Temporary Decrypted Session Cache")
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
                        text = "Version 1.0.0 • minSdk 28 • Official AndroidX PDF 1.0.0-beta01 + Apache PDFBox-Android 2.0.27.0 + ML Kit Document Scanner",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showPrivacyPolicyDialog = true },
                        modifier = Modifier.testTag("privacy_policy_button")
                    ) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Privacy & Security Architecture")
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
            title = { Text("Set 4-digit App Access PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your 4-digit PIN acts as an application UI access lock (hashed with a 16-byte salt via PBKDF2-HMAC-SHA256, 120,000 iterations) with retry cooldowns. It is not the cryptographic encryption key itself; you can also unlock via Biometrics or Device Credentials.",
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

    if (showKeyRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showKeyRecoveryDialog = false },
            title = { Text("Create New Keystore Key (v${activeKeyVersion + 1})?") },
            text = {
                Text(
                    text = "Generating a new Keystore key allows new documents to be encrypted, but CANNOT decrypt files encrypted with the invalidated old key (v$activeKeyVersion). Would you also like to purge unreadable old-key records?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showKeyRecoveryDialog = false
                        onCreateReplacementKeyAfterInvalidation(true)
                    }
                ) {
                    Text("Create Key & Purge Old Records")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showKeyRecoveryDialog = false
                        onCreateReplacementKeyAfterInvalidation(false)
                    }
                ) {
                    Text("Create Key (Keep Records Marked Unreadable)")
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
                        text = "1. 100% On-Device Processing: Paperwork processes all scans, PDF conversions, embedded text extraction, ML Kit OCR, and print jobs locally on your device.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Versioned Keystore AES-256-GCM: Every encrypted asset carries a 19-byte PWKV header with a unique 12-byte random IV. Detected Keystore backing on this device: ${keystoreSecurityLevel.label}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Flash Storage & Session Lifecycle: Because wear-leveled flash storage (F2FS/ext4) cannot be physically erased via userspace byte overwrites, Paperwork encrypts data prior to persistence and cleans up temporary viewer/print session files deterministically.",
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
    biometricEnabled: Boolean = false,
    onRequestBiometricUnlock: (() -> Unit)? = null,
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
                    text = "Enter your 4-digit PIN or use Biometric / Device Credential authentication to access your encrypted documents.",
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
                    Text("Unlock with PIN")
                }

                if (biometricEnabled && onRequestBiometricUnlock != null) {
                    FilledTonalButton(
                        onClick = onRequestBiometricUnlock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("biometric_unlock_button")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock with Biometric / Device Lock")
                    }
                }
            }
        }
    }
}
