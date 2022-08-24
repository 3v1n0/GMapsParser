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

enum class DistanceUnit {
    KM,
    M,
    FT,
    MI,
    YD,
    INVALID,
}

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
) : Parcelable, MutableContent() {
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }
}

enum class NavigationMode {
    DRIVING,
    BICYCLING,
    TWO_WHEELER,
    WALKING,
}

enum class NavigationAvoidElement {
    TOLLS,
    HIGHWAYS,
    FERRIES,
    ;
}
typealias NavigationAvoid = Set<NavigationAvoidElement>

typealias NavigationDataMap = MapStringAny
typealias NavigationDataDiff = AbstractMapStringAnySerializable

@Parcelize @Serializable
data class NavigationData(
    var isRerouting: Boolean = false,
    var canStop: Boolean = false,
    var actionIcon: NavigationIcon = NavigationIcon(),
    var nextDirection: NavigationDirection = NavigationDirection(),
    var remainingDistance: NavigationDistance = NavigationDistance(),
    var eta: NavigationTime = NavigationTime(),
    var finalDirection : String? = null,
    var dataSource: String? = null,
    @Mutable
    var postTime : NavigationTimestamp = NavigationTimestamp(),
) : Parcelable, Introspectable, MutableContent() {
    fun isValid(): Boolean {
        return isRerouting ||
                (nextDirection.localeString != null &&
                        remainingDistance.localeString != null &&
                        eta.localeString != null)
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    fun diff(other : NavigationData) : NavigationDataMap = diffMap(other)
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
    } else if (unit == "yd") {
        return DistanceUnit.YD
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
