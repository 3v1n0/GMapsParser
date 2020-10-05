package me.trevi.gmapsparser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import me.trevi.navparser.NavParserActivity
import me.trevi.navparser.lib.GMAPS_PACKAGE

class MainActivity : NavParserActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            val navURI = Uri.parse("google.navigation:q=43.769732, 11.255685&mode=d")
            val mapIntent = Intent(Intent.ACTION_VIEW, navURI)
            mapIntent.setPackage(GMAPS_PACKAGE)
            startActivity(mapIntent)
        }
    }

    override fun onStart() {
        super.onStart()

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        if (haveNotificationsAccess())
            fab.show()
        else
            fab.hide()
    }
}