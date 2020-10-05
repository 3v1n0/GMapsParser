package me.trevi.gmapsparser

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_navigation.*
import me.trevi.gmapsparser.lib.NavigationData

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
            (activity as MainActivity).stopNavigation()
        }

        (activity as MainActivity).setNavigationData(NavigationData(true))
    }

    override fun onStart() {
        super.onStart()
        (activity as MainActivity).hideMissingDataSnackbar()
    }

    override fun onStop() {
        (activity as MainActivity).stopServiceListener()
        super.onStop()
    }
}