package com.smartexpenseai.app.ui.tags

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartexpenseai.app.data.dao.TagWithCount
import com.smartexpenseai.app.databinding.ItemTagBinding

/**
 * Renders tags with their usage counts. Row click edits; the trailing icon deletes.
 */
class TagAdapter(
    private val onEdit: (TagWithCount) -> Unit,
    private val onDelete: (TagWithCount) -> Unit
) : ListAdapter<TagWithCount, TagAdapter.TagViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val binding = ItemTagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TagViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TagViewHolder(private val binding: ItemTagBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TagWithCount) {
            binding.tvTagName.text = item.tag.name
            val count = item.usageCount
            binding.tvTagCount.text = when (count) {
                0 -> "Not used"
                1 -> "1 transaction"
                else -> "$count transactions"
            }
            try {
                binding.viewTagColor.background.setTint(Color.parseColor(item.tag.color))
            } catch (_: Exception) {
                binding.viewTagColor.background.setTint(Color.parseColor("#607D8B"))
            }
            binding.root.setOnClickListener { onEdit(item) }
            binding.btnDeleteTag.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TagWithCount>() {
            override fun areItemsTheSame(a: TagWithCount, b: TagWithCount) = a.tag.id == b.tag.id
            override fun areContentsTheSame(a: TagWithCount, b: TagWithCount) = a == b
        }
    }
}
