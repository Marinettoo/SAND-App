package com.jpm.transporttool.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpm.transporttool.R
import com.jpm.transporttool.data.model.HistoryEntry
import com.jpm.transporttool.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun Logo(text: String, isMini: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = text,
            style = if (isMini) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = (-2).sp
        )
        if (!isMini) {
            Text(
                text = stringResource(R.string.by_author),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun TransportCardItem(
    name: String,
    uid: String,
    balance: Double,
    color: Color,
    modifier: Modifier = Modifier,
    imageRes: Int? = null,
    onRecharge: () -> Unit = {},
    onEditSave: (String, String) -> Unit = { _, _ -> },
    onDelete: (() -> Unit)? = null,
    initialKeyB: String = "",
    isProMode: Boolean = false
) {
    var isFlipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "card_rotation"
    )

    val shape = RoundedCornerShape(24.dp)
    val isJoven = name.contains("Joven", ignoreCase = true) || imageRes == R.drawable.tarjeta_joven
    val contentColor = if (imageRes != null) {
        if (isJoven) Color(0xFF00853E) else Color.Black
    } else Color.White

    var editName by remember { mutableStateOf(name) }
    var editKeyB by remember { mutableStateOf(initialKeyB) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_config)) },
            text = { Text("¿Estás seguro de que deseas eliminar esta tarjeta? Esta acción no se puede revertir.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete?.invoke()
                }) {
                    Text(stringResource(R.string.delete_config), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            },
        shape = shape,
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .clickable { if (!isFlipped && isProMode) onRecharge() }
                .then(
                    if (imageRes == null) {
                        Modifier.background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.8f))))
                    } else {
                        Modifier.background(Color.White)
                    }
                )
        ) {
            if (rotation <= 90f) {
                if (imageRes != null) {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }

                Column(
                    modifier = Modifier.padding(
                        start = if (isJoven) 20.dp else 24.dp,
                        top = if (isJoven) 50.dp else 24.dp
                    )
                ) {
                    if (!isJoven) {
                        Text(name, color = contentColor.copy(0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = stringResource(R.string.balance_format, balance),
                        color = contentColor,
                        fontSize = if (isJoven) 24.sp else 38.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                IconButton(
                    onClick = { isFlipped = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Icon(Icons.Default.Settings, null, tint = contentColor.copy(alpha = 0.6f))
                }

                if (!isJoven) {
                    Text(
                        stringResource(R.string.card_id, uid),
                        modifier = Modifier.align(Alignment.BottomStart).padding(24.dp),
                        color = contentColor.copy(0.5f),
                        fontSize = 11.sp
                    )
                    Icon(
                        Icons.Default.Nfc,
                        null,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).size(44.dp),
                        tint = contentColor.copy(0.2f)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Editar Tarjeta", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                            
                            Column {
                                Text("Nombre", color = Color.Black.copy(0.5f), fontSize = 11.sp)
                                BasicTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color.Black)
                                )
                                HorizontalDivider(color = Color.Black.copy(0.2f), thickness = 1.dp)
                            }

                            if (isProMode) {
                                Column {
                                    Text("Clave B (Opcional)", color = Color.Black.copy(0.5f), fontSize = 11.sp)
                                    BasicTextField(
                                        value = editKeyB,
                                        onValueChange = { editKeyB = it.uppercase() },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                                        singleLine = true,
                                        cursorBrush = SolidColor(Color.Black)
                                    )
                                    HorizontalDivider(color = Color.Black.copy(0.2f), thickness = 1.dp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onEditSave(editName, editKeyB)
                                    isFlipped = false
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Guardar", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.8f))
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Borrar", fontSize = 12.sp)
                            }
                        }
                    }

                    IconButton(
                        onClick = { isFlipped = false },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Close, null, tint = if (imageRes != null) Color.Black else Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    entry: HistoryEntry,
    prevBalance: Double? = null,
    isDeletable: Boolean = true,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val (icon, color) = when {
        prevBalance == null -> null to Color.Transparent
        entry.balance > prevBalance -> Icons.AutoMirrored.Filled.TrendingUp to Color(0xFF4CAF50)
        entry.balance < prevBalance -> Icons.AutoMirrored.Filled.TrendingDown to Color(0xFFF44336)
        else -> Icons.AutoMirrored.Filled.TrendingFlat to Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(sdf.format(Date(entry.timestamp)), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(
                    text = stringResource(R.string.balance_format, entry.balance),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (isDeletable) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.4f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechargeDialog(
    uid: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val history = viewModel.loadHistoryForUid(uid)
    val lastBal = history.firstOrNull()?.balance ?: 0.0
    var amount by remember { mutableStateOf(String.format(Locale.getDefault(), "%.2f", lastBal)) }
    
    val cardCfg = viewModel.savedCards.find { uid.startsWith(it.uidPrefix) }
    val hasKeyB = !cardCfg?.keyB.isNullOrBlank()

    val parsedAmount = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
    val isSameAmount = Math.abs(parsedAmount - lastBal) < 0.01
    val isOverLimit = parsedAmount > 327.67

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = hasKeyB && !isSameAmount && parsedAmount >= 0 && !isOverLimit,
                onClick = {
                    amount.replace(',', '.').toFloatOrNull()?.let {
                        viewModel.focusedUid = uid
                        viewModel.finalAmount = it
                        viewModel.isWaitingToWrite = true
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cargar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        title = { Text("Modificar Saldo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!hasKeyB) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Error: KEY B no configurada. Edita la tarjeta primero.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Text("Introduce el nuevo saldo para la tarjeta:", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Nuevo Saldo (€)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = hasKeyB,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                if (hasKeyB && isSameAmount && parsedAmount > 0) {
                    Text(
                        "El saldo es idéntico al actual",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (hasKeyB && isOverLimit) {
                    Text(
                        "El saldo máximo permitido es 327,67 €",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    )
}

@Composable
fun HistorySection(
    uid: String,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val history = remember(uid, viewModel.refreshTrigger) { viewModel.loadHistoryForUid(uid) }
    var isExpanded by remember { mutableStateOf(false) }
    
    val displayHistory = if (isExpanded) history else history.take(5)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.reading_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            if (history.size > 5) {
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Ver menos" else "Ver todo (${history.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Text(
                "No hay lecturas recientes",
                modifier = Modifier.padding(vertical = 16.dp),
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            displayHistory.forEachIndexed { index, entry ->
                val prevBal = history.getOrNull(index + 1)?.balance
                HistoryItem(
                    entry = entry,
                    prevBalance = prevBal,
                    isDeletable = entry != history.firstOrNull(),
                    onDelete = { viewModel.deleteHistoryItem(uid, entry) }
                )
            }
        }
    }
}

@Composable
fun HelpStep(title: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
