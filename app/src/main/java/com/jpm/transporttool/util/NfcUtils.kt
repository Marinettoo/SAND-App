package com.jpm.transporttool.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object NfcUtils {
    // Clave Maestra de 16 bytes para 3DES (Generada para este proyecto)
    private const val MASTER_KEY_HEX = "7D4B2A5F1E9C8D3A6B0F2E4A9D8C7B6A"
    fun hexToBytes(s: String): ByteArray {
        val hex = if (s.length % 2 != 0) "0$s" else s
        val data = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }

    fun buildValueBlock(units: Int): ByteArray {
        val block = ByteArray(16)
        val v = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(units).array()
        val vi = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(units.inv()).array()
        System.arraycopy(v, 0, block, 0, 4)
        System.arraycopy(vi, 0, block, 4, 4)
        System.arraycopy(v, 0, block, 8, 4)
        block[12] = 0x00.toByte()
        block[13] = 0xFF.toByte()
        block[14] = 0x00.toByte()
        block[15] = 0xFF.toByte()
        return block
    }
    
    /**
     * Extrae el contador de transacciones del Bloque 36 (Bytes 4-7)
     */
    fun getTransactionCounter(block36: ByteArray): Int {
        if (block36.size < 8) return 0
        return ((block36[4].toInt() and 0xFF) shl 24) or
                ((block36[5].toInt() and 0xFF) shl 16) or
                ((block36[6].toInt() and 0xFF) shl 8) or
                (block36[7].toInt() and 0xFF)
    }

    /**
     * Construye el nuevo Bloque 36 con el contador incrementado y el MAC actualizado.
     */
    fun buildBlock36(oldBlock: ByteArray, newCounter: Int, newMac: ByteArray): ByteArray {
        val newBlock = oldBlock.copyOf()
        // Actualizar contador (Bytes 4-7 en Big Endian)
        newBlock[4] = ((newCounter shr 24) and 0xFF).toByte()
        newBlock[5] = ((newCounter shr 16) and 0xFF).toByte()
        newBlock[6] = ((newCounter shr 8) and 0xFF).toByte()
        newBlock[7] = (newCounter and 0xFF).toByte()
        
        // Actualizar MAC (Bytes 9-15)
        if (newMac.size == 7) {
            System.arraycopy(newMac, 0, newBlock, 9, 7)
        }
        return newBlock
    }

    /**
     * Calcula el MAC del Bloque 36 usando 3DES diversificado.
     * 1. Diversifica la clave con el UID.
     * 2. Prepara el mensaje (Saldo + Contador).
     * 3. Cifra y extrae los 7 bytes de la firma.
     */
    fun calculateIndraMAC(uid: String, balanceUnits: Int, counter: Int): ByteArray {
        try {
            // 1. Diversificación de Clave (Card Unique Key)
            // En Indra suele ser: 3DES(MasterKey, UID padded)
            val uidBytes = hexToBytes(uid)
            val cardKey = diversifyKey(MASTER_KEY_HEX, uidBytes)

            // 2. Construcción del mensaje a firmar (8 bytes para un bloque DES)
            // Estructura común: [Saldo (4b)] [Contador (4b)]
            val buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            buffer.putInt(balanceUnits)
            buffer.putInt(counter)
            val message = buffer.array()

            // 3. Cifrado 3DES (Modo CBC-MAC o Simple ECB para el último bloque)
            // La clave debe tener 16 o 24 bytes para DESede.
            val finalKey = if (cardKey.size == 8) cardKey + cardKey else cardKey
            val secretKey = SecretKeySpec(finalKey, "DESede")
            val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val fullCiphertext = cipher.doFinal(message)
            
            // Extraemos los 7 bytes para el Bloque 36
            val mac = ByteArray(7)
            System.arraycopy(fullCiphertext, 0, mac, 0, 7)
            
            return mac
        } catch (e: Exception) {
            Log.e("CRYPTO_ERROR", "Error calculando MAC", e)
            return ByteArray(7)
        }
    }

    /**
     * Diversifica la Master Key usando el UID de la tarjeta para obtener una clave de 16 bytes.
     */
    private fun diversifyKey(masterKeyHex: String, uid: ByteArray): ByteArray {
        val masterKeyBytes = hexToBytes(masterKeyHex)
        // Para 3DES necesitamos 16 o 24 bytes en la clave maestra.
        val secretKey = SecretKeySpec(masterKeyBytes, "DESede")
        val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        // Para obtener una clave diversificada de 16 bytes (3DES), ciframos 16 bytes de datos.
        // Estructura común: [UID padded (8b)] + [UID inverted padded (8b)]
        val data = ByteArray(16)
        val paddedUid = ByteArray(8)
        System.arraycopy(uid, 0, paddedUid, 0, uid.size.coerceAtMost(8))
        
        System.arraycopy(paddedUid, 0, data, 0, 8)
        for (i in 0..7) {
            data[i + 8] = (paddedUid[i].toInt() xor 0xFF).toByte()
        }
        
        return cipher.doFinal(data)
    }

    /**
     * Verifica si la firma (MAC) del Bloque 36 es válida para el estado actual.
     */
    fun verifyBlock36Mac(uid: String, balanceUnits: Int, block36: ByteArray): Boolean {
        if (block36.size < 16) return false
        val currentCounter = getTransactionCounter(block36)
        val expectedMac = calculateIndraMAC(uid, balanceUnits, currentCounter)
        val actualMac = ByteArray(7)
        System.arraycopy(block36, 9, actualMac, 0, 7)
        return expectedMac.contentEquals(actualMac)
    }

    fun parseBalance(data: ByteArray?): Double {
        if (data == null || data.size < 4) return 0.0
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int / 200.0
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    fun bytesToHexSpaced(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    /**
     * Verifica si un bloque de 16 bytes sigue la estructura de un Value Block de Mifare Classic.
     */
    fun validateValueBlockStructure(data: ByteArray): Boolean {
        if (data.size != 16) return false
        // El valor se repite en 0-3 y 8-11, e invertido en 4-7
        for (i in 0..3) {
            if (data[i] != data[i + 8]) return false
            if (data[i] != (data[i + 4].toInt().inv() and 0xFF).toByte()) return false
        }
        // La dirección se repite en 12 y 14, e invertida en 13 y 15
        if (data[12] != data[14]) return false
        if (data[12] != (data[13].toInt().inv() and 0xFF).toByte()) return false
        if (data[12] != (data[15].toInt().inv() and 0xFF).toByte()) return false
        return true
    }

    /**
     * Convierte la fecha interna del Consorcio (Indra) a Unix Epoch.
     * Según la especificación:
     * - Bytes 0-1: Días transcurridos desde el 01/01/1999 (Little Endian).
     * - Bytes 2-3: Minutos transcurridos desde la medianoche (Little Endian).
     */
    fun parseConsorcioDate(data: ByteArray, blockId: Int = 0): Long {
        return try {
            // Escudo hardware para evitar ArrayIndexOutOfBounds
            if (data.size < 6) return System.currentTimeMillis()

            // 1. FECHA: Bytes 2 y 3 leídos en BIG ENDIAN (Contador de Días)
            val daysBigEndian = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)

            // 2. HORA: Bytes 4 y 5 leídos en LITTLE ENDIAN (Unidades de 2 segundos)
            val timeLittleEndian = (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)

            // --- CALIBRACIÓN DE LA PIEDRA ROSETTA ---
            // Sabemos empíricamente que el día 53798 es el 09/04/2026
            val anchorDateMs = 1775692800000L // 09/04/2026 00:00:00 UTC
            val anchorDays = 53798

            // Calculamos la diferencia de días respecto a nuestro ancla
            val daysDifference = daysBigEndian - anchorDays

            // Convertimos a milisegundos
            val daysInMs = daysDifference.toLong() * 24 * 60 * 60 * 1000L
            val secondsInMs = timeLittleEndian.toLong() * 2000L // Multiplicamos por 2 secs

            // Timestamp final
            val finalTimestamp = anchorDateMs + daysInMs + secondsInMs

            Log.v("NFC_DATES", "Bloque $blockId -> Días: $daysBigEndian | Unidades Tiempo: $timeLittleEndian")

            finalTimestamp
        } catch (e: Exception) {
            Log.e("NfcManager", "Error parseando fecha Consorcio", e)
            System.currentTimeMillis()
        }
    }
}
