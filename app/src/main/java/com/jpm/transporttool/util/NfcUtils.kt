package com.jpm.transporttool.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

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
}
