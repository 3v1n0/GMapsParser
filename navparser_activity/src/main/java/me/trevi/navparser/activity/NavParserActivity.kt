/* -*- mode: C++; c-basic-offset: 4; indent-tabs-mode: nil; -*- */
/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Copyright (c) 2020 Marco Trevisan <marco.trevisan@canonical.com>
 */

package me.trevi.navparser

import android.app.PendingIntent
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat.getTimeFormat
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_navigation.*
import me.trevi.navparser.activity.R
import me.trevi.navparser.lib.NavigationData
import me.trevi.navparser.service.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*


open class NavParserActivity : AppCompatActivity() {
    private val TAG = this.javaClass.simpleName
    private var _snackbar : Snackbar? = null;

    protected fun haveNotificationsAccess() : Boolean {
        var notificationAccess = Settings.Secure.getString(
            this.contentResolver, "enabled_notification_listeners"
        )

        return NavigationListenerEmitter::class.qualifiedName.toString() in notificationAccess
    }

    private fun serviceIntent(action: String) : Intent {
        return Intent(applicationContext, NavigationListenerEmitter::class.java).setAction(action)
    }

    fun stopNavigation() {
        Log.d(TAG, "stopNavigation ${serviceIntent(STOP_NAVIGATION)}")
        startService(serviceIntent(STOP_NAVIGATION))
    }

    private fun setServiceListenerIntent(pendingIntent: PendingIntent?) {
        startService(serviceIntent(SET_INTENT).putExtra(PENDING_INTENT, pendingIntent))
    }

    private fun startServiceListener() {
        setServiceListenerIntent(createPendingResult(100, Intent(), 0))
    }

    fun stopServiceListener() {
        setServiceListenerIntent(null)
    }

    fun setNavigationData(navData: NavigationData) {
        navigationDest.text = getString(
            if (navData.isRerouting)
                R.string.recalculating_navigation
            else
                R.string.navigate_to
        ).format(navData.finalDirection)

        nextDirection.text = navData.nextDirection.localeString
        nextAction.text = navData.nextDirection.navigationDistance?.localeString

        eta.text = getString(R.string.eta).format(
            if (navData.eta.time != null) {
                getTimeFormat(applicationContext).format(
                    Date.from(
                        LocalDateTime.of(LocalDate.now(), navData.eta.time).atZone(
                            ZoneId.systemDefault()
                        ).toInstant()
                    )
                )
            } else {
                navData.eta.localeString
            },
            navData.eta.duration?.localeString
        )

        distance.text = getString(R.string.distance).format(navData.remainingDistance.localeString)

        stopNavButton.isEnabled = navData.canStop

        var image = findViewById<ImageView>(R.id.actionDirection)
        image.setImageBitmap(navData.actionIcon.bitmap)
    }

    private fun getNavController() : NavController {
        return findNavController(R.id.nav_host_fragment)
    }

    private fun gotoFragment(fragmentId: Int) {
        Log.d(TAG, "Changing fragment ${getNavController().currentDestination} -> ${fragmentId}")
        if (getNavController().currentDestination?.id == fragmentId)
            return

        when (fragmentId) {
            R.id.InitFragment -> getNavController().navigate(R.id.action_Navigation_to_Init_fragment)
            R.id.NavigationFragment -> getNavController().navigate(R.id.action_Init_to_Navigation_fragment)
        }
    }

    fun showMissingDataSnackbar() {
        if (_snackbar != null) {
            _snackbar!!.show()
            return
        }

        _snackbar = Snackbar.make(
            findViewById(android.R.id.content),
            R.string.no_navigation_started,
            Snackbar.LENGTH_INDEFINITE
        )
        _snackbar!!.show()
    }

    fun hideMissingDataSnackbar() {
        _snackbar?.dismiss()
        _snackbar = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        Log.d(TAG, "Got activity result: ${intent}, ${intent?.extras}, ${intent?.action}")
        when (intent?.action) {

            NAVIGATION_DATA_UPDATED -> {
                gotoFragment(R.id.NavigationFragment)

                val navData = intent.getParcelableExtra<NavigationData>(NAVIGATION_DATA)
                val navDataLayout = findViewById<View>(R.id.navDataLayout)

                Log.v(TAG, "Got navigation data ${navData}")

                if (navData != null && navDataLayout != null)
                    setNavigationData(navData)
            }

            NAVIGATION_STARTED -> {
                gotoFragment(R.id.NavigationFragment)
            }

            NAVIGATION_STOPPED -> {
                gotoFragment(R.id.InitFragment)
            }
        }
    }

    protected fun showMissingNotificationsAccessSnackbar() {
        Snackbar.make(
            findViewById(android.R.id.content),
            R.string.missing_permissions,
            Snackbar.LENGTH_INDEFINITE
        ).setAction(R.string.action_settings) {
            this@NavParserActivity.startActivityForResult(
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
                0
            )
        }.show()
    }

    override fun onStart() {
        super.onStart()

        if (haveNotificationsAccess()) {
            startServiceListener()
        } else {
            gotoFragment(R.id.InitFragment)
            showMissingNotificationsAccessSnackbar()
            Log.e(TAG, "No notification access for ${NavigationListenerEmitter::class.qualifiedName}")
        }
    }
}