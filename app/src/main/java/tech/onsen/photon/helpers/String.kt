package tech.onsen.photon.helpers

import java.nio.charset.Charset

private val UTF8_CHARSET = Charset.forName("UTF-8")

fun String.encodeToByteArray(): ByteArray{
    return this.toByteArray(UTF8_CHARSET)
}