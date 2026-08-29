package com.typing.app.ui.records

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.typing.app.R
import com.typing.app.data.Record
import com.typing.app.databinding.ItemRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordAdapter : ListAdapter<Record, RecordAdapter.RecordViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    class RecordViewHolder(val binding: ItemRecordBinding) :
        RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<Record>() {
        override fun areItemsTheSame(oldItem: Record, newItem: Record): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Record, newItem: Record): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding = ItemRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val record = getItem(position)
        val context = holder.itemView.context

        holder.binding.recordTitle.text = record.contentTitle

        val dateStr = dateFormat.format(Date(record.date))
        val modeLabel = if (record.mode == "timed")
            context.getString(R.string.mode_timed)
        else
            context.getString(R.string.mode_normal)
        holder.binding.recordMeta.text = context.getString(
            R.string.mode_label, modeLabel, record.accuracy
        )

        holder.binding.recordSpeed.text = record.speed.toString()
    }
}
