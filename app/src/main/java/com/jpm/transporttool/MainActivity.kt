package com.jpm.transporttool

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jpm.transporttool.data.repository.CardRepository
import com.jpm.transporttool.ui.components.*
import com.jpm.transporttool.ui.screens.AddCardScreen
import com.jpm.transporttool.ui.screens.DisclaimerScreen
import com.jpm.transporttool.ui.screens.HomeScreen
import com.jpm.transporttool.ui.theme.JPM_Transport_ToolTheme
import com.jpm.transporttool.ui.viewmodel.MainViewModel
import com.jpm.transporttool.util.NfcUtils
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    private lateinit var viewModel: MainViewModel

    companion object {
        private const val KEY_A_SECTOR_9 = "99100225D83B"
        private const val KEY_A_SECTOR_8 = "51B3EF60BF56"
        private const val SECTOR_8 = 8
        private const val SECTOR_9 = 9
        private const val BLOCK_34 = 34
        private const val BLOCK_36 = 36
        private const val BLOCK_37 = 37
        private const val BLOCK_38 = 38
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val repository = CardRepository(this)
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository) as T
            }
        })[MainViewModel::class.java]

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE
        )

        setContent {
            var appState by remember { mutableStateOf("splash") }
            val isDisclaimerAccepted = remember { mutableStateOf(viewModel.isDisclaimerAccepted()) }

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
                            viewModel.setDisclaimerAccepted(true)
                            isDisclaimerAccepted.value = true
                            appState = "main"
                        })
                        "main" -> MainContent(viewModel)
                    }
                }
            }
        }
    }

    @Composable
    fun MainContent(viewModel: MainViewModel) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = viewModel.currentScreen,
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
                        "home" -> HomeScreen(viewModel)
                        "add_card" -> AddCardScreen(viewModel)
                    }
                }
            }

            if (viewModel.isScanningForNewCard) {
                ScanningOverlay(onDismiss = { viewModel.isScanningForNewCard = false })
            }

            if (viewModel.isWaitingToWrite || viewModel.isNormalizing) {
                val cardCfg = viewModel.savedCards.find { viewModel.focusedUid.startsWith(it.uidPrefix) }
                WritingOverlay(
                    amount = if (viewModel.isNormalizing) 0f else viewModel.finalAmount,
                    cardName = cardCfg?.name ?: stringResource(R.string.unknown_card),
                    isNormalizing = viewModel.isNormalizing,
                    onDismiss = { 
                        viewModel.isWaitingToWrite = false
                        viewModel.isNormalizing = false
                    }
                )
            }

            AnimatedVisibility(
                visible = viewModel.showNfcBubble && !viewModel.isScanningForNewCard,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            ) {
                Surface(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Nfc, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.nfc_detected), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                LaunchedEffect(viewModel.showNfcBubble) {
                    if (viewModel.showNfcBubble) {
                        delay(2000)
                        viewModel.showNfcBubble = false
                    }
                }
            }

            if (viewModel.showSuccess) {
                SuccessOverlay(
                    amount = viewModel.finalAmount,
                    isRepair = viewModel.isRepairSuccess
                ) {
                    viewModel.showSuccess = false
                    viewModel.isRepairSuccess = false
                    viewModel.currentScreen = "home"
                }
            }

            if (viewModel.showHelpDialog) {
                HelpDialog(onDismiss = { viewModel.showHelpDialog = false })
            }
            if (viewModel.showLegalDialog) {
                LegalDialog(onDismiss = { viewModel.showLegalDialog = false })
            }

            if (viewModel.showSettingsDialog) {
                SettingsOverlay(viewModel = viewModel, onDismiss = { viewModel.showSettingsDialog = false })
            }
        }
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
            Logo(text = stringResource(R.string.logo_full), isMini = false)
        }
    }

    private fun readCardBalance(tag: Tag) {
        val mifare = MifareClassic.get(tag) ?: return

        if (viewModel.isScanningForNewCard) {
            viewModel.isScanningForNewCard = false
        } else {
            viewModel.isReading = true
            viewModel.showNfcBubble = true
        }

        try {
            mifare.connect()
            val uid = NfcUtils.bytesToHex(tag.id).uppercase()
            Log.d("NFC_DEBUG", "Tarjeta detectada UID: $uid")
            
            var cardType = com.jpm.transporttool.data.model.CardType.UNKNOWN

            // Intentar leer Bloque 34 (Sector 8) para identificar tipo
            val authS8 = mifare.authenticateSectorWithKeyA(SECTOR_8, NfcUtils.hexToBytes(KEY_A_SECTOR_8))
            Log.d("NFC_DEBUG", "Autenticación Sector 8: $authS8")
            
            if (authS8) {
                val contractData = mifare.readBlock(BLOCK_34)
                val firstByte = contractData[0].toInt() and 0xFF
                Log.d("NFC_DEBUG", "Bloque 34 (Contrato) primer byte: ${String.format("%02X", firstByte)}")
                
                // Evaluamos con la nueva regla de prioridad: Subtipo > Prefijo
                val subType = contractData[8].toInt() and 0xFF
                Log.d("NFC_DEBUG", "S8 - Byte0: ${String.format("%02X", firstByte)}, Byte8: ${String.format("%02X", subType)}")

                cardType = when (subType) {
                    0x03, 0x04 -> com.jpm.transporttool.data.model.CardType.JOVEN
                    0x01 -> {
                        // El 0x01 es perfil base; en prefijo 58 es Fam. Numerosa, en el resto suele ser Normal
                        if (firstByte == 0x58) com.jpm.transporttool.data.model.CardType.FAMILIANUM
                        else com.jpm.transporttool.data.model.CardType.NORMAL
                    }
                    else -> {
                        // Fallback por prefijo si el subtipo es desconocido
                        when (firstByte) {
                            0x40 -> com.jpm.transporttool.data.model.CardType.JOVEN
                            0x58 -> com.jpm.transporttool.data.model.CardType.FAMILIANUM
                            0x18, 0x60 -> com.jpm.transporttool.data.model.CardType.NORMAL
                            else -> com.jpm.transporttool.data.model.CardType.UNKNOWN
                        }
                    }
                }
                Log.d("NFC_DEBUG", "Tipo detectado S8: ${cardType.name}")
            }

            val authS9 = mifare.authenticateSectorWithKeyA(SECTOR_9, NfcUtils.hexToBytes(KEY_A_SECTOR_9))
            Log.d("NFC_DEBUG", "Autenticación Sector 9: $authS9")
            
            if (authS9) {
                // Verificar también Bloque 36 en Sector 9 (es copia del 34)
                val contractData36 = mifare.readBlock(BLOCK_36)
                val firstByte36 = contractData36[0].toInt() and 0xFF
                Log.d("NFC_DEBUG", "Bloque 36 (S9) byte 0: ${String.format("%02X", firstByte36)}")
                
                if (cardType == com.jpm.transporttool.data.model.CardType.UNKNOWN) {
                    val subType36 = contractData36[8].toInt() and 0xFF
                    Log.d("NFC_DEBUG", "S9 - Byte0: ${String.format("%02X", firstByte36)}, Byte8: ${String.format("%02X", subType36)}")

                    cardType = when (subType36) {
                        0x03, 0x04 -> com.jpm.transporttool.data.model.CardType.JOVEN
                        0x01 -> {
                            if (firstByte36 == 0x58) com.jpm.transporttool.data.model.CardType.FAMILIANUM
                            else com.jpm.transporttool.data.model.CardType.NORMAL
                        }
                        else -> {
                            when (firstByte36) {
                                0x40 -> com.jpm.transporttool.data.model.CardType.JOVEN
                                0x58 -> com.jpm.transporttool.data.model.CardType.FAMILIANUM
                                0x18, 0x60 -> com.jpm.transporttool.data.model.CardType.NORMAL
                                else -> com.jpm.transporttool.data.model.CardType.UNKNOWN
                            }
                        }
                    }
                    Log.d("NFC_DEBUG", "Tipo detectado S9: ${cardType.name}")
                }

                val data = mifare.readBlock(BLOCK_37)
                val bal = NfcUtils.parseBalance(data)
                Log.d("NFC_DEBUG", "Saldo leído B37: $bal")

                // Detectar si el bloque 38 es igual al 37
                try {
                    val data38 = mifare.readBlock(BLOCK_38)
                    val bal38 = NfcUtils.parseBalance(data38)
                    Log.d("NFC_DEBUG", "Saldo leído B38: $bal38")

                    viewModel.isCardCorrupted = !data.contentEquals(data38)
                    if (viewModel.isCardCorrupted) {
                        Log.w("NFC_DEBUG", "¡AVISO! Datos de bloques 37 y 38 no coinciden")
                    }
                } catch (e: Exception) {
                    Log.e("NFC_DEBUG", "Error leyendo bloque 38 para verificación", e)
                    viewModel.isCardCorrupted = true
                }

                viewModel.focusedUid = uid
                
                // Buscar configuración existente por UID completo o por prefijo (ej. si se añadió manualmente)
                val existing = viewModel.savedCards.find { uid.startsWith(it.uidPrefix) }
                
                val detectedName = when(cardType) {
                    com.jpm.transporttool.data.model.CardType.NORMAL -> "Monedero Consorcio"
                    com.jpm.transporttool.data.model.CardType.FAMILIANUM -> "Familia Numerosa"
                    com.jpm.transporttool.data.model.CardType.JOVEN -> "Tarjeta Joven"
                    else -> null
                }

                // Decidimos si necesita actualización: si no existe, si el tipo cambió, o si el nombre es genérico y debe ser específico
                val isGenericName = existing?.name == "Tarjeta General" || existing?.name == "Monedero Consorcio"
                val needsUpdate = existing == null || 
                                 (cardType != com.jpm.transporttool.data.model.CardType.UNKNOWN && existing.type != cardType) ||
                                 (detectedName != null && isGenericName && existing.name != detectedName)

                if (needsUpdate) {
                    val finalName = if (existing != null && !isGenericName) {
                        existing.name // Respetar nombre personalizado si el usuario lo cambió
                    } else {
                        detectedName ?: existing?.name ?: "Tarjeta Desconocida"
                    }

                    Log.d("NFC_DEBUG", "Actualizando/Creando tarjeta: $finalName (Tipo: ${cardType.name})")
                    
                    viewModel.saveNewCard(
                        com.jpm.transporttool.data.model.TransportCard(
                            name = finalName,
                            uidPrefix = uid, // Guardamos el UID completo
                            keyB = existing?.keyB ?: "",
                            color = if (existing?.type != cardType) cardType.defaultColor.toArgb() else (existing?.color ?: cardType.defaultColor.toArgb()),
                            type = cardType
                        )
                    )
                    
                    // Si el prefijo era parcial (ej. "58"), eliminamos la configuración antigua para evitar duplicados
                    if (existing != null && existing.uidPrefix != uid) {
                        viewModel.deleteCardConfig(existing.uidPrefix)
                    }
                }

                viewModel.addToHistory(uid, bal)
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: e.javaClass.simpleName
            runOnUiThread { Toast.makeText(this, getString(R.string.error_message, errorMsg), Toast.LENGTH_SHORT).show() }
        } finally {
            mifare.close()
            viewModel.isReading = false
        }
    }

    private fun writeBalance(uid: String, amount: Float, keyBStr: String) {
        val tag = viewModel.lastDetectedTag ?: return run {
            Toast.makeText(this, getString(R.string.no_physical_card), Toast.LENGTH_SHORT).show()
        }
        if (NfcUtils.bytesToHex(tag.id).uppercase() != uid) return run {
            Toast.makeText(this, getString(R.string.wrong_card), Toast.LENGTH_SHORT).show()
        }
        if (keyBStr.isEmpty()) return run {
            Toast.makeText(this, getString(R.string.no_key_b_saved), Toast.LENGTH_SHORT).show()
        }
        val mifare = MifareClassic.get(tag) ?: return
        try {
            mifare.connect()
            Log.d("NFC_WRITE", "Intentando autenticar Sector 9 con Key B: $keyBStr")
            val auth = mifare.authenticateSectorWithKeyB(SECTOR_9, NfcUtils.hexToBytes(keyBStr))
            Log.d("NFC_WRITE", "Autenticación Key B: $auth")
            
            if (auth) {
                val valueBlock = NfcUtils.buildValueBlock((amount * 200).toInt())
                
                Log.d("NFC_WRITE", "Escribiendo bloque 37: ${NfcUtils.bytesToHex(valueBlock)}")
                mifare.writeBlock(BLOCK_37, valueBlock)
                
                Log.d("NFC_WRITE", "Escribiendo bloque 38 (backup): ${NfcUtils.bytesToHex(valueBlock)}")
                mifare.writeBlock(BLOCK_38, valueBlock)
                
                // Verificación inmediata (del bloque principal)
                val verifiedData = mifare.readBlock(BLOCK_37)
                if (verifiedData != null && verifiedData.size >= 16) {
                    val verifiedAmount = NfcUtils.parseBalance(verifiedData)
                    Log.d("NFC_WRITE", "Verificación - Saldo en tarjeta tras escribir: $verifiedAmount")
                }

                viewModel.finalAmount = amount
                viewModel.showSuccess = true
                viewModel.isWaitingToWrite = false
                viewModel.isCardCorrupted = false // Limpiar estado tras escritura exitosa
                viewModel.addToHistory(uid, amount.toDouble())
            } else {
                runOnUiThread { Toast.makeText(this, "Error de autenticación con Key B", Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: e.javaClass.simpleName
            Log.e("NFC_WRITE", "Error al escribir: $errorMsg", e)
            runOnUiThread { Toast.makeText(this, getString(R.string.error_message, errorMsg), Toast.LENGTH_SHORT).show() }
        } finally {
            mifare.close()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        @Suppress("DEPRECATION") val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        viewModel.lastDetectedTag = tag
        if (tag != null) {
            if (viewModel.isWaitingToWrite) {
                val cardCfg = viewModel.savedCards.find { viewModel.focusedUid.startsWith(it.uidPrefix) }
                writeBalance(viewModel.focusedUid, viewModel.finalAmount, cardCfg?.keyB ?: "")
            } else if (viewModel.isNormalizing) {
                val cardCfg = viewModel.savedCards.find { viewModel.focusedUid.startsWith(it.uidPrefix) }
                if (cardCfg != null && cardCfg.keyB.isNotEmpty()) {
                    // Re-leer saldo actual para normalizar
                    val mifare = MifareClassic.get(tag)
                    try {
                        mifare.connect()
                        if (mifare.authenticateSectorWithKeyA(SECTOR_9, NfcUtils.hexToBytes(KEY_A_SECTOR_9))) {
                            val data = mifare.readBlock(BLOCK_37)
                            val currentBal = NfcUtils.parseBalance(data)
                            mifare.close()
                            viewModel.isRepairSuccess = true
                            writeBalance(viewModel.focusedUid, currentBal.toFloat(), cardCfg.keyB)
                        }
                    } catch (e: Exception) {
                        Log.e("NFC_NORMALIZE", "Error al normalizar", e)
                    } finally {
                        viewModel.isNormalizing = false
                    }
                } else {
                    viewModel.isNormalizing = false
                    runOnUiThread { Toast.makeText(this, "Se necesita KEY B configurada para normalizar", Toast.LENGTH_SHORT).show() }
                }
            } else {
                readCardBalance(tag)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }
}
