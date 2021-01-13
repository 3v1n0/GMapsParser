package me.trevi.gmapsparser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import me.trevi.gmapsparser.databinding.ActivityMainBinding
import me.trevi.navparser.NavParserActivity
import me.trevi.navparser.lib.GMAPS_PACKAGE

class MainActivity : NavParserActivity() {
    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.fab.setOnClickListener {
            val navURI = Uri.parse("google.navigation:q=43.769732, 11.255685&mode=d")
            val mapIntent = Intent(Intent.ACTION_VIEW, navURI)
            mapIntent.setPackage(GMAPS_PACKAGE)
            startActivity(mapIntent)
        }
    }

    override fun onStart() {
        super.onStart()

        toggleFabVisbility()
    }

    private fun toggleFabVisbility() {
        if (notificationAccess && !isNavigationActive)
            binding.fab.show()
        else
            binding.fab.hide()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        toggleFabVisbility()
    }
}
