package me.trevi.navparser.activity

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import me.trevi.navparser.NavParserActivity
import me.trevi.navparser.activity.R

class InitFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_init, container, false)
    }

    override fun onStart() {
        super.onStart()
        (activity as NavParserActivity).showMissingDataSnackbar()
    }
}
