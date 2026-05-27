package io.github.garemat.lunachron.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.BarcodeUtils
import io.github.garemat.lunachron.CharacterEvent
import io.github.garemat.lunachron.CharacterState
import io.github.garemat.lunachron.MigrationImportStatus
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataMigrationScreen(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onNavigateBack: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Export", "Import")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Transfer Data", style = theme.titleStyle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = theme.labelStyle) }
                    )
                }
            }
            when (selectedTab) {
                0 -> ExportTab(state = state, onEvent = onEvent, theme = theme)
                1 -> ImportTab(state = state, onEvent = onEvent, theme = theme)
            }
        }
    }
}

@Composable
private fun ExportTab(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (state.migrationExportCode == null) {
            onEvent(CharacterEvent.GenerateMigrationExport)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(theme.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(theme.verticalSpacing))

        if (state.migrationExportCode == null) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            Text("Generating…", style = MaterialTheme.typography.bodyMedium)
        } else {
            val qrBitmap = remember(state.migrationExportCode) {
                BarcodeUtils.generateQrCode(state.migrationExportCode, size = 512)
            }
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Migration QR code",
                    modifier = Modifier.size(240.dp)
                )
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
                Text(
                    "Scan on the new device to import",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Text(
                "Or copy the code below",
                style = theme.headerStyle.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            OutlinedTextField(
                value = state.migrationExportCode,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                shape = theme.cardShape,
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("LunaChron migration code", state.migrationExportCode))
                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = theme.cardShape
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy code", style = theme.buttonTextStyle)
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            Text(
                "Use the QR code to transfer to a new device. Use the text code when switching installation methods (e.g. GitHub → Play Store). Codes expire after 15 minutes. Keep this code private — anyone who has it can access your campaigns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(theme.verticalSpacing))
        }
    }
}

@Composable
private fun ImportTab(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    var code by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var transferRegistration by remember(state.isRegistered) { mutableStateOf(!state.isRegistered) }

    if (showScanner) {
        Box(modifier = Modifier.fillMaxSize()) {
            QrScanner(onResult = { scanned ->
                code = scanned
                showScanner = false
            })
            IconButton(
                onClick = { showScanner = false },
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Stop scanning",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(theme.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(theme.verticalSpacing))

        Text("Paste or scan your migration code", style = theme.headerStyle.copy(fontSize = 18.sp))
        Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it
                if (state.migrationImportStatus != null) onEvent(CharacterEvent.ClearMigrationState)
            },
            label = { Text("Migration code") },
            modifier = Modifier.fillMaxWidth(),
            shape = theme.cardShape,
            minLines = 3,
            maxLines = 6,
            isError = state.migrationImportStatus is MigrationImportStatus.Error
        )

        Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

        OutlinedButton(
            onClick = { showScanner = true },
            modifier = Modifier.fillMaxWidth(),
            shape = theme.cardShape
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan QR code", style = theme.buttonTextStyle)
        }

        Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

        ThemedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(theme.cardContentPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Transfer campaign registration", style = theme.headerStyle.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
                    Switch(checked = transferRegistration, onCheckedChange = { transferRegistration = it })
                }
                Text(
                    if (state.isRegistered)
                        "This device is already registered. Enabling this will replace your current campaign identity with the one from the source device."
                    else
                        "Transfers your campaign memberships and session to this device. The source device will remain logged in until its session expires.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isRegistered && transferRegistration)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

        Button(
            onClick = { onEvent(CharacterEvent.ImportMigrationData(code.trim(), transferRegistration)) },
            enabled = code.isNotBlank() && state.migrationImportStatus !is MigrationImportStatus.Loading && state.migrationImportStatus !is MigrationImportStatus.Success,
            modifier = Modifier.fillMaxWidth(),
            shape = theme.cardShape
        ) {
            if (state.migrationImportStatus is MigrationImportStatus.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Importing…", style = theme.buttonTextStyle)
            } else {
                Text("Import data", style = theme.buttonTextStyle)
            }
        }

        Spacer(modifier = Modifier.height(theme.verticalSpacing))

        when (val status = state.migrationImportStatus) {
            is MigrationImportStatus.Success -> {
                ThemedCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Data imported successfully. Your troupes, campaigns, and game history have been added to this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(theme.cardContentPadding)
                    )
                }
            }
            is MigrationImportStatus.Error -> {
                Text(
                    status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(theme.verticalSpacing))
    }
}
