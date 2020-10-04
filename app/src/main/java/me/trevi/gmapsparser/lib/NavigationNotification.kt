/* -*- mode: C++; c-basic-offset: 4; indent-tabs-mode: nil; -*- */
/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Copyright (c) 2020 Marco Trevisan <marco.trevisan@canonical.com>
 */

package me.trevi.gmapsparser.lib

import android.content.Context
import android.service.notification.StatusBarNotification
import android.widget.Button

open class NavigationNotification(cx: Context, sbn: StatusBarNotification) {
    protected val TAG = this.javaClass.simpleName
    protected val _notification = sbn.notification
    protected val _cx = cx
    protected var _gmapsCx = _cx.createPackageContext(sbn.packageName,
        Context.CONTEXT_IGNORE_SECURITY
    )

    var navigationData : NavigationData = NavigationData()
        private set
    var stopButton : Button? = null
        protected set

    fun close() {
        stopButton?.callOnClick()
        navigationData.actionIcon.bitmap?.recycle()
    }
}