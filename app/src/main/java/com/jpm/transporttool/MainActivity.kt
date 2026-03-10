package com.jpm.transporttool

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.jpm.transporttool.ui.theme.JPM_Transport_ToolTheme
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- DATOS ---

data class TransportCard(
    val name: String,
    val uidPrefix: String,
    val keyB: String,
    val color: Int = Color(0xFF1976D2).toArgb()
)

data class HistoryEntry(
    val timestamp: Long,
    val balance: Double
)

// --- ACTIVIDAD PRINCIPAL ---

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    private val KEY_A_GLOBAL = "99100225D83B"
    private val SECTOR_9 = 9
    private val BLOCK_37 = 37

    private var currentScreen by mutableStateOf("home")
    private var focusedUid by mutableStateOf("")

    private var savedCards by mutableStateOf<List<TransportCard>>(emptyList())
    private var historyUids by mutableStateOf<List<String>>(emptyList())

    private var displayUids by mutableStateOf<List<String>>(emptyList())

    private var historyUpdateTrigger by mutableIntStateOf(0)
    private var lastDetectedTag: Tag? = null
    private var prefilledPrefix by mutableStateOf("")

    private var showSuccess by mutableStateOf(false)
    private var finalAmount by mutableStateOf(0f)
    private var showNfcBubble by mutableStateOf(false)
    private var isReading by mutableStateOf(false)

    private var showCardOptions by mutableStateOf(false)
    private var optionUid by mutableStateOf("")

    private var isScanningForNewCard by mutableStateOf(false)

    private var showHelpDialog by mutableStateOf(false)
    private var showLegalDialog by mutableStateOf(false)

    private val DISCLAIMER_TEXT = """
        ⚠️ DESCARGO DE RESPONSABILIDAD (DISCLAIMER)

        SALDO ANDALUCIA es una herramienta desarrollada exclusivamente con fines educativos y de investigación académica.

        El objetivo de este software es el estudio de la seguridad en sistemas de radiofrecuencia (RFID/NFC) y el análisis de vulnerabilidades en protocolos MIFARE.

        El AUTOR NO SE HACE RESPONSABLE del mal uso que se le pueda dar a esta aplicación. El usuario final asume toda la responsabilidad legal derivada de la modificación, clonación o alteración de sistemas de acceso o pago de los que no sea legítimo propietario o administrador.

        El uso de esta herramienta para evadir pagos, acceder a recintos privados sin autorización o alterar títulos de transporte público puede constituir un delito según la legislación vigente (Art. 248 y siguientes del Código Penal en España sobre estafa informática y uso de instrumentos de pago).

        Al continuar, aceptas usar este software bajo tu propia responsabilidad.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        loadDataAndRefreshDisplay()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        pendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE)

        setContent {
            val prefs = remember { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            var appState by remember { mutableStateOf("splash") }
            val isDisclaimerAccepted = remember { mutableStateOf(prefs.getBoolean("disclaimer_accepted", false)) }

            JPM_Transport_ToolTheme {
                AnimatedContent(
                    targetState = appState,
                    transitionSpec = {
                        fadeIn(tween(600)).togetherWith(fadeOut(tween(600)))
                    },
                    label = "app_state_transition"
                ) { state ->
                    when (state) {
                        "splash" -> FakeSplashScreen(onFinished = {
                            appState = if (isDisclaimerAccepted.value) "main" else "disclaimer"
                        })
                        "disclaimer" -> DisclaimerScreen(onAccepted = {
                            prefs.edit().putBoolean("disclaimer_accepted", true).apply()
                            isDisclaimerAccepted.value = true
                            appState = "main"
                        })
                        "main" -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AnimatedContent(
                                    targetState = currentScreen,
                                    transitionSpec = {
                                        if (targetState == "home") {
                                            (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                                        } else {
                                            (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                                        } using SizeTransform(clip = false)
                                    },
                                    label = "screen_transition"
                                ) { screen ->
                                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                                        when (screen) {
                                            "home" -> HomeScreen()
                                            "recharge" -> RechargeScreen()
                                            "add_card" -> AddCardScreen()
                                        }
                                    }
                                }

                                if (isScanningForNewCard) {
                                    ScanningOverlay(onDismiss = { isScanningForNewCard = false })
                                }

                                AnimatedVisibility(
                                    visible = showNfcBubble && !isScanningForNewCard,
                                    enter = slideInVertically { it } + fadeIn(),
                                    exit = slideOutVertically { it } + fadeOut(),
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
                                ) {
                                    Surface(modifier = Modifier.padding(horizontal = 24.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 8.dp, shadowElevation = 4.dp) {
                                        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Nfc, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp)); Text("Tarjeta NFC detectada", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    LaunchedEffect(showNfcBubble) { if (showNfcBubble) { delay(2000); showNfcBubble = false } }
                                }

                                if (showSuccess) {
                                    SuccessOverlay(amount = finalAmount) { showSuccess = false; currentScreen = "home" }
                                }

                                if (showHelpDialog) {
                                    HelpDialog(onDismiss = { showHelpDialog = false })
                                }
                                if (showLegalDialog) {
                                    LegalDialog(onDismiss = { showLegalDialog = false })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadDataAndRefreshDisplay() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("cards", "[]") ?: "[]"
        val array = JSONArray(json)
        val tempList = mutableListOf<TransportCard>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            tempList.add(TransportCard(obj.getString("name"), obj.getString("prefix"), obj.getString("keyB"), obj.optInt("color", Color(0xFF1976D2).toArgb())))
        }
        savedCards = tempList

        val historyPrefs = getSharedPreferences("history_prefs", Context.MODE_PRIVATE)
        historyUids = historyPrefs.all.keys.toList().sortedByDescending { loadHistoryForUid(it).firstOrNull()?.timestamp ?: 0L }

        val displayList = mutableListOf<String>()
        savedCards.forEach { card ->
            val fullUid = historyUids.find { it.startsWith(card.uidPrefix) } ?: card.uidPrefix
            if (!displayList.contains(fullUid)) displayList.add(fullUid)
        }
        historyUids.forEach { uid ->
            val isSaved = savedCards.any { uid.startsWith(it.uidPrefix) }
            if (!isSaved && !displayList.contains(uid)) displayList.add(uid)
        }

        displayUids = displayList
    }

    private fun saveNewCard(card: TransportCard) {
        val newList = savedCards.filter { it.uidPrefix != card.uidPrefix } + card
        saveNewCardList(newList)
    }

    private fun saveNewCardList(newList: List<TransportCard>) {
        val array = JSONArray()
        newList.forEach {
            val obj = JSONObject(); obj.put("name", it.name); obj.put("prefix", it.uidPrefix); obj.put("keyB", it.keyB); obj.put("color", it.color)
            array.put(obj)
        }
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putString("cards", array.toString()).apply()
        loadDataAndRefreshDisplay()
    }

    private fun loadHistoryForUid(uid: String): List<HistoryEntry> {
        val prefs = getSharedPreferences("history_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString(uid, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<HistoryEntry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i); list.add(HistoryEntry(obj.getLong("t"), obj.getDouble("b")))
        }
        return list.sortedByDescending { it.timestamp }
    }

    private fun addToHistory(uid: String, balance: Double) {
        val history = loadHistoryForUid(uid).toMutableList()
        history.add(0, HistoryEntry(System.currentTimeMillis(), balance))
        val array = JSONArray()
        history.take(30).forEach { val obj = JSONObject(); obj.put("t", it.timestamp); obj.put("b", it.balance); array.put(obj) }
        getSharedPreferences("history_prefs", Context.MODE_PRIVATE).edit().putString(uid, array.toString()).apply()
        historyUpdateTrigger++
        loadDataAndRefreshDisplay()
    }

    private fun deleteHistoryItem(uid: String, entry: HistoryEntry) {
        val history = loadHistoryForUid(uid).filter { it != entry }
        val array = JSONArray()
        history.forEach { val obj = JSONObject(); obj.put("t", it.timestamp); obj.put("b", it.balance); array.put(obj) }
        val prefs = getSharedPreferences("history_prefs", Context.MODE_PRIVATE)
        if (history.isEmpty()) prefs.edit().remove(uid).apply() else prefs.edit().putString(uid, array.toString()).apply()
        historyUpdateTrigger++
        loadDataAndRefreshDisplay()
    }


    @Composable
    fun FakeSplashScreen(onFinished: () -> Unit) {
        LaunchedEffect(Unit) {
            delay(1800)
            onFinished()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Logo(text = "SaldoAND", isMini = false)
        }
    }

    @Composable
    fun DisclaimerScreen(onAccepted: () -> Unit) {
        var accepted by remember { mutableStateOf(false) }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Aviso Legal",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = DISCLAIMER_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Justify,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { accepted = !accepted },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("He leído y acepto los términos", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onAccepted,
                    enabled = accepted,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Continuar", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    }

    @Composable
    fun Logo(text: String, isMini: Boolean, modifier: Modifier = Modifier) {
        Column(modifier = modifier, horizontalAlignment = if (isMini) Alignment.Start else Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = if (isMini) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (!isMini) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "By: Marinetto",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    letterSpacing = 2.sp
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun HomeScreen() {
        // Añadimos +1 al pageCount para la tarjeta extra de "+"
        val pagerState = rememberPagerState(pageCount = { displayUids.size + 1 })
        val isDark = isSystemInDarkTheme()
        val haptic = LocalHapticFeedback.current

        val blurRadius by animateDpAsState(if (showCardOptions) 16.dp else 0.dp, label = "blur")

        // 🔥 CORRECCIÓN CLAVE: Identificar si estamos en la página del botón "+"
        val isAddNewPage = pagerState.currentPage == displayUids.size

        // Obtenemos el UID solo si no estamos en la página del botón "+"
        val currentUidPager = if (isAddNewPage) null else displayUids.getOrNull(pagerState.currentPage)
        val cardCfg = savedCards.find { currentUidPager != null && currentUidPager.startsWith(it.uidPrefix) }

        // Color de fondo dinámico
        val cardColor = if (isAddNewPage || displayUids.isEmpty()) Color.DarkGray else Color(cardCfg?.color ?: Color(0xFF1976D2).toArgb())

        val dynamicBgColor by animateColorAsState(
            targetValue = if (isDark) cardColor.copy(alpha = 0.12f).compositeOver(Color(0xFF000000))
            else cardColor.copy(alpha = 0.08f).compositeOver(Color.White),
            animationSpec = tween(600), label = "dynamic_bg"
        )

        LaunchedEffect(displayUids) {
            if (displayUids.isNotEmpty() && focusedUid.isNotEmpty()) {
                val index = displayUids.indexOf(focusedUid)
                if (index != -1) pagerState.animateScrollToPage(index)
            }
        }

        BackHandler(showCardOptions) { showCardOptions = false }

        Box(modifier = Modifier.fillMaxSize().background(dynamicBgColor)) {
            Scaffold(
                modifier = Modifier.blur(blurRadius),
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = { Logo(text = "SAND", isMini = true) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = dynamicBgColor),
                        actions = {
                            IconButton(onClick = { showLegalDialog = true }) { Icon(Icons.Default.Gavel, null) }
                            IconButton(onClick = { showHelpDialog = true }) { Icon(Icons.Default.HelpOutline, null) }
                        }
                    )
                }
            ) { padding ->

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {

                    // 1. TÍTULO GRANDE
                    item {
                        Text(
                            text = "Mis tarjetas",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                        )
                    }

                    // 2. CARRUSEL DE TARJETAS
                    item {
                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(horizontal = 40.dp),
                            pageSpacing = 20.dp,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        ) { page ->
                            if (page < displayUids.size) {
                                val uid = displayUids[page]
                                val cfg = savedCards.find { uid.startsWith(it.uidPrefix) }
                                val historyForPage = loadHistoryForUid(uid)
                                val balance = historyForPage.firstOrNull()?.balance ?: 0.0

                                TransportCardItem(
                                    name = cfg?.name ?: "Desconocida",
                                    uid = uid,
                                    balance = balance,
                                    color = Color(cfg?.color ?: Color(0xFF1976D2).toArgb()),
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        optionUid = uid
                                        showCardOptions = true
                                    }
                                )
                            } else {
                                AddNewCardButton { isScanningForNewCard = true }
                            }
                        }
                    }

                    // 3. BOTÓN DE ACCIÓN (Modificar saldo)
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        if (!isAddNewPage && currentUidPager != null) {
                            if (cardCfg != null && cardCfg.keyB.isNotBlank()) {
                                val textColor = if (cardColor.luminance() > 0.5f) Color.Black else Color.White
                                Button(
                                    onClick = {
                                        focusedUid = currentUidPager
                                        currentScreen = "recharge"
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = cardColor, contentColor = textColor),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Default.Edit, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Modificar saldo", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    val msg = if (cardCfg == null) "Mantén pulsada la tarjeta para configurar" else "Sin Key B configurada"
                                    Text(msg, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(40.dp))
                    }

                    // 4. HEADER ANCLADO
                    stickyHeader {
                        Surface(
                            color = dynamicBgColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Historial de lecturas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                            )
                        }
                    }

                    // 5. LISTA DEL HISTORIAL ANIMADA (CORREGIDA)
                    item {
                        AnimatedContent(
                            targetState = pagerState.currentPage, // Animamos basándonos en la PÁGINA, no en el UID
                            transitionSpec = {
                                val direction = if (targetState > initialState) 1 else -1
                                (slideInHorizontally(tween(400, easing = FastOutSlowInEasing)) { it * direction / 4 } + fadeIn(tween(400)))
                                    .togetherWith(slideOutHorizontally(tween(400, easing = FastOutSlowInEasing)) { -it * direction / 4 } + fadeOut(tween(400)))
                            },
                            label = "history_anim"
                        ) { page ->
                            val targetUid = if (page < displayUids.size) displayUids[page] else null

                            val targetHistory = remember(targetUid, historyUpdateTrigger) {
                                targetUid?.let { loadHistoryForUid(it) } ?: emptyList()
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (targetUid == null) {
                                    // Si es la página de añadir, no hay historial
                                    Text(
                                        text = "Añade una tarjeta para ver su historial",
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                                    )
                                } else if (targetHistory.isEmpty()) {
                                    Text(
                                        text = "No hay lecturas registradas",
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                                    )
                                } else {
                                    targetHistory.forEachIndexed { index, entry ->
                                        val prevBalance = targetHistory.getOrNull(index + 1)?.balance
                                        key(entry.timestamp) {
                                            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                                                HistoryItem(
                                                    entry = entry,
                                                    prevBalance = prevBalance,
                                                    onDelete = { deleteHistoryItem(targetUid, entry) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Espacio final
                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }
            }

            // Overlay de opciones
            if (showCardOptions) {
                CardOptionsOverlay(
                    uid = optionUid,
                    cardCfg = savedCards.find { optionUid.startsWith(it.uidPrefix) },
                    onDismiss = { showCardOptions = false }
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
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Añadir", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Añadir tarjeta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Acércala al teléfono", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }

    @Composable
    fun ScanningOverlay(onDismiss: () -> Unit) {
        val infiniteTransition = rememberInfiniteTransition(label = "nfc_waves")
        val waveScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_scale"
        )
        val waveAlpha by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_alpha"
        )

        BackHandler { onDismiss() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.fillMaxSize().scale(waveScale).clip(CircleShape).background(Color(0xFF1976D2).copy(alpha = waveAlpha)))
                    Box(modifier = Modifier.fillMaxSize().scale(waveScale * 0.7f).clip(CircleShape).background(Color(0xFF1976D2).copy(alpha = waveAlpha)))

                    Surface(modifier = Modifier.size(80.dp), shape = CircleShape, color = Color(0xFF1976D2)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Nfc, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
                Text("Acerca tu tarjeta", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Mantén la tarjeta contra la parte trasera\nde tu dispositivo para añadirla.", color = Color.White.copy(0.7f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(32.dp))
                TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.Gray) }
            }
        }
    }

    @Composable
    fun CardOptionsOverlay(
        uid: String,
        cardCfg: TransportCard?,
        onDismiss: () -> Unit
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
                    text = cardCfg?.name ?: "Tarjeta detectada",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ID: $uid",
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                if (cardCfg != null && cardCfg.keyB.isNotBlank()) {
                    ListItem(
                        headlineContent = { Text("Modificar saldo", fontWeight = FontWeight.Bold) },
                        leadingContent = { Icon(Icons.Default.Edit, null) },
                        modifier = Modifier.clickable {
                            onDismiss()
                            focusedUid = uid
                            currentScreen = "recharge"
                        }
                    )
                }

                ListItem(
                    headlineContent = { Text(if (cardCfg != null) "Personalizar nombre/color" else "Configurar esta tarjeta") },
                    leadingContent = { Icon(Icons.Default.Settings, null) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        prefilledPrefix = cardCfg?.uidPrefix ?: uid.take(2)
                        currentScreen = "add_card"
                    }
                )

                if (cardCfg != null) {
                    ListItem(
                        headlineContent = { Text("Eliminar configuración", color = Color.Red) },
                        leadingContent = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                        modifier = Modifier.clickable {
                            onDismiss()
                            val newList = savedCards.filter { it.uidPrefix != cardCfg.uidPrefix }
                            saveNewCardList(newList)
                        }
                    )
                }

                ListItem(
                    headlineContent = { Text("Borrar historial completo", color = Color.Red) },
                    leadingContent = { Icon(Icons.Default.History, null, tint = Color.Red) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        getSharedPreferences("history_prefs", Context.MODE_PRIVATE).edit().remove(uid).apply()
                        historyUpdateTrigger++
                        loadDataAndRefreshDisplay()
                    }
                )
            }
        }
    }

    @Composable
    fun LegalDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Información Legal", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = DISCLAIMER_TEXT,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido") } }
        )
    }

    @Composable
    fun HelpDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Cómo usar la App", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HelpStep("1", "Pulsa en la tarjeta '+' o aproxima directamente una tarjeta NFC para leerla.")
                    HelpStep("2", "Desliza lateralmente para ver el historial de tus tarjetas.")
                    HelpStep("3", "Mantén pulsada una tarjeta para ver opciones de personalización.")
                    HelpStep("4", "Usa 'Modificar saldo' para grabar un nuevo valor si tienes la Key B guardada.")
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido") } }
        )
    }

    @Composable
    fun HelpStep(number: String, text: String) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(modifier = Modifier.size(24.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Box(contentAlignment = Alignment.Center) { Text(number, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun TransportCardItem(
        name: String,
        uid: String,
        balance: Double,
        color: Color,
        modifier: Modifier = Modifier,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) {
        val shape = RoundedCornerShape(24.dp)
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.8f))))
                    .padding(24.dp)
            ) {
                Column {
                    Text(name, color = Color.White.copy(0.8f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp)); Text(String.format(Locale.getDefault(), "%.2f €", balance), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                }
                Text("ID: $uid", modifier = Modifier.align(Alignment.BottomStart), color = Color.White.copy(0.5f), fontSize = 11.sp)
                Icon(Icons.Default.Nfc, null, modifier = Modifier.align(Alignment.BottomEnd).size(44.dp), tint = Color.White.copy(0.2f))
            }
        }
    }

    @Composable
    fun HistoryItem(entry: HistoryEntry, prevBalance: Double? = null, onDelete: () -> Unit) {
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        val (icon, color) = when {
            prevBalance == null -> null to Color.Transparent
            entry.balance > prevBalance -> Icons.AutoMirrored.Filled.TrendingUp to Color(0xFF4CAF50)
            entry.balance < prevBalance -> Icons.AutoMirrored.Filled.TrendingDown to Color(0xFFF44336)
            else -> Icons.AutoMirrored.Filled.TrendingFlat to Color.Gray
        }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(sdf.format(Date(entry.timestamp)), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(
                        text = String.format(Locale.getDefault(), "%.2f €", entry.balance),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.4f), modifier = Modifier.size(20.dp)) }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RechargeScreen() {
        val cardCfg = savedCards.find { focusedUid.startsWith(it.uidPrefix) }
        val history = loadHistoryForUid(focusedUid); val lastBal = history.firstOrNull()?.balance ?: 0.0
        var amount by remember { mutableStateOf(String.format(Locale.getDefault(), "%.2f", lastBal)) }
        val keyboardController = LocalSoftwareKeyboardController.current
        BackHandler { currentScreen = "home" }
        Scaffold(topBar = { TopAppBar(title = { Text("Grabar saldo") }, navigationIcon = { IconButton(onClick = { currentScreen = "home" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState())) {
                val cardColor = Color(cardCfg?.color ?: Color(0xFF1976D2).toArgb()); val textColor = if (cardColor.luminance() > 0.5f) Color.Black else Color.White
                TransportCardItem(name = cardCfg?.name ?: "Desconocida", uid = focusedUid, balance = lastBal, color = cardColor, modifier = Modifier.height(180.dp).padding(8.dp))
                Spacer(modifier = Modifier.height(32.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Nuevo saldo (€)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = { Text("€ ") }, textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold))
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { keyboardController?.hide(); amount.replace(',', '.').toFloatOrNull()?.let { writeBalance(focusedUid, it, cardCfg?.keyB ?: "") } }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = cardColor, contentColor = textColor), elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)) {
                    Icon(Icons.Default.Save, null); Spacer(modifier = Modifier.width(12.dp)); Text("Confirmar y grabar", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AddCardScreen() {
        val existingCard = savedCards.find { it.uidPrefix == prefilledPrefix }
        var name by remember { mutableStateOf(existingCard?.name ?: "") }; var prefix by remember { mutableStateOf(prefilledPrefix) }; var keyB by remember { mutableStateOf(existingCard?.keyB ?: "") }; var selectedColor by remember { mutableStateOf(existingCard?.color ?: Color(0xFF1976D2).toArgb()) }
        val keyboardController = LocalSoftwareKeyboardController.current
        val colors = listOf(0xFF1976D2, 0xFFD32F2F, 0xFF388E3C, 0xFFFBC02D, 0xFF7B1FA2, 0xFFE64A19, 0xFF0097A7, 0xFF455A64).map { Color(it) }
        BackHandler { currentScreen = "home" }
        Scaffold(topBar = { TopAppBar(title = { Text("Personalizar") }, navigationIcon = { IconButton(onClick = { currentScreen = "home" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Configurar tarjeta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp)); OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(12.dp)); OutlinedTextField(value = prefix, onValueChange = { prefix = it.uppercase() }, label = { Text("Prefijo UID") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(12.dp)); OutlinedTextField(value = keyB, onValueChange = { keyB = it.uppercase() }, label = { Text("Key B (Opcional)") }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("12 caracteres hex") }, shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(24.dp)); Text("Color de la tarjeta:", style = MaterialTheme.typography.labelLarge); Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    colors.forEach { color ->
                        val isSelected = selectedColor == color.toArgb(); val scale by animateFloatAsState(if (isSelected) 1.2f else 1f, label = "color_scale")
                        Box(modifier = Modifier.size(36.dp).scale(scale).clip(CircleShape).background(color).clickable { selectedColor = color.toArgb() }.then(if (isSelected) Modifier.background(color, CircleShape).padding(4.dp).background(Color.White, CircleShape).padding(2.dp).background(color, CircleShape) else Modifier))
                    }
                }
                Spacer(modifier = Modifier.height(40.dp)); val buttonColor = Color(selectedColor); val textColor = if (buttonColor.luminance() > 0.5f) Color.Black else Color.White
                Button(onClick = {
                    if (name.isNotEmpty() && prefix.isNotEmpty()) {
                        if (keyB.isNotEmpty() && keyB.length != 12) {
                            Toast.makeText(this@MainActivity, "La Key B debe tener 12 caracteres hex", Toast.LENGTH_SHORT).show()
                        } else {
                            keyboardController?.hide(); saveNewCard(TransportCard(name, prefix, keyB, selectedColor)); currentScreen = "home"
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = textColor)) { Text("Guardar cambios", fontWeight = FontWeight.Bold) }
                if (existingCard != null) {
                    Spacer(modifier = Modifier.height(16.dp)); TextButton(onClick = { keyboardController?.hide(); val newList = savedCards.filter { it.uidPrefix != prefix }; saveNewCardList(newList); currentScreen = "home" }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar configuración", color = Color.Red) }
                }
            }
        }
    }

    private fun readCardBalance(tag: Tag) {
        val mifare = MifareClassic.get(tag) ?: return

        if (isScanningForNewCard) {
            isScanningForNewCard = false
        } else {
            isReading = true
            showNfcBubble = true
        }

        try {
            mifare.connect()
            val uid = tag.id.joinToString("") { "%02X".format(it) }.uppercase()
            if (mifare.authenticateSectorWithKeyA(SECTOR_9, hexToBytes(KEY_A_GLOBAL))) {
                val data = mifare.readBlock(BLOCK_37); val bal = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int / 200.0
                focusedUid = uid; addToHistory(uid, bal)
            }
        } catch (e: Exception) { runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } } finally { mifare.close(); isReading = false }
    }

    private fun writeBalance(uid: String, amount: Float, keyBStr: String) {
        val tag = lastDetectedTag ?: return run { Toast.makeText(this, "Sin tarjeta física", Toast.LENGTH_SHORT).show() }
        if (tag.id.joinToString("") { "%02X".format(it) }.uppercase() != uid) return run { Toast.makeText(this, "Tarjeta incorrecta", Toast.LENGTH_SHORT).show() }
        if (keyBStr.isEmpty()) return run { Toast.makeText(this, "No hay Key B guardada", Toast.LENGTH_SHORT).show() }
        val mifare = MifareClassic.get(tag) ?: return
        try {
            mifare.connect()
            if (mifare.authenticateSectorWithKeyB(SECTOR_9, hexToBytes(keyBStr))) {
                mifare.writeBlock(BLOCK_37, buildValueBlock((amount * 200).toInt()))
                finalAmount = amount; showSuccess = true; addToHistory(uid, amount.toDouble())
            }
        } catch (e: Exception) { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } finally { mifare.close() }
    }

    private fun buildValueBlock(units: Int): ByteArray {
        val block = ByteArray(16); val v = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(units).array(); val vi = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(units.inv()).array()
        System.arraycopy(v, 0, block, 0, 4); System.arraycopy(vi, 0, block, 4, 4); System.arraycopy(v, 0, block, 8, 4)
        block[12] = 0x00.toByte(); block[13] = 0xFF.toByte(); block[14] = 0x00.toByte(); block[15] = 0xFF.toByte()
        return block
    }

    private fun hexToBytes(s: String): ByteArray {
        val hex = if (s.length % 2 != 0) "0$s" else s
        val data = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }

    @Composable
    fun SuccessOverlay(amount: Float, onDismiss: () -> Unit) {
        val animProgress = remember { Animatable(0f) }; val checkAnim = remember { Animatable(0f) }
        LaunchedEffect(Unit) { animProgress.animateTo(1f, tween(600, easing = FastOutSlowInEasing)); checkAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)); delay(2200); onDismiss() }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), contentAlignment = Alignment.Center) {
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
                    Text("Operación completada", color = Color.White.copy(0.7f), fontSize = 16.sp); Spacer(modifier = Modifier.height(8.dp)); Text(String.format(Locale.getDefault(), "%.2f €", amount), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.graphicsLayer(scaleX = checkAnim.value, scaleY = checkAnim.value))
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        @Suppress("DEPRECATION") val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        lastDetectedTag = tag; if (tag != null) readCardBalance(tag)
    }

    override fun onResume() { super.onResume(); nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null) }
    override fun onPause() { super.onPause(); nfcAdapter?.disableForegroundDispatch(this) }
}