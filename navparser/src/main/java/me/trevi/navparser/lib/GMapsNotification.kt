/* -*- mode: C++; c-basic-offset: 4; indent-tabs-mode: nil; -*- */
/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Copyright (c) 2020 Marco Trevisan <mail@trevi.me>
 */

package me.trevi.navparser.lib

import android.app.Notification
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.service.notification.StatusBarNotification
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.view.children
import java.util.*

const val GMAPS_PACKAGE = "com.google.android.apps.maps"

private val SPLIT_REGEX = "\\p{Space}+\\p{Punct}+\\p{Space}+".toRegex()
private val ETA_REGEX = "\\p{Digit}{1,2}\\p{Punct}\\p{Digit}{2}(\\p{Space}+(AM|PM|ص|م))?".toRegex()

enum class ContentViewType {
    NORMAL,
    BIG,
    BEST,
}

class GMapsNotification(cx: Context, sbn: StatusBarNotification) : NavigationNotification(cx, sbn) {

    init {
        Log.d(TAG, "Importing $mNotification")

        val normalContent = getContentView(ContentViewType.NORMAL)
        if (normalContent != null)
            parseRemoteView(getRemoteViewGroup(normalContent))

        val bestContentView = getContentView(ContentViewType.BEST)
        if (bestContentView != normalContent)
            parseRemoteView(getRemoteViewGroup(bestContentView))

        if (!navigationData.isValid()) {
            Log.w(TAG, "Invalid navigation data: ${navigationData}")
            throw(Exception("Impossible to parse navigation notification"))
        }

        Log.d(TAG,"Parsing completed: ${navigationData}")
    }

    private fun getContentView(type : ContentViewType = ContentViewType.BEST) : RemoteViews? {
        if (type == ContentViewType.BIG || type == ContentViewType.BEST) {
            val remoteViews : RemoteViews? = if (Build.VERSION.SDK_INT >= 24) {
                Notification.Builder.recoverBuilder(mCx, mNotification).createBigContentView()
            } else {
                @Suppress("DEPRECATION")
                mNotification.bigContentView;
            }

            if (remoteViews != null || type == ContentViewType.BIG)
                return remoteViews
        }

        return if (Build.VERSION.SDK_INT >= 24) {
            Notification.Builder.recoverBuilder(mCx, mNotification).createContentView()
        } else {
            @Suppress("DEPRECATION")
            mNotification.contentView;
        }
    }

    private fun getRemoteViewGroup(remoteViews : RemoteViews?) : ViewGroup {
        if (remoteViews == null) {
            throw Exception("Impossible to create notification view")
        }
        Log.d(TAG, "Remote view parsed ${remoteViews}")

        val layoutInflater =
            mAppSrcCx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val viewGroup = layoutInflater.inflate(remoteViews.layoutId, null) as ViewGroup?

        if (viewGroup == null)
            throw Exception("Impossible to inflate viewGroup")

        remoteViews.reapply(mAppSrcCx, viewGroup)
        Log.d(TAG, "Remote View group parsed ${viewGroup}")

        return viewGroup
    }

    private fun parseRemoteView(group: ViewGroup) {
        for (child in group.children) {
            var entryName : String? = try {
                if (child.id > 0) mAppSrcCx.getResources().getResourceEntryName(child.id) else null
            } catch (e: Exception) { null }

            Log.d(TAG, "Found child with name ${entryName}: ${child}")

            when (child) {
                is Button ->
                    if (entryName == "dismiss_nav" || entryName == "action0") {
                        stopButton = child
                        navigationData.canStop = true
                        Log.d(TAG, "Navigation Dismiss button set to ${child}")
                    }
                is ImageView -> {
                    if (entryName == "nav_notification_icon" ||
                        entryName == "right_icon" ||
                        (entryName == "lockscreen_notification_icon" &&
                                navigationData.actionIcon.bitmap == null)
                    ) {
                        Log.d(TAG, "Found navigation icon: ${child.hashCode()}")
                        (child.drawable as BitmapDrawable).bitmap.also {
                            navigationData.actionIcon = NavigationIcon(it);
                        }
                    }
                }
//                is android.view.NotificationHeaderView -> {
//                    getOriginalIconColor
//                    getOriginalNotificationColor
//                }
                is TextView -> {
                    Log.v(TAG, "Entry ${entryName} is ${child.text}")
                    when (entryName) {
                        "nav_title" -> {
                            Log.d(TAG, "Found navigation title: ${child.text}")
                            parseNavigationTitle(child.text.toString())
                        }
//                        "header_text_divider" -> {
//                        }
//                        "time_divider" -> {
//                        }
                        "nav_description" -> {
                            Log.d(TAG, "Found navigation description: ${child.text}")
                            parseNavigationDescription(child.text)
                        }
                        "nav_time", "header_text" -> {
                            Log.d(TAG, "Found navigation time: ${child.text}")
                            parseNavigationTime(child.text.toString())
                        }
                        "lockscreen_directions", "title" ->
                            if (navigationData.nextDirection.localeString == null) {
                                Log.d(TAG, "Found navigation directions: ${child.text}")
                                parseNavigationDirections(child.text)
                            }
                        "lockscreen_oneliner" ->
                            if (navigationData.nextDirection.localeString == null) {
                                Log.d(TAG, "Found navigation oneliner: ${child.text}")
                                parseNavigationDescription(child.text)
                            }
                        "lockscreen_eta", "text" -> {
                            Log.d(TAG, "Found navigation ETA: ${child.text}")
                            parseNavigationLockscreenEta(child.text.toString())
                        }
                    }
                }
                is ViewGroup ->
                    parseRemoteView(child)
            }
        }
    }

    private fun getSplitSeparator(text: String) : String {
        return try {
            SPLIT_REGEX.find(text)!!.groupValues[0]
        } catch (e: Exception) { " - " }
    }

    private fun getHtml(text: CharSequence) : String? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            if (text is Spanned)
                return Html.toHtml(text, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        }

        return null;
    }

    private fun parseNavigationTitle(title: String) {
        navigationData.nextDirection.navigationDistance = try {
            parseNavigationDistance(mCx, title)
        } catch (error: UnknownFormatConversionException) {
            NavigationDistance(title)
        }

        Log.d(TAG, "Navigation title parsed to ${navigationData.nextDirection}")
    }

    private fun parseNavigationDescription(description: CharSequence) {
        navigationData.nextDirection.localeString = description.toString()
        navigationData.nextDirection.localeHtml = getHtml(description)

        Log.d(TAG, "Navigation description parsed to ${navigationData.nextDirection}")
    }

    private fun parseNavigationTime(time: String) {
        val timeParts = time.split(SPLIT_REGEX)

        Log.d(TAG, "Navigation time split in ${timeParts}")

        if (timeParts.size != 3)
            throw(UnknownFormatConversionException("Impossible to parse navigation time ${time}"))

        val duration = timeParts[0]
        val distance = timeParts[1]
        val eta = timeParts[2]

        navigationData.remainingDistance = try {
            parseNavigationDistance(mCx, distance)
        } catch (e : Exception) {
            NavigationDistance(distance)
        }
        Log.d(TAG, "Navigation time parsed to ${navigationData.remainingDistance}")

        parseNavigationEta(eta, duration)
    }

    private fun parseNavigationDirections(directionsSeq: CharSequence) {
        val directions = directionsSeq.toString()
        val directionsParts = directions.split(SPLIT_REGEX)

        Log.d(TAG, "Navigation directions split in ${directionsParts}")

        if (directionsParts.size < 2) {
            navigationData.isRerouting = true
            navigationData.nextDirection.localeString = directions
            return
        }

        val separator = getSplitSeparator(directions)
        val distance = directionsParts.first()
        val direction = directionsParts.subList(1, directionsParts.size).joinToString(separator)
        val directionHtml = getHtml(directionsSeq)

        navigationData.nextDirection = try {
            NavigationDirection(direction, directionHtml, parseNavigationDistance(mCx, distance))
        } catch (e : UnknownFormatConversionException) {
            NavigationDirection(direction)
        }

        Log.d(TAG, "Navigation directions parsed to ${navigationData.nextDirection}")
    }

    private fun parseNavigationEta(eta : String, duration: String? = null) {
        val match = ETA_REGEX.find(eta)

        if (match != null)
            Log.d(TAG, "Navigation ETA found  ${match.groupValues}")

        navigationData.eta = try {
            /* TODO: Use duration to set navigationData.eta.{duration,date} based on localized strings */
            NavigationTime(eta, timeParser(mCx, match?.value.toString()),
                null, NavigationDuration(duration))
        } catch (e : UnknownFormatConversionException) {
            NavigationTime(eta, null, null, NavigationDuration(duration))
        }

        Log.d(TAG, "Navigation ETA parsed to ${navigationData.eta}")
    }

    private fun parseNavigationLockscreenEta(eta: String) {
        val etaParts = eta.split(SPLIT_REGEX)

        Log.d(TAG, "Navigation ETA split in ${etaParts}")

        if (etaParts.size < 2)
            return

        val separator = getSplitSeparator(eta)
        navigationData.finalDirection = etaParts.subList(0, etaParts.size - 1).joinToString(separator)

        if (navigationData.eta.localeString == null) {
            val etaString = etaParts.last()
            parseNavigationEta(etaString)
        }
    }
}
