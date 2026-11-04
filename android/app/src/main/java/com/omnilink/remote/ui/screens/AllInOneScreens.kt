package com.omnilink.remote.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnilink.remote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniMainScreen(
    myId: String,
    myPin: String,
    isSharingActive: Boolean,
    isAccessibilityEnabled: Boolean,
    isUnattendedEnabled: Boolean,
    onStartSharing: (Boolean) -> Unit,
    onStopSharing: () -> Unit,
    onConnectToPartner: (String, String, Boolean) -> Unit,
    onToggleUnattended: (Boolean, String) -> Unit,
    onRequestAccessibility: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Logo",
                            tint = AccentBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("OmniLink Remote", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgSecondary,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = BgSecondary,
                contentColor = TextPrimary
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.ScreenShare, contentDescription = "Share Screen") },
                    label = { Text("Share Screen") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Devices, contentDescription = "Control") },
                    label = { Text("Control") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Host") },
                    label = { Text("24/7 Host") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.FolderZip, contentDescription = "Files") },
                    label = { Text("Files") }
                )
            }
        },
        containerColor = BgPrimary
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> QuickSupportTab(
                    myId = myId,
                    myPin = myPin,
                    isSharingActive = isSharingActive,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    onStartSharing = onStartSharing,
                    onStopSharing = onStopSharing,
                    onRequestAccessibility = { showAccessibilityDialog = true }
                )
                1 -> RemoteControllerTab(onConnect = onConnectToPartner)
                2 -> UnattendedHostTab(
                    myId = myId,
                    isEnabled = isUnattendedEnabled,
                    onToggle = onToggleUnattended
                )
                3 -> FileTransferTab()
            }
        }
    }

    if (showAccessibilityDialog) {
        AccessibilityDisclosureDialog(
            onDismiss = { showAccessibilityDialog = false },
            onConfirm = {
                showAccessibilityDialog = false
                onRequestAccessibility()
            }
        )
    }
}

/**
 * Tab 1: Share Screen (QuickSupport)
 */
@Composable
fun QuickSupportTab(
    myId: String,
    myPin: String,
    isSharingActive: Boolean,
    isAccessibilityEnabled: Boolean,
    onStartSharing: (Boolean) -> Unit,
    onStopSharing: () -> Unit,
    onRequestAccessibility: () -> Unit
) {
    val context = LocalContext.current
    var allowRemoteControl by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgSecondary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Your Partner ID",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = myId.ifEmpty { "458 920 114" },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue
                    )
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("OmniLink ID", myId.ifEmpty { "458 920 114" }))
                        Toast.makeText(context, "ID copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID", tint = TextPrimary)
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = BgTertiary)

                Text(
                    text = "Temporary PIN",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = myPin.ifEmpty { "8392" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Remote Control Permission Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgSecondary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remote Control Permission", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            "Allow remote partner to touch screen and navigate",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = allowRemoteControl,
                        onCheckedChange = { allowRemoteControl = it }
                    )
                }

                if (allowRemoteControl && !isAccessibilityEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestAccessibility,
                        colors = ButtonDefaults.buttonColors(containerColor = BgTertiary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AccessibilityNew, contentDescription = null, tint = AccentGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Accessibility Service", fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Main Action Button (Start / Stop)
        Button(
            onClick = {
                if (isSharingActive) {
                    onStopSharing()
                } else {
                    onStartSharing(allowRemoteControl)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSharingActive) AccentRed else AccentBlue
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = if (isSharingActive) Icons.Default.StopCircle else Icons.Default.PlayCircle,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isSharingActive) "Stop Sharing (End Session)" else "Start Screen Sharing",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

/**
 * Tab 2: Remote Controller (Control other device)
 */
@Composable
fun RemoteControllerTab(
    onConnect: (partnerId: String, pinOrPass: String, isLowLatency: Boolean) -> Unit
) {
    var partnerId by remember { mutableStateOf("") }
    var pinOrPass by remember { mutableStateOf("") }
    var isLowLatency by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgSecondary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Control Remote Device", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Enter the 9-digit Partner ID and security PIN of the remote device",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                OutlinedTextField(
                    value = partnerId,
                    onValueChange = { partnerId = it },
                    label = { Text("Partner ID (e.g. 458 920 114)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = pinOrPass,
                    onValueChange = { pinOrPass = it },
                    label = { Text("Security PIN or Master Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isLowLatency, onCheckedChange = { isLowLatency = it })
                    Text("Ultra Low-Latency Mode (0-Lag WebRTC 60 FPS)", fontSize = 12.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onConnect(partnerId, pinOrPass, isLowLatency) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Remote", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Tab 3: Unattended Access / 24/7 Host
 */
@Composable
fun UnattendedHostTab(
    myId: String,
    isEnabled: Boolean,
    onToggle: (Boolean, String) -> Unit
) {
    var masterPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgSecondary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("24/7 Unattended Host (Host Mode)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Set a permanent master password to remotely access this device anytime without user presence.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it },
                    label = { Text("Permanent Master Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onToggle(!isEnabled, masterPassword) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEnabled) AccentRed else AccentGreen
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEnabled) "Disable 24/7 Host" else "Enable 24/7 Host")
                }
            }
        }
    }
}

/**
 * Tab 4: Direct P2P File Transfer
 */
@Composable
fun FileTransferTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudSync,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Direct P2P File Transfer", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Transfer files directly between devices during an active session using encrypted WebRTC channels with zero cloud limits.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

/**
 * Mandatory Google Play Store Accessibility Disclosure Dialog
 */
@Composable
fun AccessibilityDisclosureDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AccessibilityNew, contentDescription = null, tint = AccentBlue) },
        title = { Text("Accessibility Service Disclosure") },
        text = {
            Text(
                "In compliance with Google Play Developer Policies:\n\n" +
                "OmniLink Remote uses the AccessibilityService API solely to enable authorized remote users to perform touch gestures (tap, drag, swipe) and system navigation (Back, Home, Recents) on your behalf during an active remote support session.\n\n" +
                "• This service DOES NOT read or collect your personal data, keystrokes, or screen text.\n" +
                "• You can revoke this permission anytime in Android Settings.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = TextPrimary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Agree & Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = BgSecondary
    )
}
