package me.trevi.navparser_activity

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import me.trevi.navparser.NavParserActivity
import me.trevi.navparser.NavigationDataModel
import me.trevi.navparser.activity.R
import me.trevi.navparser.activity.databinding.FragmentNavigationBinding
import me.trevi.navparser.lib.NavigationData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class NavigationFragment : Fragment() {
    private val TAG = this.javaClass.simpleName
    private var _binding: FragmentNavigationBinding? = null
    private val binding get() = _binding!!
    private val navDataModel: NavigationDataModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNavigationBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.stopNavButton.setOnClickListener {
            (activity as NavParserActivity).stopNavigation()
        }

        setNavigationData(NavigationData(true))
        navDataModel.liveData.observe(viewLifecycleOwner, { setNavigationData(it) })
    }

    override fun onStart() {
        super.onStart()
        (activity as NavParserActivity).hideMissingDataSnackbar()
    }

    override fun onStop() {
        (activity as NavParserActivity).stopServiceListener()
        super.onStop()
    }

    private fun setNavigationData(navData: NavigationData) {
        binding.navigationDest.text = getString(
            if (navData.isRerouting)
                R.string.recalculating_navigation
            else
                R.string.navigate_to
        ).format(navData.finalDirection)

        binding.nextDirection.text = navData.nextDirection.localeString
        binding.nextAction.text = navData.nextDirection.navigationDistance?.localeString

        binding.eta.text = getString(R.string.eta).format(
            if (navData.eta.time != null) {
                DateFormat.getTimeFormat(activity?.applicationContext).format(
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

        binding.distance.text = getString(R.string.distance).format(navData.remainingDistance.localeString)
        binding.stopNavButton.isEnabled = navData.canStop
        binding.actionDirection.setImageBitmap(navData.actionIcon.bitmap)
    }
}
