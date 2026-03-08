package com.garemat.moonstone_companion.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.garemat.moonstone_companion.*
import com.garemat.moonstone_companion.ui.theme.LocalAppTheme

@Composable
fun TroupeSelector(
    troupes: List<Troupe>,
    selectedTroupe: Troupe?,
    allCharacters: List<Character>,
    onTroupeSelected: (Troupe) -> Unit,
    onCreateNewTroupe: () -> Unit,
    onEditTroupe: (Troupe) -> Unit,
    modifier: Modifier = Modifier,
    onPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var expanded by remember { mutableStateOf(false) }
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE

    Box(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedTroupe?.troupeName ?: "Select a troupe",
                        style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 18.sp) else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = if (selectedTroupe == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    )

                    if (selectedTroupe != null) {
                        IconButton(
                            onClick = { onEditTroupe(selectedTroupe) },
                            modifier = Modifier.size(24.dp).onGloballyPositioned {
                                onPositioned("EditTroupeButton", it)
                            }
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Troupe",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }

                if (selectedTroupe != null) {
                    val names = selectedTroupe.characterIds.mapNotNull { id ->
                        allCharacters.find { it.id == id }?.name
                    }
                    if (names.isNotEmpty()) {
                        Text(
                            text = names.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = if (isMoonstone) RoundedCornerShape(0.dp) else MenuDefaults.shape,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DropdownMenuItem(
                text = { 
                    Text(
                        "Create New Troupe", 
                        color = MaterialTheme.colorScheme.primary,
                        style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 16.sp) else MaterialTheme.typography.labelLarge
                    ) 
                },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    onCreateNewTroupe()
                    expanded = false
                },
                modifier = Modifier.onGloballyPositioned {
                    onPositioned("CreateNewTroupe", it)
                }
            )
            if (troupes.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            troupes.forEach { troupe ->
                DropdownMenuItem(
                    text = { 
                        Text(
                            troupe.troupeName,
                            style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 16.sp) else MaterialTheme.typography.bodyLarge
                        ) 
                    },
                    onClick = {
                        onTroupeSelected(troupe)
                        expanded = false
                    },
                    modifier = Modifier.onGloballyPositioned {
                        if (troupe.troupeName == "Example Troupe Name") {
                            onPositioned("ExampleTroupeItem", it)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TroupeSelectionDialog(
    troupe: Troupe,
    maxSelection: Int,
    allCharacters: List<Character>,
    onConfirmed: (List<Character>) -> Unit,
    onDismiss: () -> Unit
) {
    val troupeCharacters = remember(troupe) {
        troupe.characterIds.mapNotNull { id -> allCharacters.find { it.id == id } }
    }
    val selectedCharacters = remember { mutableStateListOf<Character>() }
    var expandedCharacterId by remember { mutableStateOf<Int?>(null) }
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize().padding(if (isMoonstone) 0.dp else 16.dp),
        shape = if (isMoonstone) RoundedCornerShape(0.dp) else RoundedCornerShape(28.dp),
        title = {
            Column {
                Text(
                    "Select Your Team", 
                    style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp) else MaterialTheme.typography.headlineSmall,
                    color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified
                )
                Text(
                    "Select up to $maxSelection characters for this game.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(troupeCharacters) { character ->
                    val isSelected = selectedCharacters.contains(character)
                    val isExpanded = expandedCharacterId == character.id

                    SelectionCharacterCard(
                        character = character,
                        isSelected = isSelected,
                        isExpanded = isExpanded,
                        onToggleSelect = {
                            if (isSelected) {
                                selectedCharacters.remove(character)
                            } else if (selectedCharacters.size < maxSelection) {
                                selectedCharacters.add(character)
                            }
                        },
                        onExpandClick = {
                            expandedCharacterId = if (isExpanded) null else character.id
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmed(selectedCharacters.toList()) },
                enabled = selectedCharacters.isNotEmpty() && selectedCharacters.size <= maxSelection,
                shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
            ) {
                Text("Confirm (${selectedCharacters.size}/$maxSelection)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SelectionCharacterCard(
    character: Character,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleSelect: () -> Unit,
    onExpandClick: () -> Unit
) {
    var isFlipped by remember { mutableStateOf(false) }
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onExpandClick() }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })

                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    CharacterPortrait(character = character, size = 40.dp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = character.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = character.tags.joinToString(", "), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                }

                IconButton(onClick = onExpandClick) {
                    Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }

            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Box(modifier = Modifier.padding(16.dp)) {
                    if (!isFlipped) {
                        CharacterFront(character = character, searchQuery = "", onFlip = { isFlipped = true })
                    } else {
                        CharacterBack(character = character, searchQuery = "", onFlip = { isFlipped = false })
                    }
                }
            }
        }
    }
}

@Composable
fun QrCodeDialog(
    troupeName: String,
    shareCode: String,
    onDismiss: () -> Unit
) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(if (isMoonstone) 0.dp else 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    troupeName, 
                    style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp) else MaterialTheme.typography.headlineSmall, 
                    textAlign = TextAlign.Center,
                    color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified
                )
                Spacer(modifier = Modifier.height(16.dp))

                val bitmap = remember(shareCode) { BarcodeUtils.generateQrCode(shareCode) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(250.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDismiss, 
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
