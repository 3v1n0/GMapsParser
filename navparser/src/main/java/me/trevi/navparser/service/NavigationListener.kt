/* -*- mode: C++; c-basic-offset: 4; indent-tabs-mode: nil; -*- */
/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Copyright (c) 2020 Marco Trevisan <mail@trevi.me>
 */

package me.trevi.navparser.service

import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.*
import me.trevi.navparser.BuildConfig
import me.trevi.navparser.lib.GMAPS_PACKAGE
import me.trevi.navparser.lib.GMapsNotification
import me.trevi.navparser.lib.NavigationNotification
import timber.log.Timber as Log

private const val NOTIFICATIONS_THRESHOLD : Long = 500 // Ignore notifications coming earlier, in ms

open class NavigationListener : NotificationListenerService() {
    private var mNotificationParserCoroutine : Job? = null;
    private lateinit var mLastNotification : StatusBarNotification
    private var mCurrentNotification : NavigationNotification? = null
    private var mNotificationsThreshold : Long = NOTIFICATIONS_THRESHOLD
    private var mEnabled = false

    protected var enabled : Boolean
        get() = mEnabled
        set(value) {
            if (value == mEnabled)
                return
            if (value.also { mEnabled = it })
                checkActiveNotifications()
            else
                mCurrentNotification = null

            Log.v("Navigation listener enabled: $mEnabled")
        }

    protected var notificationsThreshold : Long
        get() = mNotificationsThreshold
        set(value) {
            mNotificationsThreshold = if (value < 0) NOTIFICATIONS_THRESHOLD else value
            mNotificationParserCoroutine?.cancel()
            checkActiveNotifications()
        }

    protected val currentNotification : NavigationNotification?
        get() = mCurrentNotification

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Log.plant(Log.DebugTree())
        }

        if (haveNotificationsAccess()) {
            Log.d("Notifications access granted to ${this.javaClass.name}!")
        } else {
            Log.e("No notification access granted to ${this.javaClass.name}!")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected();

        checkActiveNotifications()
    }

    private fun checkActiveNotifications() {
        if (Build.VERSION.SDK_INT < 23 || !haveNotificationsAccess())
            return

        try {
            Log.d("Checking for active Navigation notifications")
            this.activeNotifications.forEach { sbn -> onNotificationPosted(sbn) }
        } catch (e: Throwable) {
            Log.e("Failed to check for active notifications: $e")
        }
    }

    private fun isGoogleMapsNotification(sbn: StatusBarNotification?) : Boolean {
        if (!mEnabled)
            return false

        if (!sbn!!.isOngoing || GMAPS_PACKAGE !in sbn.packageName)
            return false

        return (sbn.id == 1)
    }

    fun haveNotificationsAccess() : Boolean {
        Settings.Secure.getString(
            this.contentResolver, "enabled_notification_listeners"
        ).also {
            Log.v("Checking if ${this::class.qualifiedName} has notification access")
            return this::class.qualifiedName.toString() in it
        }
    }

    protected open fun onNavigationNotificationAdded(navNotification : NavigationNotification) {
    }

    protected open fun onNavigationNotificationUpdated(navNotification : NavigationNotification) {
    }

    protected open fun onNavigationNotificationRemoved(navNotification : NavigationNotification) {
    }

    private fun handleGoogleNotification(sbn: StatusBarNotification) {
        mLastNotification = sbn;

        if (mNotificationParserCoroutine != null && mNotificationParserCoroutine!!.isActive)
            return

        mNotificationParserCoroutine = GlobalScope.launch(Dispatchers.Main) {
            if (mCurrentNotification != null)
                delay(notificationsThreshold)

            val worker = GlobalScope.async(Dispatchers.Default) {
                return@async GMapsNotification(
                    this@NavigationListener.applicationContext,
                    mLastNotification
                );
            }

            try {
                val mapNotification = worker.await()
                val lastNotification = mCurrentNotification
                val updated : Boolean

                if (lastNotification == null) {
                    onNavigationNotificationAdded(mapNotification)
                    updated = true
                } else {
                    updated = lastNotification.navigationData != mapNotification.navigationData
                    Log.v("Notification is different than previous: $updated")
                }

                if (updated) {
                    mCurrentNotification = mapNotification

                    onNavigationNotificationUpdated(mCurrentNotification!!);
                }
            } catch (error: Exception) {
                if (!mNotificationParserCoroutine!!.isCancelled)
                    Log.e("Got an error while parsing: $error");
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Log.v("Got notification from ${sbn?.packageName}")

        if (sbn != null && isGoogleMapsNotification(sbn))
            handleGoogleNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null && isGoogleMapsNotification(sbn)) {
            Log.d("Notification removed ${sbn}, ${sbn.hashCode()}")
            mNotificationParserCoroutine?.cancel();

            onNavigationNotificationRemoved(mCurrentNotification!!)
            mCurrentNotification = null;
        }
    }
}
