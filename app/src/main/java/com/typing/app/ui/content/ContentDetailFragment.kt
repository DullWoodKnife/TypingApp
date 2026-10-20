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
import com.typing.app.data.DataRepository
import com.typing.app.databinding.FragmentContentDetailBinding
import kotlinx.coroutines.launch

class ContentDetailFragment : Fragment() {

    private var _binding: FragmentContentDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DataRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = DataRepository(db.contentDao(), db.recordDao())

        val contentId = arguments?.getString("contentId") ?: return

        binding.btnBackToList.setOnClickListener {
            findNavController().popBackStack()
        }

        lifecycleScope.launch {
            val content = repository.getContent(contentId)
            if (content != null) {
                binding.detailTitleText.text = content.title
                binding.detailMeta.text = getString(R.string.char_count, content.content.length)
                binding.detailContentView.text = content.content
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
