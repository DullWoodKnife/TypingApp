package com.typing.app.ui.content

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.typing.app.data.Content
import com.typing.app.databinding.ItemContentBinding

class ContentAdapter(
    private val onItemClick: (Content) -> Unit,
    private val onEditClick: (Content) -> Unit,
    private val onDeleteClick: (Content) -> Unit
) : ListAdapter<Content, ContentAdapter.ContentViewHolder>(DiffCallback()) {

    class ContentViewHolder(val binding: ItemContentBinding) :
        RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<Content>() {
        override fun areItemsTheSame(oldItem: Content, newItem: Content): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Content, newItem: Content): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentViewHolder {
        val binding = ItemContentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ContentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContentViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.contentTitle.text = item.title

        holder.binding.contentTitle.setOnClickListener { onItemClick(item) }
        holder.binding.btnEdit.setOnClickListener { onEditClick(item) }
        holder.binding.btnDelete.setOnClickListener { onDeleteClick(item) }
    }
}
