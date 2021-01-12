package me.trevi.navparser.lib

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.reflect.full.starProjectedType

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
    val base64 : String?,
    val byteArray : ByteArray?)

object BitmapSerializer : KSerializer<Bitmap> {
    override val descriptor: SerialDescriptor = BitmapSerialDescriptor.serializer().descriptor
    override fun serialize(encoder: Encoder, value: Bitmap) {
        val stream = ByteArrayOutputStream()
        val usingJson = encoder.javaClass.interfaces.find {
            it.name == "kotlinx.serialization.json.JsonEncoder" } != null
        value.compress(Bitmap.CompressFormat.PNG, 90, stream)
        stream.toByteArray().also { byteArray ->
            BitmapSerialDescriptor(value.width, value.height, value.hashCode(),
                if (usingJson) Base64.encodeToString(byteArray, Base64.DEFAULT) else null,
                if (!usingJson) byteArray else null).also {
                encoder.encodeSerializableValue(serializer(), it)
            }
        }
    }
    override fun deserialize(decoder: Decoder): Bitmap {
        val usingJson = decoder.javaClass.interfaces.find {
            it.name == "kotlinx.serialization.json.JsonDecoder" } != null
        decoder.decodeSerializableValue(BitmapSerialDescriptor.serializer()).also {
            val byteArray = if (usingJson) Base64.decode(it.base64, 0) else it.byteArray!!
            return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        }
    }
}

@Serializable
data class AnyValueSurrogate(
    val type : String,
    @Contextual
    val value : Any?
)

@Serializable
object NoneType

object AnyValueSerializer : KSerializer<Any?> {
    override val descriptor : SerialDescriptor = AnyValueSurrogate.serializer().descriptor
    override fun serialize(encoder: Encoder, value: Any?) {
        val strType = if (value != null) value::class.java.name else NoneType::class.java.name
        val composite = encoder.beginCollection(descriptor, 2)
        composite.encodeStringElement(descriptor, 0, strType)
        if (value != null)
            composite.encodeSerializableElement(descriptor, 1, serializer(value::class.starProjectedType), value)
        else
            composite.encodeSerializableElement(descriptor, 1, serializer<NoneType?>(), null)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Any? {
        val composite = decoder.beginStructure(descriptor)
        var index = composite.decodeElementIndex(descriptor)
        if (index == CompositeDecoder.DECODE_DONE)
            return null

        val strType = composite.decodeStringElement(descriptor, index)
        if (strType.isEmpty())
            throw SerializationException("Unknown serialization type")

        index = composite.decodeElementIndex(descriptor).also {
            if (it != index + 1)
                throw SerializationException("Unexpected element index!")
        }

        val valSerializer = try {
            serializer(Class.forName(strType).kotlin.starProjectedType)
        } catch (e: ClassNotFoundException) {
            throw SerializationException(e.message)
        }
        val value = composite.decodeSerializableElement(descriptor, index, valSerializer)
        composite.endStructure(descriptor)

        return value
    }
}
