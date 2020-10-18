package tech.onsen.photon.helpers

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun Int.asBytes(): ByteArray {
    return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(this).array()
}

fun Short.asBytes(): ByteArray {
    return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(this).array()
}

fun Int.asCollection(): Collection<Byte> {
    return this.asBytes().toList()
}

fun Short.asCollection(): Collection<Byte> {
    return this.asBytes().toList()
}