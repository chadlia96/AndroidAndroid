package com.android.androidAndroid.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.android.androidAndroid.R
import com.android.androidAndroid.databinding.FragmentHomeBinding
import com.android.androidAndroid.junk.SystemTimeUtil

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingTasks = mutableListOf<Runnable>()
    private val floatViews = mutableListOf<View>()
    private var iconCount = 0

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonAgentMode.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_chatFragmentAgent)
        }

        binding.buttonJustTalk.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_chatFragmentJustTalk)
        }
    }

    override fun onDestroyView() {
        pendingTasks.forEach { mainHandler.removeCallbacks(it) }
        pendingTasks.clear()
        super.onDestroyView()
        _binding = null
    }


    private fun ensureOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (Settings.canDrawOverlays(requireContext())) return true

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${requireContext().packageName}")
        )
        startActivity(intent)
        Toast.makeText(requireContext(), "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
        return false
    }

}
