package com.smartexpenseai.app.ui.recurring

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.smartexpenseai.app.databinding.ItemRecurringBinding
import com.smartexpenseai.app.services.RecurringSeries
import com.smartexpenseai.app.services.RecurringStatus
import java.text.SimpleDateFormat
import java.util.Locale

class RecurringAdapter :
    ListAdapter<RecurringSeries, RecurringAdapter.VH>(DIFF) {

    private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRecurringBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemRecurringBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecurringSeries) {
            binding.tvMerchant.text = item.displayName
            binding.tvAmount.text = "₹${money(item.typicalAmount)}"
            binding.tvCadence.text = buildString {
                append(item.cadence.label)
                append(" · ").append(item.categoryName)
                append(" · ${item.occurrences} charges")
            }
            binding.tvNext.text = "Next ~ ${dateFmt.format(item.nextEstimatedDate)}"

            val chips = binding.chipGroupFlags
            chips.removeAllViews()
            if (item.priceIncreased) {
                addChip(chips, "↑ Price up (was ₹${money(item.typicalAmount)} → ₹${money(item.latestAmount)})")
            }
            when (item.status) {
                RecurringStatus.DUE_SOON -> addChip(chips, "Due soon")
                RecurringStatus.OVERDUE -> addChip(chips, "Overdue — cancelled?")
                RecurringStatus.ACTIVE -> { /* no chip */ }
            }
        }

        private fun addChip(group: com.google.android.material.chip.ChipGroup, text: String) {
            val chip = Chip(group.context).apply {
                this.text = text
                isClickable = false
                isCheckable = false
                setEnsureMinTouchTargetSize(false)
                chipMinHeight = group.resources.displayMetrics.density * 26
                textSize = 12f
            }
            group.addView(chip)
        }

        private fun money(value: Double): String =
            String.format(Locale.getDefault(), "%,.0f", value)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RecurringSeries>() {
            override fun areItemsTheSame(a: RecurringSeries, b: RecurringSeries) =
                a.normalizedMerchant == b.normalizedMerchant
            override fun areContentsTheSame(a: RecurringSeries, b: RecurringSeries) = a == b
        }
    }
}
