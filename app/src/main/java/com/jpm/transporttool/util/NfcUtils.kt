package com.jpm.transporttool.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Log

object NfcUtils {
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
    
    fun parseBalance(data: ByteArray?): Double {
        if (data == null || data.size < 4) return 0.0
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int / 200.0
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
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
