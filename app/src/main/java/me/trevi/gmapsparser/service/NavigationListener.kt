/* -*- mode: C++; c-basic-offset: 4; indent-tabs-mode: nil; -*- */
/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Copyright (c) 2020 Marco Trevisan <marco.trevisan@canonical.com>
 */

package me.trevi.gmapsparser.service

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.*
import me.trevi.gmapsparser.lib.GMAPS_PACKAGE
import me.trevi.gmapsparser.lib.GMapsNotification
import me.trevi.gmapsparser.lib.NavigationNotification

private const val NOTIFICATIONS_THRESHOLD : Long = 500 // Ignore notifications coming earlier, in ms

open class NavigationListener : NotificationListenerService() {
    private val TAG = this.javaClass.simpleName;
    private var mNotificationParserCoroutine : Job? = null;
    private var mLastNotification : StatusBarNotification? = null
    private var mCurrentNotification : NavigationNotification? = null
    protected var mEnabled = false

    protected val currentNotification : NavigationNotification?
        get() = mCurrentNotification

    override fun onListenerConnected() {
        super.onListenerConnected();

        if (Build.VERSION.SDK_INT < 23)
            return

        this.activeNotifications.forEach { sbn -> onNotificationPosted(sbn) }
    }

    protected fun isGoogleNotification(sbn: StatusBarNotification?): Boolean {
        if (!mEnabled)
            return false

        if (!sbn!!.isOngoing || GMAPS_PACKAGE !in sbn.packageName)
            return false

        return (sbn.id == 1)
    }

    protected open fun onNavigationNotificationAdded(navNotification : NavigationNotification) {
    }

    protected open fun onNavigationNotificationUpdated(navNotification : NavigationNotification) {
    }

    protected open fun onNavigationNotificationRemoved(navNotification : NavigationNotification) {
    }

    protected fun handleGoogleNotification(sbn: StatusBarNotification) {
        mLastNotification = sbn;

        if (mNotificationParserCoroutine != null && mNotificationParserCoroutine!!.isActive)
            return

        mNotificationParserCoroutine = GlobalScope.launch(Dispatchers.Main) {
            if (mCurrentNotification != null)
                delay(NOTIFICATIONS_THRESHOLD)

            var worker = GlobalScope.async(Dispatchers.Default) {
                return@async GMapsNotification(
                    this@NavigationListener.applicationContext,
                    mLastNotification!!
                );
            }

            try {
                val mapNotification = worker.await()
                val lastNotification = mCurrentNotification
                var updated : Boolean

                if (lastNotification == null) {
                    onNavigationNotificationAdded(mapNotification)
                    updated = true
                } else {
                    updated = !lastNotification.navigationData.equals(mapNotification.navigationData)
                    Log.v(TAG, "Notification is different than previous: ${updated}")
                }

                if (updated) {
                    mCurrentNotification = mapNotification

                    onNavigationNotificationUpdated(mCurrentNotification!!);
                }
            } catch (error: Exception) {
                if (!mNotificationParserCoroutine!!.isCancelled)
                    Log.e(TAG, "Got an error while parsing: ${error}");
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Log.v(TAG, "Got notification from ${sbn?.packageName}")

        if (sbn != null && isGoogleNotification(sbn))
            handleGoogleNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null && isGoogleNotification(sbn)) {
            Log.d(TAG, "Notification removed ${sbn}, ${sbn.hashCode()}")
            mNotificationParserCoroutine?.cancel();

            onNavigationNotificationRemoved(mCurrentNotification!!)
            mCurrentNotification = null;
        }
    }
}
