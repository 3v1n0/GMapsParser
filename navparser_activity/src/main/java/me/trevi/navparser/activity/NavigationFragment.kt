package me.trevi.navparser_activity

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_navigation.*
import me.trevi.navparser.NavParserActivity
import me.trevi.navparser.activity.R
import me.trevi.navparser.lib.NavigationData

class NavigationFragment : Fragment() {
    private val TAG = this.javaClass.simpleName

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_navigation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stopNavButton.setOnClickListener {
            (activity as NavParserActivity).stopNavigation()
        }

        (activity as NavParserActivity).setNavigationData(NavigationData(true))
    }

    override fun onStart() {
        super.onStart()
        (activity as NavParserActivity).hideMissingDataSnackbar()
    }

    override fun onStop() {
        (activity as NavParserActivity).stopServiceListener()
        super.onStop()
    }
}