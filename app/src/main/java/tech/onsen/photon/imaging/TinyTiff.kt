package tech.onsen.photon.imaging

import android.util.Log
import tech.onsen.photon.helpers.decodeToString
import tech.onsen.photon.helpers.int
import tech.onsen.photon.helpers.short
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TinyTiff(tiff: ByteArray) {
    companion object {
        val emptyTiff = TinyTiff(arrayOf<Byte>(0, 0, 0, 0, 0, 0, 0, 0).toByteArray())

        private val formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

        private fun parse(tiff: ByteArray): List<IFD> {
            val ifdOffset = tiff.int(4)
            return parse(tiff, ifdOffset)
        }

        private fun parse(tiff: ByteArray, ifdOffset: Int): List<IFD> {
            val num = tiff.short(ifdOffset)
            return (0 until num).flatMap {
                val offset = ifdOffset + 2 + it * 12
                val tag = tiff.short(offset + 0x00)
                val type = tiff.short(offset + 0x02)
                val count = tiff.int(offset + 0x04)
                val size = dataSize(type, count) ?: return@flatMap emptyList<IFD>()
                val head = when (size <= 4) {
                    true -> offset + 0x08
                    false -> tiff.int(offset + 0x08)
                }
                if (size < 0 || head + size > tiff.size) return@flatMap emptyList<IFD>()
                val data = tiff.copyOfRange(head, head + size)
                if (tag == KnownTags.Exif.value || tag == KnownTags.GPS.value) {
                    return@flatMap parse(tiff, data.int(0))
                }
                return@flatMap listOf(IFD(tag, type, count, data))
            }
        }

        private fun dataSize(type: Short, count: Int): Int? {
            return when (type.toInt()) {
                1 -> count
                2 -> count
                3 -> 2 * count
                4 -> 4 * count
                5 -> 8 * count
                7 -> count
                9 -> 4 * count
                10 -> 8 * count
                else -> {
                    Log.d("TinyTiff", "Invalid DataType: " + type.toInt())
                    return null
                }
            }
        }
    }

    val ifd: List<IFD>

    init {
        ifd = parse(tiff)
    }

    data class IFD(
        val tag: Short,
        val type: Short,
        val length: Int,
        val data: ByteArray
    ) {
        override fun toString(): String {
            return "IFD(tag=${tag.toUShort()}, type=${type}, count=${length}, data=" + dumpData()
        }

        private fun dumpData(): String {
            if (length == 1 || type.toInt() == 2) {
                return dumpData(0)
            } else {
                return "[${
                    (0.until(length)).map {
                        when (type.toInt()) {
                            1 -> dumpData(it)
                            3 -> dumpData(it * 2)
                            4, 9 -> dumpData(it * 4)
                            5, 10 -> dumpData(it * 8)
                            7 -> dumpData(it)
                            else -> throw java.lang.IllegalArgumentException()
                        }
                    }.joinToString(", ")
                }]"
            }
        }

        private fun dumpData(offset: Int): String {
            return when (type.toInt()) {
                1 -> data[offset].toUByte().toString()
                2 -> data.decodeToString()
                3 -> data.short(offset).toUShort().toString()
                4 -> data.int(offset).toUInt().toString()
                5 -> "${data.int(offset).toUInt()} / ${data.int(offset + 4).toUInt()}"
                7 -> data[0].toString(16).padStart(2, '0')
                9 -> data.int(offset).toString()
                10 -> "${data.int(offset)} / ${data.int(offset + 4)}"
                else -> throw java.lang.IllegalArgumentException()
            }
        }
    }

    enum class IDFType(val value: Short) {
        Int8(1),
        String(2),
        Int16(3),
        Int32(4),
        Double(5),
    }

    enum class KnownTags(val value: Short) {
        DateTime(0x0132),
        Rating(0x4746),
        Exif(0x8769.toShort()),
        GPS(0x8825.toShort())
    }

    operator fun get(tag: Short): IFD? {
        return ifd.firstOrNull { it.tag == tag }
    }

    operator fun get(tag: KnownTags): IFD? {
        return ifd.firstOrNull { it.tag == tag.value }
    }

    fun getDateTime(): LocalDateTime {
        val text = this[KnownTags.DateTime]?.string()
        if (text == null || text == "Date/Time not set") return LocalDateTime.MIN
        return LocalDateTime.parse(text, formatter) ?: LocalDateTime.MIN
    }

    fun getRating(): Short {
        return this[KnownTags.Rating]?.short() ?: 0
    }

    private fun IFD.string(): String {
        return this.data.decodeToString().trim()
    }

    private fun IFD.short(): Short {
        return this.data.short(0)
    }
}