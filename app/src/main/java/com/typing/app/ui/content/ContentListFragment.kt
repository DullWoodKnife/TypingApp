package com.typing.app.ui.content

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.typing.app.R
import com.typing.app.data.AppDatabase
import com.typing.app.data.DataRepository
import com.typing.app.databinding.FragmentContentListBinding
import kotlinx.coroutines.launch

class ContentListFragment : Fragment() {

    private var _binding: FragmentContentListBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DataRepository
    private lateinit var adapter: ContentAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = DataRepository(db.contentDao(), db.recordDao())

        val isSelectMode = arguments?.getBoolean("selectMode") ?: false
        if (isSelectMode) {
            binding.contentListTitle.text = getString(R.string.content_select_title)
        } else {
            binding.contentListTitle.text = getString(R.string.content_list_title)
        }

        adapter = ContentAdapter(
            onItemClick = { content ->
                if (isSelectMode) {
                    // Return selected content ID to practice fragment
                    // Use shared preferences or result API
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("selected_content_id", content.id)
                    findNavController().popBackStack()
                } else {
                    viewContent(content.id)
                }
            },
            onEditClick = { content ->
                openEdit(content.id)
            },
            onDeleteClick = { content ->
                deleteContent(content.id)
            }
        )

        binding.contentRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.contentRecyclerView.adapter = adapter

        binding.btnAddNewContent.setOnClickListener {
            openEdit(null)
        }

        loadContents()
    }

    private fun loadContents() {
        lifecycleScope.launch {
            repository.allContents.observe(viewLifecycleOwner) { contents ->
                if (contents.isEmpty()) {
                    binding.emptyStateView.visibility = View.VISIBLE
                    binding.contentRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyStateView.visibility = View.GONE
                    binding.contentRecyclerView.visibility = View.VISIBLE
                    adapter.submitList(contents.toList())
                }
            }
        }
    }

    private fun viewContent(id: String) {
        val bundle = Bundle().apply { putString("contentId", id) }
        findNavController().navigate(R.id.content_detail_fragment, bundle)
    }

    private fun openEdit(id: String?) {
        val bundle = Bundle()
        id?.let { bundle.putString("contentId", it) }
        findNavController().navigate(R.id.content_edit_fragment, bundle)
    }

    private fun deleteContent(id: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.title_cannot_empty))
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteById(id)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
