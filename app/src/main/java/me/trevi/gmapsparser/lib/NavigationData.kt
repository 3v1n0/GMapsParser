/* -*- mode: C++; c-basic-offset: 4; indent-tabs-mode: nil; -*- */
/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Copyright (c) 2020 Marco Trevisan <marco.trevisan@canonical.com>
 */

package me.trevi.gmapsparser.lib

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Parcelable
import android.text.format.DateFormat.getTimeFormat
import kotlinx.android.parcel.Parcelize
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.*
import java.util.*

enum class DistanceUnit {
    KM,
    M,
    FT,
    MI,
    INVALID,
}

@Parcelize
data class NavigationDistance(
    val localeString: String? = null,
    val distance: Double = -1.0,
    val unit: DistanceUnit = DistanceUnit.INVALID,
) : Parcelable

@Parcelize
data class NavigationDirection(
    var localeString: String? = null,
    var localeHtml: String? = null,
    var navigationDistance: NavigationDistance? = null,
) : Parcelable

@Parcelize
data class NavigationDuration(
    val localeString: String? = null,
    val duration: Duration = Duration.ZERO,
) : Parcelable

@Parcelize
data class NavigationTime(
    val localeString: String? = null,
    val time: LocalTime? = null,
    val date: LocalDate? = null,
    val duration: NavigationDuration? = null,
) : Parcelable

@Parcelize
data class NavigationIcon(
    val bitmap: Bitmap? = null,
) :Parcelable {
    override fun equals(other: Any?): Boolean {
        return if (other !is NavigationIcon || bitmap !is Bitmap)
            super.equals(other)
        else bitmap.sameAs(other.bitmap)
    }
}

@Parcelize
data class NavigationData(
    var isRerouting: Boolean = false,
    var actionIcon: NavigationIcon = NavigationIcon(),
    var nextDirection: NavigationDirection = NavigationDirection(),
    var remainingDistance: NavigationDistance = NavigationDistance(),
    var eta: NavigationTime = NavigationTime(),
    var finalDirection : String? = null,
) : Parcelable {
    fun isValid(): Boolean {
        return isRerouting ||
                (nextDirection.localeString != null &&
                        remainingDistance.localeString != null &&
                        eta.localeString != null)
    }
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

    var distanceIndex = if (localeInfo.isRtl) 1 else 0
    var distancePart = distanceParts[distanceIndex]
    var unitPart = distanceParts[(distanceIndex + 1) % distanceParts.size]

    return NavigationDistance(
        distance,
        NumberFormat.getInstance(getCurrentLocale(cx).locale).parse(distancePart).toDouble(),
        unitStringToDistanceUnit(unitPart)
    )
}

fun timeParser(cx: Context, time: String) : LocalTime {
    var parsedDate = getTimeFormat(cx).parse(time)

    if (parsedDate == null)
        throw(UnknownFormatConversionException("Impossible to parse navigation time ${time}"))

    return try {
        /* We don't care about zones here, as it's the local one */
        LocalTime.of(parsedDate.hours, parsedDate.minutes)
    } catch (e : Exception) {
        throw(UnknownFormatConversionException("Impossible to parse navigation distance ${time}"))
    }
}

fun timeDurationParser(cx: Context, duration: String) : Duration {
    val localeInfo = getCurrentLocale(cx)
    val format = SimpleDateFormat("HH:mm Z", localeInfo.locale)
    var parsedDate = format.parse("${duration} +0000")

    if (parsedDate == null)
        throw(UnknownFormatConversionException("Impossible to parse navigation distance ${duration}"))

    return try {
        Duration.between(Instant.EPOCH, parsedDate.toInstant())
    } catch (e : Exception) {
        throw(UnknownFormatConversionException("Impossible to parse navigation duration ${duration}"))
    }
}