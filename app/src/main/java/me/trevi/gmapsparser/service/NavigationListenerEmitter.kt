/* -*- mode: C++; c-basic-offset: 4; indent-tabs-mode: nil; -*- */
/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Copyright (c) 2020 Marco Trevisan <marco.trevisan@canonical.com>
 */

package me.trevi.gmapsparser.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import me.trevi.gmapsparser.BuildConfig
import me.trevi.gmapsparser.lib.NavigationNotification

const val SET_INTENT = "${BuildConfig.APPLICATION_ID}.SET_INTENT"
const val INTENT_SET = "${BuildConfig.APPLICATION_ID}.INTENT_SET"
const val STOP_NAVIGATION = "${BuildConfig.APPLICATION_ID}.STOP_NAVIGATION"
const val NAVIGATION_DATA_UPDATED = "${BuildConfig.APPLICATION_ID}.NAVIGATION_DATA_UPDATED"
const val NAVIGATION_STARTED = "${BuildConfig.APPLICATION_ID}.NAVIGATION_STARTED"
const val NAVIGATION_STOPPED = "${BuildConfig.APPLICATION_ID}.NAVIGATION_STOPPED"

const val PENDING_INTENT = "pendingIntent"
const val NAVIGATION_DATA = "navData"

class NavigationListenerEmitter : NavigationListener() {
    private val TAG = this.javaClass.simpleName;
    private var _pendingIntent : PendingIntent? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.v(TAG, "onStartCommand: ${intent}")
        when (intent?.action) {
            SET_INTENT -> {
                _pendingIntent = intent.getParcelableExtra<PendingIntent>(PENDING_INTENT)
                _enabled = _pendingIntent != null
                Log.d(TAG, "Set pending intent ${_pendingIntent}: ${intent}, ${intent.action}")

                if (_pendingIntent != null) {
                    _pendingIntent?.send(applicationContext, 200, Intent(INTENT_SET))
                }
            }

            STOP_NAVIGATION -> {
                Log.d(TAG, "Stopping navigation requested: ${currentNotification?.stopButton}")
                if (currentNotification != null)
                    currentNotification!!.close()
            }
        }

        return START_NOT_STICKY
    }

    override fun onNavigationNotificationAdded(navNotification: NavigationNotification) {
        _pendingIntent?.send(applicationContext, 200, Intent(NAVIGATION_STARTED))
    }

    override fun onNavigationNotificationUpdated(navNotification : NavigationNotification) {
        _pendingIntent?.send(
            applicationContext, 200, Intent(NAVIGATION_DATA_UPDATED).putExtra(
                NAVIGATION_DATA, navNotification.navigationData
            )
        )
    }

    override fun onNavigationNotificationRemoved(navNotification : NavigationNotification) {
        _enabled = false
        _pendingIntent?.send(applicationContext, 200, Intent(NAVIGATION_STOPPED))
    }
}
