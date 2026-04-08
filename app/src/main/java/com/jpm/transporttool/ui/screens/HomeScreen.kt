package com.jpm.transporttool.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import com.jpm.transporttool.R
import com.jpm.transporttool.ui.components.*
import com.jpm.transporttool.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val pagerState = rememberPagerState(pageCount = { viewModel.displayUids.size + 1 })
    val isDark = isSystemInDarkTheme()

    val blurRadius by animateDpAsState(if (viewModel.showCardOptions) 16.dp else 0.dp, label = "blur")

    val isAddNewPage = pagerState.currentPage == viewModel.displayUids.size
    val currentUidPager = if (isAddNewPage) null else viewModel.displayUids.getOrNull(pagerState.currentPage)
    val cardCfg = viewModel.savedCards.find { currentUidPager != null && currentUidPager.startsWith(it.uidPrefix) }

    val cardColor = if (isAddNewPage || viewModel.displayUids.isEmpty()) Color.DarkGray else Color(cardCfg?.color ?: Color(0xFF1976D2).toArgb())

    val dynamicBgColor by animateColorAsState(
        targetValue = if (isDark) cardColor.copy(alpha = 0.12f).compositeOver(Color(0xFF000000))
        else cardColor.copy(alpha = 0.08f).compositeOver(Color.White),
        animationSpec = tween(600), label = "dynamic_bg"
    )

    LaunchedEffect(viewModel.focusedUid) {
        if (viewModel.displayUids.isNotEmpty() && viewModel.focusedUid.isNotEmpty()) {
            val index = viewModel.displayUids.indexOf(viewModel.focusedUid)
            if (index != -1) pagerState.animateScrollToPage(index)
        }
    }

    BackHandler(viewModel.showCardOptions) { viewModel.showCardOptions = false }

    var showRechargeModal by remember { mutableStateOf(false) }
    var rechargeUid by remember { mutableStateOf("") }
    var showProConfirm by remember { mutableStateOf(false) }

    if (showProConfirm) {
        AlertDialog(
            onDismissRequest = { showProConfirm = false },
            title = { Text("Activar Modo Pro") },
            text = { Text("El modo Pro permite editar el saldo y claves de seguridad (KEY B). Úsalo solo si sabes lo que haces, ya que podrías dejar la tarjeta inutilizable.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleProMode()
                        showProConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Activar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showRechargeModal) {
        RechargeDialog(
            uid = rechargeUid,
            viewModel = viewModel,
            onDismiss = { showRechargeModal = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(dynamicBgColor)) {
        Scaffold(
            modifier = Modifier.blur(blurRadius),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Logo(text = stringResource(R.string.logo_short), isMini = true) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = dynamicBgColor),
                    actions = {
                        IconButton(onClick = { viewModel.showSettingsDialog = true }) { Icon(Icons.Default.Settings, "Ajustes") }
                        IconButton(onClick = { viewModel.showLegalDialog = true }) { Icon(Icons.Default.Gavel, null) }
                        IconButton(onClick = { viewModel.showHelpDialog = true }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) }
                    }
                )
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    Text(
                        text = stringResource(R.string.my_cards),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                    )
                }

                item {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        pageSpacing = 20.dp,
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    ) { page ->
                        if (page < viewModel.displayUids.size) {
                            val uid = viewModel.displayUids[page]
                            val cfg = viewModel.savedCards.find { uid.startsWith(it.uidPrefix) }
                            val historyForPage = remember(uid, viewModel.refreshTrigger) { viewModel.loadHistoryForUid(uid) }
                            val balance = historyForPage.firstOrNull()?.balance ?: 0.0

                            val cardColor = if (cfg != null && cfg.type != com.jpm.transporttool.data.model.CardType.UNKNOWN) {
                                cfg.type.defaultColor
                            } else {
                                Color(cfg?.color ?: Color(0xFF1976D2).toArgb())
                            }
                            
                            TransportCardItem(
                                name = cfg?.name ?: cfg?.type?.label ?: stringResource(R.string.unknown_card),
                                uid = uid,
                                balance = balance,
                                color = cardColor,
                                imageRes = cfg?.type?.imageRes,
                                initialKeyB = cfg?.keyB ?: "",
                                onRecharge = { 
                                    rechargeUid = uid
                                    showRechargeModal = true 
                                },
                                onEditSave = { newName, newKeyB ->
                                    if (cfg != null) {
                                        viewModel.saveNewCard(cfg.copy(name = newName, keyB = newKeyB))
                                    } else {
                                        viewModel.prefilledPrefix = uid.take(2)
                                        viewModel.saveNewCard(com.jpm.transporttool.data.model.TransportCard(
                                            name = newName, 
                                            uidPrefix = uid.take(2), 
                                            keyB = newKeyB, 
                                            color = cardColor.toArgb()
                                        ))
                                    }
                                },
                                onDelete = if (cfg != null) {
                                    { viewModel.deleteCardConfig(cfg.uidPrefix) }
                                } else null,
                                isProMode = viewModel.isProMode
                            )
                        } else {
                            AddNewCardButton { viewModel.isScanningForNewCard = true }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    if (!isAddNewPage && currentUidPager != null) {
                        if (cardCfg == null) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.hold_to_configure), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    AnimatedContent(
                        targetState = pagerState.currentPage,
                        transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(tween(400, easing = FastOutSlowInEasing)) { it * direction / 4 } + fadeIn(tween(400)))
                                .togetherWith(slideOutHorizontally(tween(400, easing = FastOutSlowInEasing)) { -it * direction / 4 } + fadeOut(tween(400)))
                        },
                        label = "history_anim"
                    ) { page ->
                        val targetUid = if (page < viewModel.displayUids.size) viewModel.displayUids[page] else null

                        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                            if (targetUid == null) {
                                Text(
                                    text = stringResource(R.string.add_card_for_history),
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 20.dp)
                                )
                            } else {
                                Column {
                                    // Indicadores de estado físico de la tarjeta (solo si es la enfocada actualmente)
                                    if (targetUid == viewModel.focusedUid) {
                                        if (viewModel.isCardCorrupted) {
                                            Surface(
                                                modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("Datos inconsistentes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                                    }
                                                    Button(
                                                        onClick = { viewModel.normalizeCard() },
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                                    ) {
                                                        Text("Reparar", style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                            }
                                        } else {
                                            Surface(
                                                modifier = Modifier.padding(bottom = 12.dp),
                                                color = Color(0xFFE8F5E9),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Tarjeta íntegra", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                                }
                                            }
                                        }
                                    }

                                    HistorySection(
                                        uid = targetUid,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }

        if (viewModel.isScanningForNewCard) {
            ScanningOverlay(onDismiss = { viewModel.isScanningForNewCard = false })
        }

        if (viewModel.isWaitingToWrite) {
            val cardCfg = viewModel.savedCards.find { viewModel.focusedUid.startsWith(it.uidPrefix) }
            WritingOverlay(
                amount = viewModel.finalAmount,
                cardName = cardCfg?.name ?: stringResource(R.string.unknown_card),
                onDismiss = { viewModel.isWaitingToWrite = false }
            )
        }

        if (viewModel.isNormalizing) {
            val cardCfg = viewModel.savedCards.find { viewModel.focusedUid.startsWith(it.uidPrefix) }
            WritingOverlay(
                amount = 0f,
                cardName = cardCfg?.name ?: stringResource(R.string.unknown_card),
                isNormalizing = true,
                onDismiss = { viewModel.isNormalizing = false }
            )
        }
    }
}

@Composable
fun AddNewCardButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Card(
        modifier = Modifier.fillMaxSize().clickable { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = stringResource(R.string.add), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.add_card), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.bring_closer), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}
