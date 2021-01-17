/* -*- mode: C++; c-basic-offset: 4; indent-tabs-mode: nil; -*- */
/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Copyright (c) 2020 Marco Trevisan <mail@trevi.me>
 */

package me.trevi.navparser.lib

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
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
    val height : Int)
{
    var base64 : String? = null
    var hashCode : Int = 0
    @ByteString
    var byteArray : ByteArray? = null

    constructor(bitmap : Bitmap, useBase64 : Boolean)
            : this(bitmap.width, bitmap.height) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        stream.toByteArray().also {
            hashCode = it.contentHashCode()
            if (useBase64)
                base64 = Base64.encodeToString(it, Base64.DEFAULT)
            else
                byteArray = it
        }
    }

    fun decode(fromBase64 : Boolean) : Bitmap {
        (if (fromBase64) Base64.decode(base64, 0) else byteArray!!).also { byteArray ->
            return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        }
    }
}

object BitmapSerializer : KSerializer<Bitmap> {
    override val descriptor: SerialDescriptor = BitmapSerialDescriptor.serializer().descriptor
    override fun serialize(encoder: Encoder, value: Bitmap) =
        encoder.encodeSerializableValue(serializer(),
            BitmapSerialDescriptor(value, useBase64 = encoder is JsonEncoder))
    override fun deserialize(decoder: Decoder): Bitmap =
        decoder.decodeSerializableValue(BitmapSerialDescriptor.serializer()).decode(
            decoder is JsonDecoder)
}

/* Generic serializer for "Any?" value */

@Serializable
data class AnyValueSurrogate(
    val type : String,
    @Contextual
    val value : Any?
)

@Serializable
object NoneType

fun getSerializerForType(type: KType) : KSerializer<Any?> {
    return try {
        serializer(type)
    } catch (e: SerializationException) {
        @Suppress("UNCHECKED_CAST")
        when (type) {
            LocalDate::class.starProjectedType -> LocalDateSerializer as KSerializer<Any?>
            LocalTime::class.starProjectedType -> LocalTimeSerializer as KSerializer<Any?>
            Duration::class.starProjectedType -> DurationSerializer as KSerializer<Any?>
            Bitmap::class.starProjectedType -> BitmapSerializer as KSerializer<Any?>
            else -> throw(e)
        }
    }
}

object AnyValueSerializer : KSerializer<Any?> {
    override val descriptor : SerialDescriptor = AnyValueSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Any?) {
        if (value != null) {
            val valueClass = value::class
            val valueType = valueClass.starProjectedType
            val valueSerializer = getSerializerForType(valueType)

            if (encoder is JsonEncoder && isTypePrimitive(valueType)) {
                encoder.encodeJsonElement(Json.encodeToJsonElement(valueSerializer, value))
            } else {
                /* Would be nice to use valueSerializer.descriptor.serialName,
                 * but how to deserialize that to a type? */
                val composite = encoder.beginCollection(descriptor, 2)
                composite.encodeSerializableElement(descriptor, 0, serializer(), valueClass.java.name)
                composite.encodeSerializableElement(descriptor, 1, valueSerializer, value)
                composite.endStructure(descriptor)
            }
        } else {
            if (encoder is JsonEncoder) {
                encoder.encodeJsonElement(JsonNull)
            } else {
                val composite = encoder.beginCollection(descriptor, 2)
                composite.encodeSerializableElement(descriptor, 1, serializer<NoneType?>(), null)
                composite.endStructure(descriptor)
            }
        }
    }

    private fun isTypePrimitive(type : KType) : Boolean {
        /* This can be replaced when using experimental API (via @ExperimentalSerializationApi) with:
         *  valueSerializer.descriptor.kind is PrimitiveKind */
        if (type.isSubtypeOf(Number::class.starProjectedType))
            return true

        if (type.isSubtypeOf(String::class.starProjectedType))
            return true

        if (type.isSubtypeOf(Boolean::class.starProjectedType))
            return true

        return false
    }

    private fun getSerializerForTypeName(strType : String) : KSerializer<*> {
        return try {
            serializer(Class.forName(strType).kotlin.starProjectedType)
        } catch (e: ClassNotFoundException) {
            throw SerializationException(e.message)
        }
    }

    override fun deserialize(decoder: Decoder): Any? {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonNull)
                return null

            if (element is JsonPrimitive) {
                if (element.isString)
                    return element.content

                return try {
                    element.boolean
                } catch (e: Throwable) {
                    try {
                        element.long
                    } catch (e: Throwable) {
                        element.double
                    }
                }
            } else if (element is JsonObject && "type" in element && "value" in element) {
                element["type"].also { type ->
                    if (type is JsonPrimitive && type.isString) {
                        val valueSerializer = getSerializerForTypeName(type.content)
                        element["value"].also { value ->
                            if (value is JsonObject)
                                return Json.decodeFromJsonElement(valueSerializer, value)
                        }
                    }
                }
            }
            throw SerializationException("Invalid Json element $element")
        } else {
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

            getSerializerForTypeName(strType).also { serializer ->
                composite.decodeSerializableElement(descriptor, index, serializer).also {
                    composite.endStructure(descriptor)
                    return it
                }
            }
        }
    }
}

/* Two-way Serializer for a container of Map<String, Any?> values without a root element */

@Serializable(with = AnySerializableValueSerializer::class)
data class AnySerializableValue(val value : Any?)

object AnySerializableValueSerializer : KSerializer<AnySerializableValue> {
    override val descriptor: SerialDescriptor = AnyValueSurrogate.serializer().descriptor
    override fun serialize(encoder: Encoder, value: AnySerializableValue) =
        AnyValueSerializer.serialize(encoder, value.value)
    override fun deserialize(decoder: Decoder): AnySerializableValue =
        AnySerializableValue(AnyValueSerializer.deserialize(decoder))
}

typealias SerializableMap = MutableMap<String, AnySerializableValue>

object MapStringAnySerializer : KSerializer<MapStringAny> {
    override val descriptor : SerialDescriptor = serializer<SerializableMap>().descriptor
    override fun serialize(encoder: Encoder, value: MapStringAny) {
        val entries : SerializableMap = mutableMapOf()
        value.entries.forEach { entries[it.key] = AnySerializableValue(it.value) }
        encoder.encodeSerializableValue(serializer(), entries)
    }
    override fun deserialize(decoder: Decoder): MapStringAny {
        val map = mutableMapOf<String, Any?>()
        decoder.decodeSerializableValue(serializer<SerializableMap>()).forEach {
            map[it.key] = it.value.value
        }
        return MapStringAny(map)
    }
}

sealed class AbstractMapStringAnySerializable {
    abstract val entries : Map<String, Any?>
}

@Serializable(with = MapStringAnySerializer::class)
data class MapStringAny(override val entries: Map<String, Any?> = emptyMap()) :
    AbstractMapStringAnySerializable()

/* Serializer (only) for Any type when included as member of other classes (untyped)
 * Can be used in cases such as:
 *   AnySerializableOnly(val value : @Serializable(with = AnySerializableOnlyValueSerializer::class) Any?)
 */

@Serializable
abstract class AnySerializableOnlyValue : Any()

object AnySerializableOnlyValueSerializer : KSerializer<Any?> {
    override val descriptor : SerialDescriptor = AnySerializableOnlyValue.serializer().descriptor
    override fun serialize(encoder: Encoder, value: Any?) {
        if (value != null)
            encoder.encodeSerializableValue(getSerializerForType(value::class.starProjectedType), value)
        else
            encoder.encodeSerializableValue(serializer<NoneType?>(), null)
    }
    override fun deserialize(decoder: Decoder): Any? {
        /* We don't care about being able to deserialize for this type */
        throw NotImplementedError("Deserialization not implemented for Any type")
    }
}

/* Serializer only for a container of Map<String, Any?> values without a root element */

object MapStringAnyValueSerializer : KSerializer<Map<String, Any?>> {
    override val descriptor : SerialDescriptor = mapSerialDescriptor(
        serializer<String>().descriptor,
        AnySerializableOnlyValueSerializer.descriptor,
    )
    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        val composite = encoder.beginCollection(descriptor, value.size)
        var index = 0
        value.forEach { (k, v) ->
            composite.encodeSerializableElement(descriptor, index++, serializer(), k)
            if (v != null) {
                composite.encodeSerializableElement(descriptor, index++, getSerializerForType(v::class.starProjectedType), v)
            } else {
                composite.encodeSerializableElement(descriptor, index++, serializer<NoneType?>(), null)
            }
        }
        composite.endStructure(descriptor)
    }
    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        throw NotImplementedError()
    }
}

object MapStringAnySerializableOnlySerializer : KSerializer<MapStringAnySerializableOnly> {
    override val descriptor : SerialDescriptor = MapStringAnyValueSerializer.descriptor
    override fun serialize(encoder: Encoder, value: MapStringAnySerializableOnly) =
        MapStringAnyValueSerializer.serialize(encoder, value.entries)
    override fun deserialize(decoder: Decoder): MapStringAnySerializableOnly =
        MapStringAnySerializableOnly(MapStringAnyValueSerializer.deserialize(decoder))
}

@Serializable(with = MapStringAnySerializableOnlySerializer::class)
data class MapStringAnySerializableOnly(override val entries: Map<String, Any?> = emptyMap()) :
    AbstractMapStringAnySerializable()
