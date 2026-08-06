package com.android.androidAndroid.ui.chat

import android.graphics.drawable.ColorDrawable
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.androidAndroid.R
import com.android.androidAndroid.accessibility.NERV
import com.android.androidAndroid.databinding.FragmentChatBinding

/**
 * Chat 页面主控制器（对话 + 任务执行）。
 *
 * 职责概览（给后续 code agent / LLM）：
 * - 接收用户输入，构造 `ChatMessage(mode=0)` 并追加到列表。
 * - AGENT 模式将用户输入封装为 `UserInput` 发送给 `NERV`。
 * - 维护 RecyclerView 数据源 `entries` 与滚动行为（新消息追加后滚动到底部）。
 * - 监听 IME（软键盘）insets，动态调整输入栏底部间距，避免输入区被遮挡。
 *
 * 约定：
 * - `mode=0` 表示用户消息，`mode=1` 表示系统/Agent 回复（由 `ChatMessageAdapter` 决定左右对齐）。
 * - UI 更新必须在主线程执行；任务执行在 IO 协程中进行。
 */

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel by lazy { ViewModelProvider(this)[ChatViewModel::class.java] }
    private val baseBottomMarginDp = 10
    private val entryAdapter by lazy {
        ChatMessageAdapter(viewModel.entries.value ?: mutableListOf())
    }
    private var previewPopupWindow: PopupWindow? = null
    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                viewModel.setSelectedImageUri(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entryMode = arguments?.getInt(ARG_ENTRY_MODE, ENTRY_MODE_AGENT_LOOP)
            ?: ENTRY_MODE_AGENT_LOOP
        viewModel.setEntryMode(entryMode)
        if (entryMode == ENTRY_MODE_AGENT_LOOP && NERV.getRunningService() == null) {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.open_accessibility_failed, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeImeAndAdjustInputBar()

        binding.recyclerViewEntries.apply {
            var mLayoutManager = LinearLayoutManager(requireContext())
            mLayoutManager.stackFromEnd = true
            layoutManager = mLayoutManager
            adapter = entryAdapter
            setHasFixedSize(true)
        }
        observeViewModel()
        setupImagePickerButton()
    }

    override fun onResume() {
        super.onResume()
        setupSubmitClickListenerByMode()
    }

    override fun onDestroyView() {
        hideImagePreviewBubble()
        super.onDestroyView()
        _binding = null
    }

    fun getImeHeightPx(): Int {
        val insets = ViewCompat.getRootWindowInsets(binding.root) ?: return 0
        return insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
    }

    private fun observeImeAndAdjustInputBar() {
        val baseBottomMarginPx = dpToPx(baseBottomMarginDp)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val params = binding.editLayout.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = if (isImeVisible) {
                baseBottomMarginPx + getImeHeightPx()
            } else {
                baseBottomMarginPx
            }
            binding.editLayout.layoutParams = params
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun observeViewModel() {
        viewModel.entries.observe(viewLifecycleOwner) {
            entryAdapter.notifyDataSetChanged()
            if (it.isNotEmpty()) {
                binding.recyclerViewEntries.scrollToPosition(it.lastIndex)
            }
        }
        viewModel.selectedImageUri.observe(viewLifecycleOwner) { uri ->
            if (uri == null) {
                hideImagePreviewBubble()
            } else {
                showImagePreviewBubble(uri)
            }
        }
    }

    private fun setupSubmitClickListenerByMode() {
        when (viewModel.entryMode) {
            ENTRY_MODE_JUST_TALK -> {
                binding.buttonSubmitEntry.setOnClickListener {
                    submitCurrentInput()
                }
            }

            else -> {
                binding.buttonSubmitEntry.setOnClickListener {
                    submitCurrentInput()
                }
            }
        }
    }

    private fun setupImagePickerButton() {
        binding.buttonPickImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
    }

    private fun submitCurrentInput() {
        val rawInput = binding.editTextEntry.text?.toString()?.trim().orEmpty()
        binding.editTextEntry.text?.clear()
        if (viewModel.submitInput(requireContext(), rawInput)) {
            hideImagePreviewBubble()
        }
    }

    private fun ensureImagePreviewPopup(): PopupWindow {
        val existingPopup = previewPopupWindow
        if (existingPopup != null) {
            return existingPopup
        }
        val popupView = layoutInflater.inflate(R.layout.popup_image_preview, null)
        return PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            //isOutsideTouchable = true
            isFocusable = false
            elevation = dpToPx(8).toFloat()
            setBackgroundDrawable(
                ColorDrawable(
                    ContextCompat.getColor(requireContext(), android.R.color.transparent)
                )
            )
            setOnDismissListener {
                previewPopupWindow = this
            }
            previewPopupWindow = this
        }
    }

    private fun showImagePreviewBubble(uri: Uri? = viewModel.selectedImageUri.value) {
        val previewUri = uri ?: return
        val popupWindow = ensureImagePreviewPopup()
        val imageView = popupWindow.contentView.findViewById<ImageView>(R.id.imageViewPreview)
        imageView.setImageURI(previewUri)

        popupWindow.contentView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = popupWindow.contentView.measuredHeight
        val offsetY = -(binding.buttonPickImage.height + popupHeight + dpToPx(12))

        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
        popupWindow.showAsDropDown(binding.buttonPickImage, 0, offsetY)
    }

    private fun dpToPx(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun hideImagePreviewBubble() {
        previewPopupWindow?.dismiss()
    }

    companion object {
        private const val ARG_ENTRY_MODE = "entryMode"
        private const val ENTRY_MODE_AGENT_LOOP = 1
        private const val ENTRY_MODE_JUST_TALK = 2
    }
}
