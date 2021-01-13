/* -*- mode: C++; c-basic-offset: 4; indent-tabs-mode: nil; -*- */
/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Copyright (c) 2020 Marco Trevisan <mail@trevi.me>
 */

package me.trevi.navparser.lib

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Parcelable
import android.text.format.DateFormat.getTimeFormat
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.*
import java.util.*
import kotlin.reflect.KProperty
import kotlin.reflect.KVisibility

enum class DistanceUnit {
    KM,
    M,
    FT,
    MI,
    INVALID,
}

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Mutable

@Parcelize @Serializable
data class NavigationDistance(
    val localeString: String? = null,
    val distance: Double = -1.0,
    val unit: DistanceUnit = DistanceUnit.INVALID,
) : Parcelable

@Parcelize @Serializable
data class NavigationDirection(
    var localeString: String? = null,
    var localeHtml: String? = null,
    var navigationDistance: NavigationDistance? = null,
) : Parcelable

@Parcelize @Serializable
data class NavigationDuration(
    val localeString: String? = null,
    @Serializable(with = DurationSerializer::class)
    val duration: Duration = Duration.ZERO,
) : Parcelable

@Parcelize @Serializable
data class NavigationTime(
    val localeString: String? = null,
    @Serializable(with = LocalTimeSerializer::class)
    val time: LocalTime? = null,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate? = null,
    val duration: NavigationDuration? = null,
) : Parcelable

@Parcelize @Serializable
data class NavigationIcon(
    @Serializable(with = BitmapSerializer::class)
    val bitmap: Bitmap? = null,
    ) : Parcelable, AutoCloseable {
    override fun equals(other: Any?): Boolean {
        return if (other !is NavigationIcon || bitmap !is Bitmap)
            super.equals(other)
        else bitmap.sameAs(other.bitmap)
    }

    override fun hashCode(): Int {
        return bitmap?.hashCode() ?: 0
    }

    override fun close() {
        bitmap?.recycle()
    }
}

@Parcelize @Serializable
data class NavigationTimestamp(
    @Mutable
    var timestamp : Long = 0,
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        /* We don't care about changes on the timestamp, so we always return true if same type */
        return other is NavigationTimestamp
    }

    override fun hashCode(): Int {
        return timestamp.hashCode()
    }
}

typealias NavigationDataMap = MapStringAny

@Parcelize @Serializable
data class NavigationData(
    var isRerouting: Boolean = false,
    var canStop: Boolean = false,
    var actionIcon: NavigationIcon = NavigationIcon(),
    var nextDirection: NavigationDirection = NavigationDirection(),
    var remainingDistance: NavigationDistance = NavigationDistance(),
    var eta: NavigationTime = NavigationTime(),
    var finalDirection : String? = null,
    @Mutable
    var postTime : NavigationTimestamp = NavigationTimestamp(),
) : Parcelable {
    fun isValid(): Boolean {
        return isRerouting ||
                (nextDirection.localeString != null &&
                        remainingDistance.localeString != null &&
                        eta.localeString != null)
    }

    fun asMap() : NavigationDataMap {
        val map = emptyMap<String, Any?>().toMutableMap()

        this::class.members.forEach { m ->
            if (m is KProperty && m.visibility == KVisibility.PUBLIC && m.isFinal)
                map[m.name] = m.getter.call(this)
        }

        return NavigationDataMap(map)
    }

    fun diffMap(other: NavigationData) : Map<String, Any?> {
        val diff = emptyMap<String, Any?>().toMutableMap()

        this::class.members.forEach { m ->
            if (m is KProperty && m.visibility == KVisibility.PUBLIC && m.isFinal &&
                    m.annotations.find { it is Mutable } == null) {
                m.getter.call(other).also {
                    if (it != m.getter.call(this))
                        diff[m.name] = it
                }
            }
        }

        return diff
    }

    fun diff(other : NavigationData) : NavigationDataMap = NavigationDataMap(diffMap(other))
}

data class LocaleInfo(val locale : Locale, val isRtl : Boolean = false)

private fun getCurrentLocale(cx : Context) : LocaleInfo {
    val configuration = cx.resources?.configuration

    if (configuration == null)
        return LocaleInfo(Locale.ENGLISH, false)

    return LocaleInfo(
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        },
        configuration.layoutDirection == Configuration.SCREENLAYOUT_LAYOUTDIR_RTL
    )
}

fun unitStringToDistanceUnit(unit: String) : DistanceUnit {
    if (unit == "km" || unit == "км" || unit == "كم") {
        return DistanceUnit.KM
    } else if (unit == "m" || unit == "м" || unit == "متر") {
        return DistanceUnit.M
    } else if (unit == "mi") {
        return DistanceUnit.MI
    } else if (unit == "ft") {
        return DistanceUnit.FT
    }

    throw(UnknownFormatConversionException("Impossible to parse a distance unit ${unit}"))
}

fun parseNavigationDistance(cx: Context, distance: String) : NavigationDistance {
    val distanceParts = distance.split("\\p{Space}+".toRegex())
    val localeInfo = getCurrentLocale(cx)

    if (distanceParts.size != 2)
        throw(UnknownFormatConversionException("Impossible to parse navigation distance ${distance}"))

    val distanceIndex = if (localeInfo.isRtl) 1 else 0
    val distancePart = distanceParts[distanceIndex]
    val unitPart = distanceParts[(distanceIndex + 1) % distanceParts.size]
    val distanceValue = NumberFormat.getInstance(getCurrentLocale(cx).locale).parse(distancePart)

    if (distanceValue == null)
        throw(UnknownFormatConversionException("Impossible to parse navigation distance ${distancePart}"))

    return NavigationDistance(
        distance,
        distanceValue.toDouble(),
        unitStringToDistanceUnit(unitPart)
    )
}

fun timeParser(cx: Context, time: String) : LocalTime {
    val parsedDate = getTimeFormat(cx).parse(time)

    if (parsedDate == null)
        throw(UnknownFormatConversionException("Impossible to parse navigation time ${time}"))

    Calendar.getInstance(getCurrentLocale(cx).locale).also {
        return try {
            /* We don't care about zones here, as it's the local one */
            it.time = parsedDate
            LocalTime.of(it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE), it.get(Calendar.SECOND))
        } catch (e : Exception) {
            throw(UnknownFormatConversionException("Impossible to parse navigation distance ${time}: $e"))
        }
    }
}

fun timeDurationParser(cx: Context, duration: String) : Duration {
    val localeInfo = getCurrentLocale(cx)
    val format = SimpleDateFormat("HH:mm Z", localeInfo.locale)
    val parsedDate = format.parse("${duration} +0000")

    if (parsedDate == null)
        throw(UnknownFormatConversionException("Impossible to parse navigation distance ${duration}"))

    return try {
        Duration.between(Instant.EPOCH, parsedDate.toInstant())
    } catch (e : Exception) {
        throw(UnknownFormatConversionException("Impossible to parse navigation duration ${duration}"))
    }
}
