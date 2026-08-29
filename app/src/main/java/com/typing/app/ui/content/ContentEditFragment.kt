package com.typing.app.ui.content

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.typing.app.R
import com.typing.app.data.AppDatabase
import com.typing.app.data.Content
import com.typing.app.data.DataRepository
import com.typing.app.databinding.FragmentContentEditBinding
import kotlinx.coroutines.launch

class ContentEditFragment : Fragment() {

    private var _binding: FragmentContentEditBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DataRepository
    private var editId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContentEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = DataRepository(db.contentDao(), db.recordDao())

        editId = arguments?.getString("contentId")

        if (editId != null) {
            binding.editTitleText.text = getString(R.string.edit_title_edit)
            lifecycleScope.launch {
                val content = editId?.let { repository.getContent(it) }
                if (content != null) {
                    binding.editInputTitle.setText(content.title)
                    binding.editInputContent.setText(content.content)
                }
            }
        } else {
            binding.editTitleText.text = getString(R.string.edit_title)
        }

        binding.btnCancelEdit.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnSaveContent.setOnClickListener {
            saveContent()
        }
    }

    private fun saveContent() {
        val title = binding.editInputTitle.text.toString().trim()
        val content = binding.editInputContent.text.toString().trim()

        if (title.isEmpty() || content.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.title_cannot_empty))
                .setMessage(getString(R.string.msg_title_content_empty))
                .setPositiveButton("好的", null)
                .show()
            return
        }

        lifecycleScope.launch {
            if (editId != null) {
                val existing = repository.getContent(editId!!)
                if (existing != null) {
                    repository.updateContent(existing.copy(title = title, content = content))
                }
            } else {
                val newContent = Content(
                    id = "c${System.currentTimeMillis()}_${(Math.random() * 10000).toInt().toString(36)}",
                    title = title,
                    content = content,
                    createdAt = System.currentTimeMillis()
                )
                repository.insertContent(newContent)
            }
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
