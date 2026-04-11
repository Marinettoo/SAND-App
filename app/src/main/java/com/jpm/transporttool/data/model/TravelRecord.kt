package com.jpm.transporttool.data.model

data class TravelRecord(
    val id: Int, // Número de bloque
    val isMetro: Boolean, // true si es Metro, false si es Autobús
    val isDiscounted: Boolean, // true si tarifa es 3C3C, false si es 5A5A
    val timestamp: Long, // Fecha y hora en formato Unix Epoch estándar
    val amountPaid: Double // Cantidad descontada (ej: 0.33 o 0.82)
)
