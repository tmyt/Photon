package tech.onsen.photon.helpers

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

private val UTF8_CHARSET = Charset.forName("UTF-8")

fun ByteArray.dump(length: Int = 1024): String {
    return this.take(length)
        .map { it.toUByte().toString(16).padStart(2, '0') }
        .joinToString(" ")
}

fun ByteArray.long(offset: Int): Long{
    return ByteBuffer.wrap(this, offset, 8).order(ByteOrder.LITTLE_ENDIAN).long
}

fun ByteArray.int(offset: Int): Int {
    return ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
}

fun ByteArray.short(offset: Int): Short {
    return ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short
}

fun ByteArray.decodeToString(): String {
    var index = this.indexOfFirst { it == 0.toByte() }
    if (index < 0) index = this.size
    return this.decodeToString(0, index)
}

fun ByteArray.decodeToString(from: Int, to: Int): String {
    return String(this, from, to - from, UTF8_CHARSET)
}