package me.trevi.navparser.lib

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "LocalTime",
        PrimitiveKind.INT
    )
    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeInt(value.toSecondOfDay())
    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.ofSecondOfDay(
        decoder.decodeInt().toLong()
    )
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "LocalDate",
        PrimitiveKind.LONG
    )
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeLong(value.toEpochDay())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.ofEpochDay(decoder.decodeLong())
}

object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "Duration",
        PrimitiveKind.LONG
    )
    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeLong(value.toMillis())
    override fun deserialize(decoder: Decoder): Duration = Duration.ofMillis(decoder.decodeLong())
}

object BitmapBase64Serializer : KSerializer<Bitmap> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "Bitmap",
        PrimitiveKind.STRING
    )
    override fun serialize(encoder: Encoder, value: Bitmap) {
        val stream = ByteArrayOutputStream()
        value.compress(Bitmap.CompressFormat.PNG, 90, stream)
        encoder.encodeString(Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT))
    }
    override fun deserialize(decoder: Decoder): Bitmap {
        val decoded = decoder.decodeString()
        return BitmapFactory.decodeByteArray(Base64.decode(decoded, 0), 0, decoded.length)
    }
}
