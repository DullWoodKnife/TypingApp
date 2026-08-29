package com.typing.app.ui.records

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.typing.app.R
import com.typing.app.data.AppDatabase
import com.typing.app.data.DataRepository
import com.typing.app.databinding.FragmentRecordsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordsFragment : Fragment() {

    private var _binding: FragmentRecordsBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DataRepository
    private lateinit var adapter: RecordAdapter

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = DataRepository(db.contentDao(), db.recordDao())

        adapter = RecordAdapter()
        binding.recordsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.recordsRecyclerView.adapter = adapter

        binding.btnClearRecords.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.title_cannot_empty))
                .setMessage(getString(R.string.confirm_clear_records))
                .setPositiveButton("确定") { _, _ ->
                    lifecycleScope.launch {
                        repository.clearAllRecords()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        loadRecords()
    }

    private fun loadRecords() {
        lifecycleScope.launch {
            repository.allRecords.observe(viewLifecycleOwner) { records ->
                if (records.isEmpty()) {
                    binding.emptyRecordsView.visibility = View.VISIBLE
                    binding.recordsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyRecordsView.visibility = View.GONE
                    binding.recordsRecyclerView.visibility = View.VISIBLE
                    adapter.submitList(records.reversed())
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
