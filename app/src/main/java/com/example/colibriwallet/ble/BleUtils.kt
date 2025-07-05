package com.example.colibriwallet.ble

import java.util.UUID

/**
 * Converts a 16-bit or 32-bit UUID to the full 128-bit form using the Colibri base UUID.
 */
fun shortUuidTo128(uuid: Int): UUID {
    val base = UUID.fromString("31415926-5358-9793-2384-626433832795")
    val msb = base.mostSignificantBits
    val lsb = base.leastSignificantBits
    val short = uuid.toLong() and 0xFFFFFFFFL
    return UUID(msb + (short shl 32), lsb)
}
