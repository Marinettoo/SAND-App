package com.jpm.transporttool.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpm.transporttool.R
import com.jpm.transporttool.data.model.TransportCard
import kotlinx.coroutines.delay

@Composable
fun ScanningOverlay(onDismiss: () -> Unit) {
    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            // Usamos la nueva animación realista también para el escaneo
            NfcAnimation(modifier = Modifier.size(200.dp))
            
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.scan_card_title),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.scan_card_msg),
                color = Color.White.copy(0.7f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(48.dp))
            TextButton(onClick = onDismiss) { 
                Text(stringResource(R.string.cancel).uppercase(), color = Color.White.copy(0.4f), letterSpacing = 1.sp) 
            }
        }
    }
}

@Composable
fun NfcAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_anim")
    
    // Animación más lenta (2.5s) y dirigida a la parte superior
    val cardOffset by infiniteTransition.animateFloat(
        initialValue = -130f, // Empieza desde arriba fuera del plano
        targetValue = -45f,   // Se detiene en la parte superior del dispositivo
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "card_move"
    )
    
    val cardAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2500
                0.0f at 0
                1.0f at 800
                1.0f at 2100
                0.0f at 2500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "card_alpha"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // "Teléfono" vertical, más grande y limpio (sin círculo central)
        Surface(
            modifier = Modifier
                .size(width = 95.dp, height = 165.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                // Detalle del auricular/cámara superior
                Box(
                    modifier = Modifier
                        .size(width = 30.dp, height = 4.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                )
            }
        }

        // "Tarjeta" animada aproximándose a la zona del lector (superior)
        Surface(
            modifier = Modifier
                .size(width = 105.dp, height = 66.dp)
                .offset(y = cardOffset.dp) 
                .graphicsLayer { 
                    alpha = cardAlpha
                    // Efecto de escala y rotación sutil para dar sensación de profundidad
                    val progress = (cardOffset + 130f) / 85f
                    val s = 0.75f + (progress.coerceIn(0f, 1f) * 0.25f)
                    scaleX = s
                    scaleY = s
                    rotationX = -10f * (1f - progress.coerceIn(0f, 1f))
                },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 16.dp
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                // Detalle del chip metálico
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 16.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
fun WritingOverlay(amount: Float, cardName: String, onDismiss: () -> Unit) {
    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            // Nueva animación de la tarjeta aproximándose
            NfcAnimation(modifier = Modifier.size(200.dp))
            
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                stringResource(R.string.writing_balance_title),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.writing_balance_msg, amount.toDouble()),
                color = Color.White.copy(0.7f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = cardName,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(40.dp))
            TextButton(onClick = onDismiss) { 
                Text(stringResource(R.string.cancel).uppercase(), color = Color.White.copy(0.4f), letterSpacing = 1.sp) 
            }
        }
    }
}

@Composable
fun FallingBills() {
    val infiniteTransition = rememberInfiniteTransition(label = "bills")
    val bills = remember { List(20) { index -> index } }

    Box(modifier = Modifier.fillMaxSize()) {

        bills.forEach { index ->
            val startDelay = index * 200
            val duration = 2500

            val yPos by infiniteTransition.animateFloat(
                initialValue = -100f,
                targetValue = 1200f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, delayMillis = startDelay, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "yPos"
            )

            val xOffset = remember { (0..1000).random().toFloat() / 1000f }
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f * 2,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, delayMillis = startDelay, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )

            Surface(
                modifier = Modifier
                    .offset(y = yPos.dp)
                    .align(Alignment.TopStart)
                    .padding(start = (xOffset * 400).dp)
                    .size(width = 60.dp, height = 30.dp)
                    .graphicsLayer { rotationZ = rotation },
                color = Color(0xFF4CAF50).copy(alpha = 0.8f),
                shape = RoundedCornerShape(2.dp),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("€", color = Color.White.copy(0.4f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun SuccessOverlay(amount: Float, onDismiss: () -> Unit) {
    val animProgress = remember { Animatable(0f) }
    val checkAnim = remember { Animatable(0f) }
    val showBills = amount >= 50f
    var startFalling by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        checkAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        
        if (showBills) {
            delay(250) // Delay de 1s después de completar la operación
            startFalling = true
            delay(4000) // Tiempo extra para ver la lluvia
        } else {
            delay(2200)
        }
        onDismiss()
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), contentAlignment = Alignment.Center) {
        if (showBills && startFalling) {
            FallingBills()
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { animProgress.value }, modifier = Modifier.fillMaxSize(), color = Color(0xFF4CAF50), strokeWidth = 6.dp, trackColor = Color.White.copy(0.1f), strokeCap = StrokeCap.Round)
                Canvas(modifier = Modifier.size(60.dp)) {
                    val path = Path().apply { moveTo(size.width * 0.1f, size.height * 0.5f); lineTo(size.width * 0.4f, size.height * 0.8f); lineTo(size.width * 0.95f, size.height * 0.2f) }
                    val pathMeasure = PathMeasure(); pathMeasure.setPath(path, false); val partialPath = Path(); pathMeasure.getSegment(0f, checkAnim.value * pathMeasure.length, partialPath)
                    drawPath(path = partialPath, color = Color.White, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Spacer(modifier = Modifier.height(40.dp)); AnimatedVisibility(visible = animProgress.value > 0.8f, enter = slideInVertically { it } + fadeIn()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.operation_completed), color = Color.White.copy(0.7f), fontSize = 16.sp); Spacer(modifier = Modifier.height(8.dp)); Text(stringResource(R.string.balance_format, amount), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.graphicsLayer(scaleX = checkAnim.value, scaleY = checkAnim.value))
            }
        }
        }
    }
}

@Composable
fun CardOptionsOverlay(
    uid: String,
    cardCfg: TransportCard?,
    onDismiss: () -> Unit,
    onRecharge: (String) -> Unit,
    onConfigure: (String, TransportCard?) -> Unit,
    onDeleteConfig: (TransportCard) -> Unit,
    onDeleteHistory: (String) -> Unit
) {
    val scale by animateFloatAsState(targetValue = 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy), label = "overlay_scale")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
                .clickable(enabled = false) { }
        ) {
            Text(
                text = cardCfg?.name ?: stringResource(R.string.card_detected),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.card_id, uid),
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            if (cardCfg != null && cardCfg.keyB.isNotBlank()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.modify_balance), fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(Icons.Default.Edit, null) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onRecharge(uid)
                    }
                )
            }

            ListItem(
                headlineContent = { Text(if (cardCfg != null) stringResource(R.string.customize_card) else stringResource(R.string.configure_card)) },
                leadingContent = { Icon(Icons.Default.Settings, null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onConfigure(uid, cardCfg)
                }
            )

            if (cardCfg != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.delete_config), color = Color.Red) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onDeleteConfig(cardCfg)
                    }
                )
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.delete_history), color = Color.Red) },
                leadingContent = { Icon(Icons.Default.History, null, tint = Color.Red) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onDeleteHistory(uid)
                }
            )
        }
    }
}

@Composable
fun LegalDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.legal_info), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.disclaimer_text),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Justify
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.understood)) } }
    )
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.how_to_use), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpStep("1", stringResource(R.string.help_step_1))
                HelpStep("2", stringResource(R.string.help_step_2))
                HelpStep("3", stringResource(R.string.help_step_3))
                HelpStep("4", stringResource(R.string.help_step_4))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.understood)) } }
    )
}
