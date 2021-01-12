package me.trevi.navparser.lib

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

@Serializable
data class LocalTimeSerialDescriptor(val hour : Byte, val minute : Byte, val second : Byte)

object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor = LocalTimeSerialDescriptor.serializer().descriptor
    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeSerializableValue(serializer(),
            LocalTimeSerialDescriptor(
                value.hour.toByte(),
                value.minute.toByte(),
                value.second.toByte()))
    }
    override fun deserialize(decoder: Decoder): LocalTime {
        decoder.decodeSerializableValue(LocalTimeSerialDescriptor.serializer()).also {
            return LocalTime.of(it.hour.toInt(), it.minute.toInt(), it.second.toInt())
        }
    }
}

@Serializable
data class LocalDateSerialDescriptor(val day : Byte, val month : Byte, val year : Short)

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = LocalDateSerialDescriptor.serializer().descriptor
    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeSerializableValue(serializer(),
            LocalDateSerialDescriptor(
                value.dayOfMonth.toByte(),
                value.month.value.toByte(),
                value.year.toShort()))
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        decoder.decodeSerializableValue(LocalDateSerialDescriptor.serializer()).also {
            return LocalDate.of(it.year.toInt(), it.month.toInt(), it.day.toInt())
        }
    }
}

object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        Duration::class.java.name,
        PrimitiveKind.INT
    )
    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeInt((value.toMillis() / 1000).toInt())
    override fun deserialize(decoder: Decoder): Duration = Duration.ofSeconds(decoder.decodeInt().toLong())
}

@Serializable
data class BitmapSerialDescriptor(
    val width : Int,
    val height : Int,
    val hashCode : Int,
    val base64 : String)

object BitmapBase64Serializer : KSerializer<Bitmap> {
    override val descriptor: SerialDescriptor = BitmapSerialDescriptor.serializer().descriptor
    override fun serialize(encoder: Encoder, value: Bitmap) {
        val stream = ByteArrayOutputStream()
        value.compress(Bitmap.CompressFormat.PNG, 90, stream)
        stream.toByteArray().also { byteArray ->
            BitmapSerialDescriptor(value.width, value.height, value.hashCode(),
                Base64.encodeToString(byteArray, Base64.DEFAULT)).also {
                encoder.encodeSerializableValue(serializer(), it)
            }
        }
    }
    override fun deserialize(decoder: Decoder): Bitmap {
        decoder.decodeSerializableValue(BitmapSerialDescriptor.serializer()).also {
            Base64.decode(it.base64, 0).also { byteArray ->
                return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            }
        }
    }
}
