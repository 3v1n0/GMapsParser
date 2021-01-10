/* -*- mode: C++; c-basic-offset: 4; indent-tabs-mode: nil; -*- */
/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Copyright (c) 2020 Marco Trevisan <mail@trevi.me>
 */

package me.trevi.navparser

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.google.android.material.snackbar.Snackbar
import me.trevi.navparser.activity.R
import me.trevi.navparser.lib.NavigationData
import me.trevi.navparser.service.*
import timber.log.Timber as Log
import java.util.*


class NavigationDataModel : ViewModel() {
    private val mutableData = MutableLiveData<NavigationData>()
    val liveData: LiveData<NavigationData> get() = mutableData
    var data: NavigationData?
        get() = mutableData.value
        set(value) { mutableData.value = value }
}


open class NavParserActivity : AppCompatActivity() {
    private var mSnackbar : Snackbar? = null;
    private val mNavDataModel: NavigationDataModel by viewModels()
    val isNavigationActive get() = getNavController().currentDestination?.id == R.id.NavigationFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            Log.plant(Log.DebugTree())
        }

        Log.d("${this.javaClass.simpleName} created")
    }

    protected fun haveNotificationsAccess() : Boolean {
        val notificationAccess = Settings.Secure.getString(
            this.contentResolver, "enabled_notification_listeners"
        )

        return NavigationListenerEmitter::class.qualifiedName.toString() in notificationAccess
    }

    private fun serviceIntent(action: String) : Intent {
        return Intent(applicationContext, NavigationListenerEmitter::class.java).setAction(action)
    }

    fun stopNavigation() {
        Log.d("stopNavigation ${serviceIntent(STOP_NAVIGATION)}")
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

    private fun getNavController() : NavController {
        return findNavController(R.id.nav_host_fragment)
    }

    private fun gotoFragment(fragmentId: Int) {
        Log.d("Changing fragment ${getNavController().currentDestination} -> $fragmentId")
        if (getNavController().currentDestination?.id == fragmentId)
            return

        when (fragmentId) {
            R.id.InitFragment -> getNavController().navigate(R.id.action_Navigation_to_Init_fragment)
            R.id.NavigationFragment -> getNavController().navigate(R.id.action_Init_to_Navigation_fragment)
        }
    }

    fun showMissingDataSnackbar() {
        if (mSnackbar != null) {
            mSnackbar!!.show()
            return
        }

        mSnackbar = Snackbar.make(
            findViewById(android.R.id.content),
            R.string.no_navigation_started,
            Snackbar.LENGTH_INDEFINITE
        )
        mSnackbar!!.show()
    }

    fun hideMissingDataSnackbar() {
        mSnackbar?.dismiss()
        mSnackbar = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        Log.d("Got activity result: $intent, ${intent?.extras}, ${intent?.action}")
        when (intent?.action) {

            NAVIGATION_DATA_UPDATED -> {
                gotoFragment(R.id.NavigationFragment)

                val navData = intent.getParcelableExtra<NavigationData>(NAVIGATION_DATA)

                Log.v("Got navigation data $navData")
                mNavDataModel.data = navData
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
            Log.e("No notification access for ${NavigationListenerEmitter::class.qualifiedName}")
        }
    }
}
